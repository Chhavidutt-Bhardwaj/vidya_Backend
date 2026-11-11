package com.vidya.tenantdata.student.enrollment.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.security.entity.PlatformRole;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentGuardian;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.Family;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.GuardianWithParent;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LinkParentRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.MergeParentsRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.MergeParentsResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.ParentSummary;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.SiblingRef;
import com.vidya.tenantdata.student.mapper.StudentGuardianMapper;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentGuardianRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.user.account.SchoolContext;
import com.vidya.tenantdata.user.account.TenantUserAccountService;
import com.vidya.tenantdata.user.account.dto.AccountDtos.LoginInfo;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Parent mapping. A guardian row belongs to one student; a <em>parent record</em> ({@code tenant_user} of type
 * PARENT) is one person and is shared by all their children. The parent's login hangs off that record, so a
 * mother with three children in the school has one login that sees all three.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentLinkService {

    private final StudentGuardianRepository guardianRepository;
    private final StudentRepository studentRepository;
    private final StudentAcademicEnrollmentRepository enrollmentRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantUserAccountService accounts;
    private final SchoolContext context;

    /** What to do with a guardian's parent record while admitting. */
    public record ParentPlan(TenantUser existing, boolean create, boolean login, String email, String phone) {
    }

    // ── Resolution ───────────────────────────────────────────────────────────

    /**
     * Finds the parent record a guardian should link to: an explicit id, else an existing parent with the same
     * email or phone (when {@code linkExisting}), else a new one (when {@code createParent} and there is an email
     * or phone to identify the person by).
     */
    public ParentPlan plan(UUID parentUserId, String email, String phone, Boolean linkExisting, Boolean createParent, Boolean login,
                           List<String> errors, List<String> warnings, String who) {
        String mail = email != null && !email.isBlank() ? email.trim().toLowerCase() : null;
        String tel = normalizePhone(phone);
        boolean wantLogin = Boolean.TRUE.equals(login);
        TenantUser existing = null;
        if (parentUserId != null) {
            existing = tenantUserRepository.findByIdAndNotDeleted(parentUserId).orElse(null);
            if (existing == null || existing.getUserType() != TenantUserType.PARENT) {
                errors.add(who + ": parent " + parentUserId + " not found");
                return new ParentPlan(null, false, false, mail, tel);
            }
        } else if (linkExisting == null || linkExisting) {
            List<TenantUser> matches = findParents(mail, tel);
            if (matches.size() == 1) {
                existing = matches.get(0);
                warnings.add(who + ": linked to existing parent " + TenantUserAccountService.name(existing) + " (" + existing.getUserCode() + ")");
            } else if (matches.size() > 1) {
                errors.add(who + ": " + matches.size() + " parents share this email/phone; send parentUserId to pick one");
                return new ParentPlan(null, false, false, mail, tel);
            }
        }
        boolean create = existing == null && (createParent == null || createParent) && (mail != null || tel != null);
        if (existing == null && !create && wantLogin) {
            errors.add(who + ": a parent login needs an email or phone");
        }
        if (wantLogin) {
            String loginMail = existing != null && existing.getEmail() != null ? existing.getEmail() : mail;
            if (loginMail == null || loginMail.endsWith(".internal")) {
                errors.add(who + ": a parent login needs an email address");
            } else if (existing != null && existing.getMasterUserId() != null) {
                wantLogin = false;
                warnings.add(who + ": parent already has a login");
            } else {
                context.requireAuthority("USER_CREATE", "create parent logins");
            }
        }
        return new ParentPlan(existing, create, wantLogin, mail, tel);
    }

    /** Creates / reuses the parent record for a saved guardian and attaches a login when planned. */
    public GuardianWithParent apply(StudentGuardian guardian, ParentPlan plan, UUID schoolId, Set<UUID> linked, List<String> warnings) {
        TenantUser parent = plan.existing();
        if (parent == null && plan.create()) {
            parent = createParentRecord(guardian.getGuardianName(), plan.email(), plan.phone());
        }
        LoginInfo login = null;
        if (parent != null) {
            guardian.setGuardianUserId(parent.getId());
            guardianRepository.save(guardian);
            if (plan.login()) {
                login = accounts.attach(parent, schoolId, parent.getEmail(), PlatformRole.PARENT, null, linked, warnings);
            }
        }
        return describe(guardian, parent, login);
    }

    /** Link (or create) the parent record behind one existing guardian row. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public GuardianWithParent link(UUID studentId, UUID guardianId, LinkParentRequest req) {
        StudentGuardian g = guardianRepository.findByIdAndNotDeleted(guardianId)
                .filter(x -> x.getStudent() != null && x.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentGuardian", guardianId));
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        UUID want = req != null ? req.parentUserId() : null;
        Boolean login = req != null ? req.login() : null;
        if (want == null && g.getGuardianUserId() != null) {
            want = g.getGuardianUserId();
        }
        ParentPlan plan = plan(want, g.getEmail(), g.getPhone(), true, true, login, errors, warnings, g.getGuardianName());
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("; ", errors));
        }
        if (plan.existing() == null && !plan.create()) {
            throw new BusinessException("Guardian has no email or phone to identify the parent by; add one first");
        }
        boolean wantsLogin = plan.login();
        return apply(g, plan, wantsLogin ? context.schoolId() : null, wantsLogin ? context.linkedMasterUserIds() : Set.of(), warnings);
    }

    /** Unlinks a guardian row from its parent record (the parent record and other children are untouched). */
    @Transactional(transactionManager = "tenantTransactionManager")
    public GuardianWithParent unlink(UUID studentId, UUID guardianId) {
        StudentGuardian g = guardianRepository.findByIdAndNotDeleted(guardianId)
                .filter(x -> x.getStudent() != null && x.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentGuardian", guardianId));
        g.setGuardianUserId(null);
        guardianRepository.save(g);
        return describe(g, null, null);
    }

    // ── Views ────────────────────────────────────────────────────────────────

    /** Guardians of a student with their parent records and the parent's other children. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<GuardianWithParent> guardiansOf(UUID studentId) {
        List<StudentGuardian> gs = guardianRepository.findAllByStudentId(studentId);
        Map<UUID, TenantUser> parents = parentsById(gs.stream().map(StudentGuardian::getGuardianUserId).filter(Objects::nonNull).toList());
        List<GuardianWithParent> out = new ArrayList<>();
        for (StudentGuardian g : gs) {
            out.add(describe(g, g.getGuardianUserId() != null ? parents.get(g.getGuardianUserId()) : null, null));
        }
        return out;
    }

    /** Other students sharing a parent record with this one. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<SiblingRef> siblingsOf(UUID studentId) {
        List<UUID> parentIds = guardianRepository.findAllByStudentId(studentId).stream()
                .map(StudentGuardian::getGuardianUserId).filter(Objects::nonNull).distinct().toList();
        if (parentIds.isEmpty()) {
            return List.of();
        }
        return childrenOf(parentIds, studentId);
    }

    /** Every parent record with its children. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ParentSummary> directory(String search, Boolean withLogin) {
        List<TenantUser> parents = tenantUserRepository.findAllActiveByType(TenantUserType.PARENT);
        Map<UUID, List<StudentGuardian>> byParent = guardianRepository.findAllActiveWithStudent().stream()
                .filter(g -> g.getGuardianUserId() != null)
                .collect(Collectors.groupingBy(StudentGuardian::getGuardianUserId));
        Map<UUID, SiblingRef> refs = refs(byParent.values().stream().flatMap(List::stream).map(StudentGuardian::getStudent).toList());
        String q = search != null ? search.trim().toLowerCase() : null;
        List<ParentSummary> out = new ArrayList<>();
        for (TenantUser p : parents) {
            boolean hasLogin = p.getMasterUserId() != null;
            if (withLogin != null && withLogin != hasLogin) {
                continue;
            }
            String name = TenantUserAccountService.name(p);
            if (q != null && !(contains(name, q) || contains(p.getEmail(), q) || contains(p.getPhone(), q) || contains(p.getUserCode(), q))) {
                continue;
            }
            List<SiblingRef> children = byParent.getOrDefault(p.getId(), List.of()).stream()
                    .map(g -> refs.get(g.getStudent().getId())).filter(Objects::nonNull).distinct()
                    .sorted(Comparator.comparing(SiblingRef::name, Comparator.nullsLast(String::compareTo))).toList();
            out.add(new ParentSummary(p.getId(), p.getUserCode(), name, p.getEmail(), p.getPhone(), hasLogin, children));
        }
        out.sort(Comparator.comparing(ParentSummary::name, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    /**
     * Households: every parent record and student connected through guardian links form one family, so two
     * parents sharing three children appear once, with both parents and all three children.
     */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<Family> families(String search) {
        List<StudentGuardian> links = guardianRepository.findAllActiveWithStudent().stream()
                .filter(g -> g.getGuardianUserId() != null && !g.getStudent().isDeleted()).toList();
        Map<UUID, UUID> root = new HashMap<>();
        for (StudentGuardian g : links) {
            union(root, g.getGuardianUserId(), g.getStudent().getId());
        }
        Map<UUID, ParentSummary> parents = directory(null, null).stream().collect(Collectors.toMap(ParentSummary::parentUserId, x -> x));
        Map<UUID, SiblingRef> students = refs(links.stream().map(StudentGuardian::getStudent).distinct().toList());
        Map<UUID, Set<UUID>> parentsByRoot = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> childrenByRoot = new LinkedHashMap<>();
        for (StudentGuardian g : links) {
            UUID r = find(root, g.getGuardianUserId());
            parentsByRoot.computeIfAbsent(r, k -> new java.util.LinkedHashSet<>()).add(g.getGuardianUserId());
            childrenByRoot.computeIfAbsent(r, k -> new java.util.LinkedHashSet<>()).add(g.getStudent().getId());
        }
        String q = search != null && !search.isBlank() ? search.trim().toLowerCase() : null;
        List<Family> out = new ArrayList<>();
        for (UUID r : parentsByRoot.keySet()) {
            List<ParentSummary> ps = parentsByRoot.get(r).stream().map(parents::get).filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ParentSummary::name, Comparator.nullsLast(String::compareTo))).toList();
            List<SiblingRef> cs = childrenByRoot.get(r).stream().map(students::get).filter(Objects::nonNull)
                    .sorted(Comparator.comparing(SiblingRef::name, Comparator.nullsLast(String::compareTo))).toList();
            if (ps.isEmpty() || cs.isEmpty()) {
                continue;
            }
            if (q != null && ps.stream().noneMatch(p -> contains(p.name(), q) || contains(p.email(), q) || contains(p.phone(), q))
                    && cs.stream().noneMatch(c -> contains(c.name(), q) || contains(c.admissionNumber(), q))) {
                continue;
            }
            int logins = (int) ps.stream().filter(ParentSummary::hasLogin).count();
            out.add(new Family(ps.get(0).userCode(), ps, cs, logins));
        }
        out.sort(Comparator.comparing((Family f) -> -f.children().size()).thenComparing(f -> f.parents().get(0).name(), Comparator.nullsLast(String::compareTo)));
        return out;
    }

    /**
     * Folds a duplicate parent record into {@code keepId}: all guardian rows move over, blank contact details are
     * filled from the duplicate, its login is moved (or disabled when both have one), and the duplicate is deleted.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public MergeParentsResult merge(UUID keepId, MergeParentsRequest req) {
        if (keepId.equals(req.mergeParentUserId())) {
            throw new BusinessException("Pick two different parent records");
        }
        TenantUser keep = parentUser(keepId);
        TenantUser dup = parentUser(req.mergeParentUserId());
        List<String> warnings = new ArrayList<>();

        int moved = 0;
        Set<UUID> keepStudents = guardianRepository.findByGuardianUserId(keep.getId()).stream().map(g -> g.getStudent().getId()).collect(Collectors.toSet());
        for (StudentGuardian g : guardianRepository.findByGuardianUserId(dup.getId())) {
            if (keepStudents.contains(g.getStudent().getId())) {
                warnings.add(studentName(g.getStudent()) + " already had a guardian row for the kept parent; the duplicate row "
                        + g.getGuardianCode() + " was unlinked, review it under the student's guardians");
                g.setGuardianUserId(null);
            } else {
                g.setGuardianUserId(keep.getId());
                moved++;
            }
            guardianRepository.save(g);
        }

        boolean loginMoved = false;
        boolean loginDisabled = false;
        if (dup.getMasterUserId() != null) {
            context.requireAuthority("USER_UPDATE", "move or disable the duplicate's login");
            if (keep.getMasterUserId() == null) {
                keep.setMasterUserId(dup.getMasterUserId());
                dup.setMasterUserId(null);
                loginMoved = true;
            } else {
                accounts.disable(dup);
                dup.setMasterUserId(null);
                loginDisabled = true;
                warnings.add("Both records had a login; the duplicate's login " + dup.getEmail() + " was disabled, the kept one stays");
            }
        }

        // Contact details: the unique email must leave the duplicate before it can land on the kept record.
        String dupEmail = dup.getEmail();
        String dupPhone = dup.getPhone();
        dup.setEmail(null);
        dup.setPhone(dupPhone != null ? dupPhone : keep.getPhone() != null ? keep.getPhone() : "0");
        dup.softDelete();
        tenantUserRepository.saveAndFlush(dup);
        if (keep.getEmail() == null && dupEmail != null) {
            keep.setEmail(dupEmail);
        } else if (dupEmail != null && !dupEmail.equalsIgnoreCase(keep.getEmail())) {
            warnings.add("Email " + dupEmail + " of the duplicate was dropped; the kept record uses " + keep.getEmail());
        }
        if (keep.getPhone() == null && dupPhone != null) {
            keep.setPhone(dupPhone);
        }
        TenantUserProfile kp = keep.getProfile();
        TenantUserProfile dp = dup.getProfile();
        if (kp != null && dp != null) {
            if (kp.getAddressLine1() == null) {
                kp.setAddressLine1(dp.getAddressLine1());
                kp.setAddressLine2(dp.getAddressLine2());
                kp.setCity(dp.getCity());
                kp.setState(dp.getState());
                kp.setPostalCode(dp.getPostalCode());
            }
        }
        tenantUserRepository.save(keep);
        log.info("Merged parent [{}] into [{}]: {} guardian rows moved", dup.getUserCode(), keep.getUserCode(), moved);

        ParentSummary summary = directory(null, null).stream().filter(p -> p.parentUserId().equals(keep.getId())).findFirst()
                .orElseGet(() -> new ParentSummary(keep.getId(), keep.getUserCode(), TenantUserAccountService.name(keep), keep.getEmail(),
                        keep.getPhone(), keep.getMasterUserId() != null, List.of()));
        return new MergeParentsResult(summary, dup.getId(), moved, loginMoved, loginDisabled, warnings);
    }

    private TenantUser parentUser(UUID id) {
        return tenantUserRepository.findByIdAndNotDeleted(id).filter(u -> u.getUserType() == TenantUserType.PARENT)
                .orElseThrow(() -> new ResourceNotFoundException("Parent", id));
    }

    private static UUID find(Map<UUID, UUID> root, UUID x) {
        UUID r = root.getOrDefault(x, x);
        while (!r.equals(root.getOrDefault(r, r))) {
            r = root.get(r);
        }
        root.put(x, r);
        return r;
    }

    private static void union(Map<UUID, UUID> root, UUID a, UUID b) {
        UUID ra = find(root, a);
        UUID rb = find(root, b);
        if (!ra.equals(rb)) {
            root.put(rb, ra);
        }
    }

    /** Parents (as tenant users) of a student that have a login; used when the student leaves. */
    public List<TenantUser> linkedParents(UUID studentId) {
        List<UUID> ids = guardianRepository.findAllByStudentId(studentId).stream()
                .map(StudentGuardian::getGuardianUserId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? List.of() : tenantUserRepository.findAllActiveByIdIn(ids);
    }

    /** True when the parent has an active child other than {@code exceptStudentId}. */
    public boolean hasOtherActiveChild(UUID parentUserId, UUID exceptStudentId) {
        return guardianRepository.findActiveByGuardianUserIdIn(List.of(parentUserId)).stream()
                .map(StudentGuardian::getStudent)
                .anyMatch(s -> s != null && !s.getId().equals(exceptStudentId) && !s.isDeleted()
                        && s.getStatus() == com.vidya.tenantdata.student.entity.StudentStatus.ACTIVE);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<TenantUser> findParents(String email, String phone) {
        Map<UUID, TenantUser> found = new LinkedHashMap<>();
        if (email != null) {
            tenantUserRepository.findActiveByTypeAndEmail(TenantUserType.PARENT, email).forEach(u -> found.put(u.getId(), u));
        }
        if (found.isEmpty() && phone != null && phone.length() >= 10) {
            tenantUserRepository.findActiveByTypeAndPhoneLike(TenantUserType.PARENT, "%" + phone.substring(phone.length() - 10))
                    .forEach(u -> found.put(u.getId(), u));
        }
        return new ArrayList<>(found.values());
    }

    private TenantUser createParentRecord(String fullName, String email, String phone) {
        String userCode = CodeGenerator.userCode();
        while (tenantUserRepository.existsByUserCode(userCode)) {
            userCode = CodeGenerator.userCode();
        }
        String mail = email;
        if (mail != null && tenantUserRepository.existsByEmailIgnoreCase(mail)) {
            // A non-parent user (e.g. a teacher) already uses this email: keep the parent record reachable by phone.
            if (phone == null) {
                throw new BusinessException("Email " + mail + " already belongs to another user; send parentUserId or a phone number");
            }
            mail = null;
        }
        if (mail == null && phone == null) {
            throw new BusinessException("A parent record needs an email or phone");
        }
        TenantUser u = new TenantUser(userCode, mail, phone, TenantUserType.PARENT);
        String name = fullName != null ? fullName.trim() : "Parent";
        int sp = name.lastIndexOf(' ');
        TenantUserProfile p = new TenantUserProfile(u, sp > 0 ? name.substring(0, sp) : name, sp > 0 ? name.substring(sp + 1) : null);
        u.setProfile(p);
        TenantUser saved = tenantUserRepository.save(u);
        log.info("Created parent record [{}] for {}", saved.getUserCode(), name);
        return saved;
    }

    private GuardianWithParent describe(StudentGuardian g, TenantUser parent, LoginInfo login) {
        List<SiblingRef> others = parent != null && g.getStudent() != null ? childrenOf(List.of(parent.getId()), g.getStudent().getId()) : List.of();
        return new GuardianWithParent(StudentGuardianMapper.toResponse(g), parent != null ? parent.getId() : null,
                parent != null ? parent.getUserCode() : null, parent != null && parent.getMasterUserId() != null, login, others);
    }

    private Map<UUID, TenantUser> parentsById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tenantUserRepository.findAllActiveByIdIn(new HashSet<>(ids)).stream().collect(Collectors.toMap(TenantUser::getId, u -> u));
    }

    private List<SiblingRef> childrenOf(Collection<UUID> parentIds, UUID exceptStudentId) {
        List<Student> kids = guardianRepository.findActiveByGuardianUserIdIn(parentIds).stream()
                .map(StudentGuardian::getStudent)
                .filter(s -> s != null && !s.isDeleted() && (exceptStudentId == null || !s.getId().equals(exceptStudentId)))
                .collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a, LinkedHashMap::new)).values().stream().toList();
        return new ArrayList<>(refs(kids).values());
    }

    /** Sibling references with the current class / section, in one query for all students. */
    Map<UUID, SiblingRef> refs(List<Student> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = students.stream().map(Student::getId).collect(Collectors.toSet());
        Map<UUID, StudentAcademicEnrollment> current = new LinkedHashMap<>();
        for (StudentAcademicEnrollment e : enrollmentRepository.findActiveByStudentIdIn(ids)) {
            current.putIfAbsent(e.getStudent().getId(), e); // ordered by enrollmentDate desc → first wins
        }
        Map<UUID, SiblingRef> out = new LinkedHashMap<>();
        for (Student s : students) {
            if (out.containsKey(s.getId())) {
                continue;
            }
            StudentAcademicEnrollment e = current.get(s.getId());
            out.put(s.getId(), new SiblingRef(s.getId(), s.getStudentCode(), studentName(s), s.getAdmissionNumber(),
                    e != null && e.getAcademicClass() != null ? e.getAcademicClass().getName() : null,
                    e != null && e.getSection() != null ? e.getSection().getName() : null, s.getStatus()));
        }
        return out;
    }

    static String studentName(Student s) {
        return s.getTenantUser() != null ? TenantUserAccountService.name(s.getTenantUser()) : s.getStudentCode();
    }

    static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    Optional<Student> student(UUID id) {
        return studentRepository.findByIdAndNotDeleted(id);
    }
}
