package com.vidya.tenantdata.staff.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.security.access.account.SchoolAccountService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.staff.entity.Department;
import com.vidya.tenantdata.staff.entity.Designation;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.entity.StaffSubject;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.mapper.StaffMapper;
import com.vidya.tenantdata.staff.repository.DepartmentRepository;
import com.vidya.tenantdata.staff.repository.DesignationRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.repository.StaffSubjectRepository;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.BulkOnboardRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.BulkOnboardResult;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.BulkRow;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OffboardRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OffboardResult;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OnboardResult;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OnboardStaffRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.SubjectSkill;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.SubjectSkillResponse;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.TeacherSuggestion;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.account.SchoolContext;
import com.vidya.tenantdata.user.account.TenantUserAccountService;
import com.vidya.tenantdata.user.account.dto.AccountDtos.LoginInfo;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff lifecycle: onboarding (person + staff record + subjects + login + roles in one step),
 * logins and roles for existing staff, offboarding, teaching subjects and subject-teacher suggestions.
 *
 * <p>Logins live in the master database and are created in their own transaction. When the tenant
 * transaction then rolls back, every login created during it is deleted again (compensation), so a
 * failed onboarding never leaves a stray account behind.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffOnboardingService {

    static final Set<EmploymentStatus> EXIT_STATUSES = EnumSet.of(EmploymentStatus.RESIGNED, EmploymentStatus.TERMINATED,
            EmploymentStatus.RETIRED, EmploymentStatus.SUSPENDED, EmploymentStatus.INACTIVE);
    private static final Set<EmploymentStatus> PERMANENT_EXIT = EnumSet.of(EmploymentStatus.RESIGNED, EmploymentStatus.TERMINATED,
            EmploymentStatus.RETIRED);

    private final StaffRepository staffRepository;
    private final StaffSubjectRepository staffSubjectRepository;
    private final TenantUserRepository tenantUserRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicClassRepository classRepository;
    private final TenantUserAccountService accounts;
    private final StaffDutyService duties;
    private final SchoolContext context;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    /** A validated onboarding row. */
    record Plan(OnboardStaffRequest req, String email, StaffType type, Designation designation, Department department, Staff reportingTo,
                Map<UUID, Subject> subjects, List<StaffDutyService.Prepared> duties, boolean login, PlatformRole role, List<String> errors,
                List<String> warnings) {
    }

    // ── Onboarding ───────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public OnboardResult onboard(OnboardStaffRequest req) {
        Plan p = plan(req, new HashSet<>(), new HashSet<>());
        if (!p.errors().isEmpty()) {
            throw new BusinessException("Staff not created: " + String.join("; ", p.errors()));
        }
        requirePermissions(List.of(p));
        UUID schoolId = p.login() ? context.schoolId() : null;
        return execute(p, schoolId, p.login() ? context.linkedMasterUserIds() : Set.of());
    }

    /** Validates every row first; saves only when all rows are valid (and not a dry run). */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BulkOnboardResult bulkOnboard(BulkOnboardRequest r) {
        boolean dry = Boolean.TRUE.equals(r.dryRun());
        Set<String> emails = new HashSet<>();
        Set<String> codes = new HashSet<>();
        List<Plan> plans = r.staff().stream().map(req -> plan(req, emails, codes)).toList();
        boolean valid = plans.stream().allMatch(p -> p.errors().isEmpty());
        List<BulkRow> rows = new ArrayList<>();
        if (dry || !valid) {
            for (int i = 0; i < plans.size(); i++) {
                Plan p = plans.get(i);
                rows.add(new BulkRow(i + 1, fullName(p.req()), p.email(), p.errors().isEmpty() ? null : String.join("; ", p.errors()), null,
                        p.login() ? new LoginInfo(null, null, p.email(), p.role(), null, false, null, List.of()) : null));
            }
            return new BulkOnboardResult(dry, valid, 0, 0, rows);
        }
        requirePermissions(plans);
        boolean anyLogin = plans.stream().anyMatch(Plan::login);
        UUID schoolId = anyLogin ? context.schoolId() : null;
        Set<UUID> linked = anyLogin ? context.linkedMasterUserIds() : new HashSet<>();
        int logins = 0;
        for (int i = 0; i < plans.size(); i++) {
            OnboardResult res = execute(plans.get(i), schoolId, linked);
            if (res.login() != null) {
                linked.add(res.login().userId());
                logins += res.login().created() ? 1 : 0;
            }
            rows.add(new BulkRow(i + 1, fullName(plans.get(i).req()), plans.get(i).email(), null, res.staff().getStaffCode(), res.login()));
        }
        log.info("Bulk onboarding: {} staff, {} new logins", rows.size(), logins);
        return new BulkOnboardResult(false, true, rows.size(), logins, rows);
    }

    private void requirePermissions(List<Plan> plans) {
        if (plans.stream().anyMatch(Plan::login)) {
            context.requireAuthority("USER_CREATE", "create logins");
        }
        if (plans.stream().anyMatch(p -> p.req().login() != null && p.req().login().extraRoleIds() != null && !p.req().login().extraRoleIds().isEmpty())
                || plans.stream().anyMatch(p -> p.login() && p.duties().stream().anyMatch(d -> d.type().getAccessRoleCode() != null))) {
            context.requireAuthority("SCHOOL_ROLE_MANAGE", "assign roles");
        }
    }

    Plan plan(OnboardStaffRequest req, Set<String> batchEmails, Set<String> batchCodes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String email = req.email() != null && !req.email().isBlank() ? req.email().trim().toLowerCase(Locale.ROOT) : null;
        String phone = req.phone() != null && !req.phone().isBlank() ? req.phone().trim() : null;
        if (email == null && phone == null) {
            errors.add("email or phone is required");
        }
        if (email != null && (!batchEmails.add(email) || tenantUserRepository.existsByEmailIgnoreCase(email))) {
            errors.add("email " + email + " is already used");
        }
        if (req.employeeCode() != null && !req.employeeCode().isBlank()) {
            String c = req.employeeCode().trim();
            if (!batchCodes.add(c.toUpperCase(Locale.ROOT)) || staffRepository.existsActiveByEmployeeCode(c, null)) {
                errors.add("employee code " + c + " is already used");
            }
        }
        Designation designation = null;
        if (req.designationId() != null) {
            designation = designationRepository.findByIdAndNotDeleted(req.designationId()).orElse(null);
            if (designation == null) {
                errors.add("designation " + req.designationId() + " not found");
            } else if (!designation.isActive()) {
                errors.add("designation " + designation.getName() + " is inactive");
            }
        }
        StaffType type = req.staffType() != null ? req.staffType() : designation != null ? designation.getStaffType() : null;
        if (type == null) {
            errors.add("staffType or designationId is required");
        }
        UUID depId = req.departmentId() != null ? req.departmentId() : designation != null ? designation.getDefaultDepartmentId() : null;
        Department department = null;
        if (depId != null) {
            department = departmentRepository.findByIdAndNotDeleted(depId).orElse(null);
            if (department == null || !department.isActive()) {
                errors.add("department " + depId + " not found or inactive");
            }
        }
        Staff reportingTo = null;
        if (req.reportingToStaffId() != null) {
            reportingTo = staffRepository.findByIdAndNotDeleted(req.reportingToStaffId()).orElse(null);
            if (reportingTo == null || reportingTo.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
                errors.add("reportingTo staff " + req.reportingToStaffId() + " not found or not active");
            }
        }
        Map<UUID, Subject> subjects = subjects(req.subjects(), errors);
        if (!subjects.isEmpty() && type != null && type != StaffType.TEACHER) {
            warnings.add("Subjects were recorded for a " + type + " staff member");
        }
        List<StaffDutyService.Prepared> dutyPlans = new ArrayList<>();
        if (req.duties() != null) {
            for (DutyRequest d : req.duties()) {
                StaffDutyService.Prepared dp = duties.prepare(d, null, errors);
                if (dp != null) {
                    dutyPlans.add(dp);
                }
            }
        }
        if (req.joiningDate() != null && req.joiningDate().isAfter(LocalDate.now().plusYears(1))) {
            errors.add("joining date " + req.joiningDate() + " is more than a year away");
        }

        var login = req.login();
        String defaultRole = designation != null ? designation.getDefaultRole() : null;
        boolean wantLogin = login != null && login.create() != null ? login.create()
                : email != null && (designation != null ? defaultRole != null : type == StaffType.TEACHER);
        PlatformRole role = login != null && login.role() != null ? login.role()
                : defaultRole != null ? PlatformRole.valueOf(defaultRole) : type == StaffType.TEACHER ? PlatformRole.TEACHER : PlatformRole.SCHOOL_STAFF;
        if (wantLogin) {
            if (email == null) {
                errors.add("a login needs an email address");
            }
            if (!SchoolAccountService.STAFF_ROLES.contains(role)) {
                errors.add("role " + role + " cannot be given to staff");
            }
        } else if (login != null && login.extraRoleIds() != null && !login.extraRoleIds().isEmpty()) {
            errors.add("extra roles need a login (login.create = true)");
        }
        return new Plan(req, email, type, designation, department, reportingTo, subjects, dutyPlans, wantLogin, role, errors, warnings);
    }

    private OnboardResult execute(Plan p, UUID schoolId, Set<UUID> linked) {
        OnboardStaffRequest req = p.req();
        String userCode = CodeGenerator.userCode();
        while (tenantUserRepository.existsByUserCode(userCode)) {
            userCode = CodeGenerator.userCode();
        }
        TenantUser user = new TenantUser(userCode, p.email(), blankToNull(req.phone()),
                p.type() == StaffType.TEACHER ? TenantUserType.TEACHER : TenantUserType.STAFF);
        TenantUserProfile profile = new TenantUserProfile(user, req.firstName().trim(), blankToNull(req.lastName()));
        profile.setGender(req.gender());
        profile.setDateOfBirth(req.dateOfBirth());
        user.setProfile(profile);
        user = tenantUserRepository.save(user);

        String staffCode = CodeGenerator.staffCode();
        while (staffRepository.existsByStaffCode(staffCode)) {
            staffCode = CodeGenerator.staffCode();
        }
        Staff staff = new Staff(staffCode, user, blankToNull(req.employeeCode()), req.joiningDate(), p.type(),
                p.designation() != null ? p.designation().getName() : null, p.department() != null ? p.department().getName() : null);
        staff.setDesignationId(p.designation() != null ? p.designation().getId() : null);
        staff.setDepartmentId(p.department() != null ? p.department().getId() : null);
        staff.setReportingToStaffId(p.reportingTo() != null ? p.reportingTo().getId() : null);
        staff.setEmploymentType(req.employmentType());
        staff.setQualification(blankToNull(req.qualification()));
        staff.setExperienceYears(req.experienceYears());
        staff.setMaxPeriodsPerWeek(req.maxPeriodsPerWeek());
        staff = staffRepository.save(staff);
        saveSubjects(staff.getId(), req.subjects());
        em.flush();

        LoginInfo login = null;
        List<String> warnings = new ArrayList<>(p.warnings());
        if (p.login()) {
            login = accounts.attach(user, schoolId, p.email(), p.role(), req.login() != null ? req.login().extraRoleIds() : null, linked, warnings);
        }
        for (StaffDutyService.Prepared d : p.duties()) {
            duties.apply(staff, d);
        }
        log.info("Onboarded staff {} ({}, {}){}", staffCode, p.type(), p.designation() != null ? p.designation().getCode() : "-",
                login != null ? " with login " + login.userCode() : "");
        return new OnboardResult(StaffMapper.toDetailResponse(staff), login, warnings);
    }

    // ── Offboarding ──────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public OffboardResult offboard(UUID staffId, OffboardRequest r) {
        if (!EXIT_STATUSES.contains(r.status())) {
            throw new BusinessException("Offboarding status must be one of " + EXIT_STATUSES);
        }
        Staff staff = staff(staffId);
        LocalDate last = r.lastWorkingDate() != null ? r.lastWorkingDate() : LocalDate.now();
        if (staff.getJoiningDate() != null && last.isBefore(staff.getJoiningDate())) {
            throw new BusinessException("Last working date " + last + " is before the joining date " + staff.getJoiningDate());
        }
        staff.updateStatus(r.status());
        staff.setLastWorkingDate(last);
        boolean permanent = PERMANENT_EXIT.contains(r.status());
        TenantUser user = staff.getTenantUser();
        if (permanent && user.getStatus() == TenantUserStatus.ACTIVE) {
            user.setStatus(TenantUserStatus.INACTIVE);
        }

        long classTeacher = count("SELECT count(*) FROM class_teacher_assignment WHERE staff_id = :s AND status = 'ACTIVE' AND deleted_at IS NULL", staffId);
        long subjects = count("SELECT count(*) FROM teacher_subject_assignment WHERE teacher_id = :s AND status IN ('ACTIVE', 'DRAFT') AND deleted_at IS NULL", staffId);
        long slots = count("""
                SELECT count(*) FROM timetable_entry e JOIN timetable t ON t.id = e.timetable_id
                WHERE e.teacher_id = :s AND e.deleted_at IS NULL AND t.deleted_at IS NULL AND t.status IN ('PUBLISHED', 'ACTIVE')
                """, staffId);
        boolean ended = false;
        if (Boolean.TRUE.equals(r.endAssignments()) && (classTeacher + subjects) > 0) {
            em.createNativeQuery("UPDATE class_teacher_assignment SET status = 'COMPLETED', end_date = :d, updated_at = NOW() "
                    + "WHERE staff_id = :s AND status = 'ACTIVE' AND deleted_at IS NULL").setParameter("d", last).setParameter("s", staffId).executeUpdate();
            em.createNativeQuery("UPDATE teacher_subject_assignment SET status = CASE status WHEN 'DRAFT' THEN 'CANCELLED' ELSE 'COMPLETED' END, "
                    + "end_date = CASE status WHEN 'DRAFT' THEN end_date ELSE :d END, updated_at = NOW() "
                    + "WHERE teacher_id = :s AND status IN ('ACTIVE', 'DRAFT') AND deleted_at IS NULL")
                    .setParameter("d", last).setParameter("s", staffId).executeUpdate();
            ended = true;
        }
        List<String> headed = departmentRepository.findAllNotDeleted().stream().filter(d -> staffId.equals(d.getHeadStaffId())).map(Department::getName).toList();
        // Duties (HOD, coordinator, in-charge …) end with the person; their access roles come off the login.
        List<String> dutiesEnded = duties.endAllFor(staffId, last);
        long reportees = count("SELECT count(*) FROM staff WHERE reporting_to_staff_id = :s AND deleted_at IS NULL AND employment_status = 'ACTIVE'", staffId);
        em.flush();

        boolean disable = r.revokeLogin() == null || r.revokeLogin();
        if (disable && user.getMasterUserId() != null) {
            context.requireAuthority("USER_UPDATE", "disable the login (or send revokeLogin=false)");
        }
        int sessions = 0;
        boolean loginDisabled = false;
        if (disable && user.getMasterUserId() != null) {
            sessions = accounts.disable(user);
            loginDisabled = true;
        }

        List<String> next = new ArrayList<>();
        if (!ended && classTeacher > 0) {
            next.add(classTeacher + " section(s) need a new class teacher: POST /api/v1/class-teacher-assignments/{id}/replace");
        }
        if (!ended && subjects > 0) {
            next.add(subjects + " subject assignment(s) need another teacher: GET /api/v1/staff-setup/teachers-for-subject");
        }
        if (slots > 0) {
            next.add(slots + " timetable slot(s) still name this teacher; arrange substitutions or edit the timetable");
        }
        if (!headed.isEmpty()) {
            next.add("Choose a new head for: " + String.join(", ", headed));
        }
        if (reportees > 0) {
            next.add(reportees + " staff report to this person; update their reporting line");
        }
        log.info("Offboarded staff {} as {} on {} (login disabled: {}, assignments ended: {})", staff.getStaffCode(), r.status(), last,
                loginDisabled, ended);
        return new OffboardResult(r.status(), last, loginDisabled, sessions, classTeacher, subjects, slots, ended, headed, dutiesEnded, reportees, next);
    }

    // ── Subjects and suggestions ─────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public List<SubjectSkillResponse> setSubjects(UUID staffId, List<SubjectSkill> skills) {
        staff(staffId);
        List<String> errors = new ArrayList<>();
        subjects(skills, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException("Subjects not saved: " + String.join("; ", errors));
        }
        staffSubjectRepository.deleteByStaffId(staffId);
        em.flush();
        saveSubjects(staffId, skills);
        return subjectsOf(staffId);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<SubjectSkillResponse> subjectsOf(UUID staffId) {
        return staffSubjectRepository.findByStaffId(staffId).stream()
                .map(s -> {
                    Subject sub = subjectRepository.findByIdAndNotDeleted(s.getSubjectId()).orElse(null);
                    return new SubjectSkillResponse(s.getSubjectId(), sub != null ? sub.getSubjectCode() : null, sub != null ? sub.getName() : null,
                            s.getFromGradeLevel(), s.getToGradeLevel(), s.isPrimary());
                })
                .sorted(Comparator.comparing(SubjectSkillResponse::primary).reversed().thenComparing(x -> x.subjectName() == null ? "" : x.subjectName()))
                .toList();
    }

    /**
     * Active teachers who can teach a subject (for a class, when given), least loaded first; the
     * primary-subject teachers come first.
     */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<TeacherSuggestion> teachersFor(UUID subjectId, UUID academicClassId) {
        subjectRepository.findByIdAndNotDeleted(subjectId).orElseThrow(() -> new ResourceNotFoundException("Subject", subjectId));
        Integer level = null;
        if (academicClassId != null) {
            AcademicClass c = classRepository.findByIdAndNotDeleted(academicClassId)
                    .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", academicClassId));
            level = c.getGradeLevel();
        }
        final Integer lv = level;
        Map<UUID, StaffSubject> skills = staffSubjectRepository.findBySubjectId(subjectId).stream().filter(s -> s.covers(lv))
                .collect(Collectors.toMap(StaffSubject::getStaffId, s -> s, (a, b) -> a));
        if (skills.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> periods = StaffOrgService.periodsPerWeek(em);
        Map<UUID, Long> assignments = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT teacher_id, count(*) FROM teacher_subject_assignment WHERE status = 'ACTIVE' "
                + "AND deleted_at IS NULL GROUP BY teacher_id").getResultList();
        rows.forEach(x -> assignments.put((UUID) x[0], ((Number) x[1]).longValue()));
        List<TeacherSuggestion> out = new ArrayList<>();
        for (Staff s : staffRepository.findAllById(skills.keySet())) {
            if (s.getDeletedAt() != null || s.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
                continue;
            }
            long p = periods.getOrDefault(s.getId(), 0L);
            out.add(new TeacherSuggestion(s.getId(), s.getStaffCode(), StaffNames.name(s), s.getDesignation(), skills.get(s.getId()).isPrimary(),
                    assignments.getOrDefault(s.getId(), 0L), p, s.getMaxPeriodsPerWeek(),
                    s.getMaxPeriodsPerWeek() != null && p >= s.getMaxPeriodsPerWeek()));
        }
        out.sort(Comparator.comparing(TeacherSuggestion::overloaded)
                .thenComparing(TeacherSuggestion::primarySubject, Comparator.reverseOrder())
                .thenComparingLong(TeacherSuggestion::periodsPerWeek)
                .thenComparingLong(TeacherSuggestion::subjectAssignments));
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<UUID, Subject> subjects(List<SubjectSkill> skills, List<String> errors) {
        Map<UUID, Subject> out = new HashMap<>();
        if (skills == null) {
            return out;
        }
        for (SubjectSkill s : skills) {
            if (out.containsKey(s.subjectId())) {
                errors.add("subject " + s.subjectId() + " listed twice");
                continue;
            }
            Optional<Subject> sub = subjectRepository.findByIdAndNotDeleted(s.subjectId());
            if (sub.isEmpty() || sub.get().getStatus() != SubjectStatus.ACTIVE) {
                errors.add("subject " + s.subjectId() + " not found or inactive");
                continue;
            }
            if (s.fromGradeLevel() != null && s.toGradeLevel() != null && s.fromGradeLevel() > s.toGradeLevel()) {
                errors.add(sub.get().getName() + ": class range " + s.fromGradeLevel() + "–" + s.toGradeLevel() + " is reversed");
            }
            out.put(s.subjectId(), sub.get());
        }
        return out;
    }

    private void saveSubjects(UUID staffId, List<SubjectSkill> skills) {
        if (skills == null) {
            return;
        }
        for (SubjectSkill s : skills) {
            staffSubjectRepository.save(new StaffSubject(staffId, s.subjectId(), s.fromGradeLevel(), s.toGradeLevel(), Boolean.TRUE.equals(s.primary())));
        }
    }

    private Staff staff(UUID id) {
        return staffRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("Staff", id));
    }

    private long count(String sql, UUID staffId) {
        return ((Number) em.createNativeQuery(sql).setParameter("s", staffId).getSingleResult()).longValue();
    }

    private static String fullName(OnboardStaffRequest r) {
        return (r.firstName() + " " + (r.lastName() != null ? r.lastName() : "")).trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
