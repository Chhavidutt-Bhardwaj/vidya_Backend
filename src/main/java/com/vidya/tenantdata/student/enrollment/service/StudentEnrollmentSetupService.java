package com.vidya.tenantdata.student.enrollment.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.security.entity.PlatformRole;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import com.vidya.tenantdata.attendance.repository.StudentAttendanceRepository;
import com.vidya.tenantdata.student.dto.BatchAssignSubjectsRequest;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.EnrollmentResponse;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentGuardian;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmitResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmitStudentRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AttendanceSummary;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.BulkAdmitRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.BulkAdmitResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.BulkAdmitRow;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.ClassCount;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.EnrollmentOverview;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.GuardianRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.GuardianWithParent;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.Issue;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LeaveRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LeaveResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LeavingType;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.ProfileDetails;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.ProfileDetailsResponse;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.ReadmitRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.SectionCount;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.SiblingRef;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.StudentFullProfile;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.UpdateProfileRequest;
import com.vidya.tenantdata.student.enrollment.entity.StudentProfileDetail;
import com.vidya.tenantdata.student.enrollment.repository.StudentProfileDetailRepository;
import com.vidya.tenantdata.student.enrollment.service.ParentLinkService.ParentPlan;
import com.vidya.tenantdata.student.mapper.EnrollmentMapper;
import com.vidya.tenantdata.student.mapper.StudentMapper;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentGuardianRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.service.StudentEnrollmentService;
import com.vidya.tenantdata.student.service.StudentSubjectEnrollmentService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Student & parent enrollment for a school: new admissions (single and bulk, with a dry run), the full student
 * profile, parent mapping with shared parent logins, students leaving and coming back, and an overview of where
 * this year's enrollment stands. Promotion of existing students lives in
 * {@code POST /api/v1/academic-years/{id}/promotion}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentEnrollmentSetupService {

    private final StudentRepository studentRepository;
    private final TenantUserRepository tenantUserRepository;
    private final StudentGuardianRepository guardianRepository;
    private final StudentAcademicEnrollmentRepository enrollmentRepository;
    private final StudentProfileDetailRepository detailRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final StudentAttendanceRepository attendanceRepository;
    private final StudentEnrollmentService enrollmentService;
    private final StudentSubjectEnrollmentService subjectEnrollmentService;
    private final AdmissionNumberService admissionNumbers;
    private final ParentLinkService parents;
    private final TenantUserAccountService accounts;
    private final SchoolContext context;

    // ── Admission ────────────────────────────────────────────────────────────

    private record Plan(AdmitStudentRequest req, AcademicYear year, AcademicClass cls, Section section, String admissionNumber,
                        boolean generatedNumber, String email, boolean studentLogin, List<ParentPlan> parentPlans, List<String> errors,
                        List<String> warnings) {
        String name() {
            return (req.firstName() + " " + (req.lastName() != null ? req.lastName() : "")).trim();
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public AdmitResult admit(AdmitStudentRequest req) {
        Plan p = plan(req, new HashSet<>(), new HashSet<>(), new Existing());
        if (!p.errors().isEmpty()) {
            throw new BusinessException(String.join("; ", p.errors()));
        }
        boolean anyLogin = p.studentLogin() || p.parentPlans().stream().anyMatch(ParentPlan::login);
        return execute(p, anyLogin ? context.schoolId() : null, anyLogin ? context.linkedMasterUserIds() : new HashSet<>());
    }

    /** Validates every row first; nothing is saved unless all rows are valid. {@code dryRun} only validates. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BulkAdmitResult bulkAdmit(BulkAdmitRequest req) {
        boolean dryRun = Boolean.TRUE.equals(req.dryRun());
        Set<String> numbersInBatch = new HashSet<>();
        Set<String> emailsInBatch = new HashSet<>();
        Existing existing = new Existing();
        List<Plan> plans = new ArrayList<>();
        List<BulkAdmitRow> rows = new ArrayList<>();
        boolean valid = true;
        boolean anyLogin = false;
        int i = 0;
        for (AdmitStudentRequest r : req.students()) {
            Plan p = plan(r, numbersInBatch, emailsInBatch, existing);
            plans.add(p);
            anyLogin |= p.studentLogin() || p.parentPlans().stream().anyMatch(ParentPlan::login);
            if (!p.errors().isEmpty()) {
                valid = false;
            }
            rows.add(new BulkAdmitRow(i++, p.name(), p.admissionNumber() + (p.generatedNumber() ? " (next)" : ""),
                    p.errors().isEmpty() ? null : String.join("; ", p.errors()), null, null, p.warnings()));
        }
        if (dryRun || !valid) {
            return new BulkAdmitResult(dryRun, valid, 0, 0, rows);
        }
        UUID schoolId = anyLogin ? context.schoolId() : null;
        Set<UUID> linked = anyLogin ? context.linkedMasterUserIds() : new HashSet<>();
        List<BulkAdmitRow> done = new ArrayList<>();
        int parentLogins = 0;
        for (int k = 0; k < plans.size(); k++) {
            AdmitResult r = execute(plans.get(k), schoolId, linked);
            parentLogins += (int) r.guardians().stream().filter(g -> g.parentLogin() != null && g.parentLogin().created()).count();
            done.add(new BulkAdmitRow(k, rows.get(k).name(), r.student().getAdmissionNumber(), null, r.student().getStudentCode(),
                    r.student().getId(), r.warnings()));
        }
        return new BulkAdmitResult(false, true, done.size(), parentLogins, done);
    }

    /** Students already on record, loaded once per admission batch for the duplicate check. */
    private final class Existing {
        private List<Student> students;

        List<Student> get() {
            if (students == null) {
                students = studentRepository.findAllActiveWithUser();
            }
            return students;
        }
    }

    private Plan plan(AdmitStudentRequest req, Set<String> numbersInBatch, Set<String> emailsInBatch, Existing existing) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String who = req.firstName() != null ? req.firstName().trim() : "student";

        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(req.academicYearId()).orElse(null);
        if (year == null) {
            errors.add(who + ": academic year not found");
        }
        AcademicClass cls = classRepository.findByIdAndNotDeleted(req.academicClassId()).orElse(null);
        if (cls == null) {
            errors.add(who + ": class not found");
        }
        Section section = null;
        if (req.sectionId() != null) {
            section = sectionRepository.findByIdAndNotDeleted(req.sectionId()).orElse(null);
            if (section == null) {
                errors.add(who + ": section not found");
            } else if (year != null && cls != null
                    && (!section.getAcademicYear().getId().equals(year.getId()) || !section.getAcademicClass().getId().equals(cls.getId()))) {
                errors.add(who + ": section " + section.getName() + " does not belong to " + cls.getName() + " in " + year.getCode());
            } else if (section.getCapacity() != null
                    && enrollmentRepository.countActiveEnrollmentsBySectionId(section.getId()) >= section.getCapacity()) {
                errors.add(who + ": section " + section.getName() + " is full (" + section.getCapacity() + ")");
            }
        } else {
            warnings.add(who + ": no section yet; place the student later with PUT /api/v1/enrollments/{id}/transfer");
        }
        if (year != null && cls != null && req.rollNumber() != null && !req.rollNumber().isBlank()
                && enrollmentRepository.existsActiveRollNumber(year.getId(), cls.getId(), section != null ? section.getId() : null,
                req.rollNumber().trim(), null)) {
            errors.add(who + ": roll number " + req.rollNumber().trim() + " is already taken");
        }

        String number;
        boolean generated = false;
        if (req.admissionNumber() != null && !req.admissionNumber().isBlank()) {
            number = req.admissionNumber().trim();
            if (studentRepository.existsByAdmissionNumber(number)) {
                errors.add(who + ": admission number " + number + " already exists");
            }
        } else {
            number = year != null ? admissionNumbers.peek(year) : "?";
            generated = true;
        }
        if (!numbersInBatch.add(number.toLowerCase(Locale.ROOT)) && !generated) {
            errors.add(who + ": admission number " + number + " appears twice in this batch");
        }

        String email = req.email() != null && !req.email().isBlank() ? req.email().trim().toLowerCase(Locale.ROOT) : null;
        if (email != null) {
            if (tenantUserRepository.existsByEmailIgnoreCase(email) || !emailsInBatch.add(email)) {
                errors.add(who + ": email " + email + " is already used by another person");
            }
        }
        boolean studentLogin = Boolean.TRUE.equals(req.studentLogin());
        if (studentLogin) {
            if (email == null) {
                errors.add(who + ": a student login needs the student's own email address");
            } else {
                context.requireAuthority("USER_CREATE", "create logins");
            }
        }
        if (req.dateOfBirth() == null) {
            warnings.add(who + ": no date of birth");
        } else if (!req.dateOfBirth().isBefore(LocalDate.now().minusYears(2))) {
            errors.add(who + ": date of birth " + req.dateOfBirth() + " is not plausible");
        }
        if (req.dateOfBirth() != null) {
            duplicateCheck(req, who, warnings, existing.get());
        }

        List<ParentPlan> parentPlans = new ArrayList<>();
        if (req.guardians() != null) {
            long primaries = req.guardians().stream().filter(g -> Boolean.TRUE.equals(g.primary())).count();
            if (primaries > 1) {
                errors.add(who + ": only one guardian can be primary");
            }
            for (GuardianRequest g : req.guardians()) {
                parentPlans.add(parents.plan(g.parentUserId(), g.email(), g.phone(), g.linkExisting(), g.createParent(), g.login(),
                        errors, warnings, who + " / " + g.name()));
            }
        } else {
            warnings.add(who + ": no guardian; add parents so they can be contacted and get a login");
        }
        return new Plan(req, year, cls, section, number, generated, email, studentLogin, parentPlans, errors, warnings);
    }

    /** Warns when a student with the same name and date of birth already exists (likely a re-entry). */
    private static void duplicateCheck(AdmitStudentRequest req, String who, List<String> warnings, List<Student> existing) {
        String first = req.firstName().trim().toLowerCase(Locale.ROOT);
        for (Student s : existing) {
            TenantUserProfile p = s.getTenantUser() != null ? s.getTenantUser().getProfile() : null;
            if (p != null && req.dateOfBirth().equals(p.getDateOfBirth()) && p.getFirstName() != null
                    && p.getFirstName().trim().toLowerCase(Locale.ROOT).equals(first)) {
                warnings.add(who + ": a student with the same name and date of birth exists (" + s.getAdmissionNumber() + ", " + s.getStatus()
                        + "); use re-admission if this is the same child");
                return;
            }
        }
    }

    private AdmitResult execute(Plan p, UUID schoolId, Set<UUID> linked) {
        AdmitStudentRequest req = p.req();
        List<String> warnings = new ArrayList<>(p.warnings());
        String admissionNumber = p.generatedNumber() ? admissionNumbers.next(p.year()) : p.admissionNumber();
        LocalDate admissionDate = req.admissionDate() != null ? req.admissionDate() : LocalDate.now();

        String studentCode = CodeGenerator.studentCode();
        while (studentRepository.existsByStudentCode(studentCode)) {
            studentCode = CodeGenerator.studentCode();
        }
        String userCode = CodeGenerator.userCode();
        while (tenantUserRepository.existsByUserCode(userCode)) {
            userCode = CodeGenerator.userCode();
        }
        String email = p.email() != null ? p.email() : "student." + studentCode.toLowerCase(Locale.ROOT) + "@school.vidya.internal";
        TenantUser user = new TenantUser(userCode, email, req.phone(), TenantUserType.STUDENT);
        TenantUserProfile profile = new TenantUserProfile(user, req.firstName().trim(), req.lastName() != null ? req.lastName().trim() : null);
        profile.setGender(req.gender());
        profile.setDateOfBirth(req.dateOfBirth());
        profile.setAddressLine1(req.addressLine1());
        profile.setAddressLine2(req.addressLine2());
        profile.setCity(req.city());
        profile.setState(req.state());
        profile.setPostalCode(req.postalCode());
        user.setProfile(profile);
        user = tenantUserRepository.save(user);

        Student student = studentRepository.save(new Student(studentCode, user, admissionNumber, admissionDate));
        if (req.details() != null) {
            StudentProfileDetail d = new StudentProfileDetail(student.getId());
            applyDetails(d, req.details());
            detailRepository.save(d);
        }

        List<GuardianWithParent> guardians = new ArrayList<>();
        if (req.guardians() != null) {
            boolean first = true;
            for (int i = 0; i < req.guardians().size(); i++) {
                GuardianRequest g = req.guardians().get(i);
                String code = CodeGenerator.guardianCode();
                while (guardianRepository.existsByGuardianCode(code)) {
                    code = CodeGenerator.guardianCode();
                }
                boolean primary = g.primary() != null ? g.primary() : (first && req.guardians().stream().noneMatch(x -> Boolean.TRUE.equals(x.primary())));
                StudentGuardian sg = new StudentGuardian(code, student, g.name(), g.relationship(), g.phone(), g.email(), g.occupation(),
                        g.annualIncome(), g.addressLine1(), g.addressLine2(), g.city(), g.state(), g.postalCode(), primary,
                        Boolean.TRUE.equals(g.emergencyContact()));
                sg = guardianRepository.save(sg);
                guardians.add(parents.apply(sg, p.parentPlans().get(i), schoolId, linked, warnings));
                first = false;
            }
        }

        LocalDate enrollmentDate = admissionDate;
        StudentAcademicEnrollment enrollment = enrollmentService.createEnrollmentEntity(student, CreateEnrollmentRequest.builder()
                .academicYearId(p.year().getId())
                .academicClassId(p.cls().getId())
                .sectionId(p.section() != null ? p.section().getId() : null)
                .rollNumber(req.rollNumber() != null && !req.rollNumber().isBlank() ? req.rollNumber().trim() : null)
                .enrollmentStatus(StudentEnrollmentStatus.ACTIVE)
                .admissionDate(admissionDate)
                .enrollmentDate(enrollmentDate)
                .remarks(req.remarks())
                .build());
        if (req.autoAssignCompulsorySubjects() == null || req.autoAssignCompulsorySubjects()) {
            subjectEnrollmentService.autoEnrollCompulsorySubjects(enrollment);
        }
        if (req.subjectIds() != null && !req.subjectIds().isEmpty()) {
            subjectEnrollmentService.batchEnrollSubjects(enrollment.getId(), BatchAssignSubjectsRequest.builder()
                    .subjectIds(req.subjectIds()).effectiveFrom(enrollmentDate).remarks("Assigned during admission").build());
        }

        LoginInfo studentLogin = null;
        if (p.studentLogin()) {
            studentLogin = accounts.attach(user, schoolId, email, PlatformRole.STUDENT, null, linked, warnings);
        }
        List<SiblingRef> siblings = guardians.stream().flatMap(g -> g.otherChildren().stream())
                .collect(Collectors.toMap(SiblingRef::studentId, s -> s, (a, b) -> a, LinkedHashMap::new)).values().stream().toList();
        log.info("Admitted student [{}] {} into {} {}", admissionNumber, p.name(), p.cls().getName(),
                p.section() != null ? p.section().getName() : "(no section)");
        return new AdmitResult(StudentMapper.toDetailResponse(student), EnrollmentMapper.toResponse(enrollment), guardians, studentLogin,
                siblings, warnings);
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentFullProfile profile(UUID studentId) {
        Student s = student(studentId);
        CurrentEnrollmentResponse current = null;
        try {
            current = enrollmentService.getCurrentEnrollment(studentId);
        } catch (ResourceNotFoundException ignored) {
            // no active enrollment
        }
        List<EnrollmentResponse> history = enrollmentRepository.findAllByStudentId(studentId).stream().map(EnrollmentMapper::toResponse).toList();
        AttendanceSummary attendance = null;
        if (current != null && current.getAcademicYearId() != null) {
            UUID y = current.getAcademicYearId();
            long total = attendanceRepository.countTotalByStudentAndYear(studentId, y);
            if (total > 0) {
                long present = attendanceRepository.countByStudentAndYearAndStatus(studentId, y, AttendanceStatus.PRESENT);
                long absent = attendanceRepository.countByStudentAndYearAndStatus(studentId, y, AttendanceStatus.ABSENT);
                long late = attendanceRepository.countByStudentAndYearAndStatus(studentId, y, AttendanceStatus.LATE);
                attendance = new AttendanceSummary(y, present, absent, late, total, (int) Math.round((present + late) * 100.0 / total));
            }
        }
        TenantUser u = s.getTenantUser();
        return new StudentFullProfile(StudentMapper.toDetailResponse(s), details(detailRepository.findById(studentId).orElse(null)), current,
                history, parents.guardiansOf(studentId), parents.siblingsOf(studentId), attendance, u != null ? u.getId() : null,
                u != null && u.getMasterUserId() != null);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentFullProfile updateProfile(UUID studentId, UpdateProfileRequest r) {
        Student s = student(studentId);
        TenantUser u = s.getTenantUser();
        TenantUserProfile p = u.getProfile();
        if (p == null) {
            p = new TenantUserProfile(u, r.firstName() != null ? r.firstName() : "Student", r.lastName());
            u.setProfile(p);
        }
        if (r.firstName() != null && !r.firstName().isBlank()) {
            p.setFirstName(r.firstName().trim());
        }
        if (r.lastName() != null) {
            p.setLastName(r.lastName().isBlank() ? null : r.lastName().trim());
        }
        if (r.gender() != null) {
            p.setGender(r.gender());
        }
        if (r.dateOfBirth() != null) {
            p.setDateOfBirth(r.dateOfBirth());
        }
        if (r.addressLine1() != null) {
            p.setAddressLine1(r.addressLine1());
        }
        if (r.addressLine2() != null) {
            p.setAddressLine2(r.addressLine2());
        }
        if (r.city() != null) {
            p.setCity(r.city());
        }
        if (r.state() != null) {
            p.setState(r.state());
        }
        if (r.postalCode() != null) {
            p.setPostalCode(r.postalCode());
        }
        if (r.phone() != null) {
            u.setPhone(r.phone().isBlank() ? null : r.phone().trim());
        }
        if (r.email() != null && !r.email().isBlank()) {
            String mail = r.email().trim().toLowerCase(Locale.ROOT);
            if (!mail.equalsIgnoreCase(u.getEmail())) {
                if (tenantUserRepository.existsByEmailIgnoreCase(mail)) {
                    throw new BusinessException("Email " + mail + " is already used by another person");
                }
                if (u.getMasterUserId() != null) {
                    throw new BusinessException("The student has a login; change the email through the account API");
                }
                u.setEmail(mail);
            }
        }
        tenantUserRepository.save(u);
        if (r.details() != null) {
            StudentProfileDetail d = detailRepository.findById(studentId).orElseGet(() -> new StudentProfileDetail(studentId));
            applyDetails(d, r.details());
            detailRepository.save(d);
        }
        return profile(studentId);
    }

    private static void applyDetails(StudentProfileDetail d, ProfileDetails x) {
        if (x.bloodGroup() != null) {
            d.setBloodGroup(x.bloodGroup());
        }
        if (x.nationality() != null) {
            d.setNationality(x.nationality());
        }
        if (x.motherTongue() != null) {
            d.setMotherTongue(x.motherTongue());
        }
        if (x.house() != null) {
            d.setHouse(x.house());
        }
        if (x.previousSchool() != null) {
            d.setPreviousSchool(x.previousSchool());
        }
        if (x.previousClass() != null) {
            d.setPreviousClass(x.previousClass());
        }
        if (x.tcNumberIn() != null) {
            d.setTcNumberIn(x.tcNumberIn());
        }
        if (x.medicalNotes() != null) {
            d.setMedicalNotes(x.medicalNotes());
        }
        if (x.photoUrl() != null) {
            d.setPhotoUrl(x.photoUrl());
        }
    }

    private static ProfileDetailsResponse details(StudentProfileDetail d) {
        if (d == null) {
            return null;
        }
        return new ProfileDetailsResponse(d.getBloodGroup(), d.getNationality(), d.getMotherTongue(), d.getHouse(), d.getPreviousSchool(),
                d.getPreviousClass(), d.getTcNumberIn(), d.getTcNumberOut(), d.getMedicalNotes(), d.getPhotoUrl(), d.getLeavingType(),
                d.getLeavingDate(), d.getLeavingReason());
    }

    // ── Leaving / re-admission ───────────────────────────────────────────────

    /**
     * The student leaves the school: status, open enrollments, TC number, and logins (the student's, and each
     * parent's unless they still have another active child here).
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public LeaveResult leave(UUID studentId, LeaveRequest r) {
        Student s = student(studentId);
        LocalDate date = r.date() != null ? r.date() : LocalDate.now();
        StudentStatus target = switch (r.type()) {
            case TRANSFERRED -> StudentStatus.TRANSFERRED;
            case WITHDRAWN -> StudentStatus.WITHDRAWN;
            case GRADUATED -> StudentStatus.GRADUATED;
        };
        boolean disable = r.disableLogins() == null || r.disableLogins();
        if (disable) {
            context.requireAuthority("USER_UPDATE", "disable logins (or send disableLogins=false)");
        }
        s.updateStatus(target);
        studentRepository.save(s);

        int closed = 0;
        for (StudentAcademicEnrollment e : enrollmentRepository.findAllByStudentId(studentId)) {
            if (!e.getEnrollmentStatus().isActiveOrAdmitted()) {
                continue;
            }
            LocalDate d = date.isBefore(e.getEnrollmentDate()) ? e.getEnrollmentDate() : date;
            if (r.type() == LeavingType.GRADUATED && e.getEnrollmentStatus() == StudentEnrollmentStatus.ACTIVE) {
                e.complete(d);
            } else {
                e.withdraw(d, r.reason() != null ? r.reason() : r.type().name().toLowerCase(Locale.ROOT).replace('_', ' '), r.remarks());
            }
            enrollmentRepository.save(e);
            closed++;
        }

        StudentProfileDetail d = detailRepository.findById(studentId).orElseGet(() -> new StudentProfileDetail(studentId));
        d.setLeavingType(r.type().name());
        d.setLeavingDate(date);
        d.setLeavingReason(r.reason());
        if (r.tcNumber() != null && !r.tcNumber().isBlank()) {
            d.setTcNumberOut(r.tcNumber().trim());
        }
        detailRepository.save(d);

        boolean studentLoginDisabled = false;
        List<String> parentsDisabled = new ArrayList<>();
        List<String> parentsKept = new ArrayList<>();
        if (disable) {
            TenantUser u = s.getTenantUser();
            if (u != null && u.getMasterUserId() != null) {
                accounts.disable(u);
                studentLoginDisabled = true;
            }
            for (TenantUser parent : parents.linkedParents(studentId)) {
                if (parent.getMasterUserId() == null) {
                    continue;
                }
                String label = TenantUserAccountService.name(parent) + " (" + parent.getUserCode() + ")";
                if (parents.hasOtherActiveChild(parent.getId(), studentId)) {
                    parentsKept.add(label);
                } else {
                    accounts.disable(parent);
                    parentsDisabled.add(label);
                }
            }
        }
        List<String> next = new ArrayList<>();
        if (r.type() == LeavingType.TRANSFERRED && (r.tcNumber() == null || r.tcNumber().isBlank())) {
            next.add("Record the transfer certificate number (PUT profile details tcNumber, or send tcNumber here)");
        }
        if (!disable) {
            next.add("Logins were left active; disable them with PATCH /api/v1/tenant/users/{id}/account/status");
        }
        if (r.type() == LeavingType.WITHDRAWN) {
            next.add("If the student comes back: POST /api/v1/student-setup/students/{id}/readmit");
        }
        log.info("Student [{}] left: {} on {}", s.getAdmissionNumber(), r.type(), date);
        return new LeaveResult(s.getStatus(), date, d.getTcNumberOut(), closed, studentLoginDisabled, parentsDisabled, parentsKept, next);
    }

    /** A withdrawn / inactive student comes back: new enrollment in the given class, logins re-enabled on request. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public AdmitResult readmit(UUID studentId, ReadmitRequest r) {
        Student s = student(studentId);
        if (s.getStatus() == StudentStatus.TRANSFERRED || s.getStatus() == StudentStatus.GRADUATED) {
            throw new BusinessException("A " + s.getStatus() + " student cannot be re-admitted on the same record; admit afresh (the previous "
                    + "admission number stays with the old record)");
        }
        boolean enable = Boolean.TRUE.equals(r.enableLogins());
        if (enable) {
            context.requireAuthority("USER_UPDATE", "re-enable logins");
        }
        if (s.getStatus() != StudentStatus.ACTIVE) {
            s.updateStatus(StudentStatus.ACTIVE);
            studentRepository.save(s);
        }
        LocalDate date = r.date() != null ? r.date() : LocalDate.now();
        StudentAcademicEnrollment e = enrollmentService.createEnrollmentEntity(s, CreateEnrollmentRequest.builder()
                .academicYearId(r.academicYearId()).academicClassId(r.academicClassId()).sectionId(r.sectionId())
                .rollNumber(r.rollNumber()).enrollmentStatus(StudentEnrollmentStatus.ACTIVE).admissionDate(s.getAdmissionDate())
                .enrollmentDate(date).remarks(r.remarks() != null ? r.remarks() : "Re-admitted").build());
        if (r.autoAssignCompulsorySubjects() == null || r.autoAssignCompulsorySubjects()) {
            subjectEnrollmentService.autoEnrollCompulsorySubjects(e);
        }
        detailRepository.findById(studentId).ifPresent(d -> {
            d.setLeavingType(null);
            d.setLeavingDate(null);
            d.setLeavingReason(null);
            detailRepository.save(d);
        });
        List<String> warnings = new ArrayList<>();
        if (enable) {
            TenantUser u = s.getTenantUser();
            if (u != null && u.getMasterUserId() != null) {
                accounts.setActive(u.getId(), true);
            }
            for (TenantUser parent : parents.linkedParents(studentId)) {
                if (parent.getMasterUserId() != null) {
                    accounts.setActive(parent.getId(), true);
                }
            }
        } else {
            warnings.add("Logins were not touched; send enableLogins=true to re-enable the student's and parents' logins");
        }
        return new AdmitResult(StudentMapper.toDetailResponse(s), EnrollmentMapper.toResponse(e), parents.guardiansOf(studentId), null,
                parents.siblingsOf(studentId), warnings);
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public EnrollmentOverview overview(UUID academicYearId) {
        AcademicYear year = academicYearId != null
                ? academicYearRepository.findByIdAndNotDeleted(academicYearId).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId))
                : academicYearRepository.findCurrentYear().orElseThrow(() -> new BusinessException("No current academic year; pass academicYearId"));

        List<StudentAcademicEnrollment> all = enrollmentRepository.findAllByAcademicYearIdWithStudent(year.getId());
        List<StudentAcademicEnrollment> active = all.stream().filter(e -> e.getEnrollmentStatus().isActiveOrAdmitted()).toList();
        long left = all.stream().filter(e -> e.getEnrollmentStatus() == StudentEnrollmentStatus.WITHDRAWN
                || e.getEnrollmentStatus() == StudentEnrollmentStatus.TRANSFERRED).count();
        long newAdmissions = active.stream().map(StudentAcademicEnrollment::getStudent).distinct()
                .filter(s -> s.getAdmissionDate() != null && !s.getAdmissionDate().isBefore(year.getStartDate())
                        && !s.getAdmissionDate().isAfter(year.getEndDate())).count();

        Map<StudentStatus, Long> byStatus = new EnumMap<>(StudentStatus.class);
        List<Student> students = studentRepository.findAllActiveWithUser();
        long studentsWithLogin = 0;
        for (Student s : students) {
            byStatus.merge(s.getStatus(), 1L, Long::sum);
            if (s.getTenantUser() != null && s.getTenantUser().getMasterUserId() != null) {
                studentsWithLogin++;
            }
        }

        // classes and sections
        Map<UUID, Section> sections = sectionRepository.findActiveByAcademicYearId(year.getId()).stream()
                .collect(Collectors.toMap(Section::getId, x -> x));
        Map<UUID, List<StudentAcademicEnrollment>> byClass = active.stream()
                .collect(Collectors.groupingBy(e -> e.getAcademicClass().getId(), LinkedHashMap::new, Collectors.toList()));
        List<ClassCount> classes = new ArrayList<>();
        List<String> fullSections = new ArrayList<>();
        List<String> noSection = new ArrayList<>();
        long withoutSection = 0;
        for (AcademicClass c : classRepository.findByDeletedAtIsNullOrderBySortOrderAsc()) {
            List<StudentAcademicEnrollment> in = byClass.getOrDefault(c.getId(), List.of());
            List<Section> classSections = sections.values().stream().filter(x -> x.getAcademicClass().getId().equals(c.getId()))
                    .sorted(Comparator.comparing(Section::getCode)).toList();
            if (in.isEmpty() && classSections.isEmpty()) {
                continue;
            }
            Map<UUID, Long> perSection = in.stream().filter(e -> e.getSection() != null)
                    .collect(Collectors.groupingBy(e -> e.getSection().getId(), Collectors.counting()));
            List<SectionCount> sc = new ArrayList<>();
            for (Section x : classSections) {
                long n = perSection.getOrDefault(x.getId(), 0L);
                boolean full = x.getCapacity() != null && n >= x.getCapacity();
                if (x.getCapacity() != null && n > x.getCapacity()) {
                    fullSections.add(c.getName() + " " + x.getName() + " (" + n + "/" + x.getCapacity() + ")");
                }
                sc.add(new SectionCount(x.getId(), x.getName(), n, x.getCapacity(), full));
            }
            long none = in.stream().filter(e -> e.getSection() == null).count();
            withoutSection += none;
            in.stream().filter(e -> e.getSection() == null).limit(5).forEach(e -> noSection.add(ParentLinkService.studentName(e.getStudent())));
            classes.add(new ClassCount(c.getId(), c.getName(), in.size(), none, sc));
        }

        // parents
        List<StudentGuardian> guardians = guardianRepository.findAllActiveWithStudent();
        Map<UUID, List<StudentGuardian>> guardiansByStudent = guardians.stream().collect(Collectors.groupingBy(g -> g.getStudent().getId()));
        Set<UUID> parentIds = guardians.stream().map(StudentGuardian::getGuardianUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, TenantUser> parentUsers = parentIds.isEmpty() ? Map.of()
                : tenantUserRepository.findAllActiveByIdIn(parentIds).stream().collect(Collectors.toMap(TenantUser::getId, u -> u));
        long parentsWithLogin = parentUsers.values().stream().filter(u -> u.getMasterUserId() != null).count();

        List<String> noGuardian = new ArrayList<>();
        List<String> noParentLogin = new ArrayList<>();
        List<String> noRoll = new ArrayList<>();
        List<String> noDob = new ArrayList<>();
        long unlinkedGuardians = guardians.stream().filter(g -> g.getGuardianUserId() == null).count();
        Set<UUID> seen = new HashSet<>();
        for (StudentAcademicEnrollment e : active) {
            Student s = e.getStudent();
            if (!seen.add(s.getId())) {
                continue;
            }
            String name = ParentLinkService.studentName(s);
            List<StudentGuardian> gs = guardiansByStudent.getOrDefault(s.getId(), List.of());
            if (gs.isEmpty()) {
                noGuardian.add(name);
            } else if (gs.stream().noneMatch(g -> g.getGuardianUserId() != null && parentUsers.containsKey(g.getGuardianUserId())
                    && parentUsers.get(g.getGuardianUserId()).getMasterUserId() != null)) {
                noParentLogin.add(name);
            }
            if (e.getRollNumber() == null || e.getRollNumber().isBlank()) {
                noRoll.add(name);
            }
            TenantUserProfile p = s.getTenantUser() != null ? s.getTenantUser().getProfile() : null;
            if (p == null || p.getDateOfBirth() == null) {
                noDob.add(name);
            }
        }

        // promotion pending: active last year, nothing this year
        long pendingPromotion = 0;
        AcademicYear previous = academicYearRepository.findAll().stream()
                .filter(y -> y.getDeletedAt() == null && y.getStartDate().isBefore(year.getStartDate()))
                .max(Comparator.comparing(AcademicYear::getStartDate)).orElse(null);
        if (previous != null) {
            Set<UUID> here = new HashSet<>(enrollmentRepository.findStudentIdsWithActiveEnrollment(year.getId()));
            pendingPromotion = enrollmentRepository.findStudentIdsWithActiveEnrollment(previous.getId()).stream().filter(id -> !here.contains(id)).count();
        }

        List<Issue> issues = new ArrayList<>();
        if (pendingPromotion > 0) {
            issues.add(new Issue("WARN", "PENDING_PROMOTION", pendingPromotion, pendingPromotion + " student(s) were active in " + previous.getCode()
                    + " and have no enrollment in " + year.getCode() + "; run the promotion (POST /api/v1/academic-years/" + previous.getId()
                    + "/promotion/preview).", List.of()));
        }
        issue(issues, "WARN", "NO_SECTION", withoutSection, noSection, " student(s) have a class but no section; attendance needs a section.");
        issue(issues, "WARN", "SECTION_OVER_CAPACITY", fullSections.size(), fullSections, " section(s) hold more students than their capacity.");
        issue(issues, "WARN", "NO_GUARDIAN", noGuardian.size(), noGuardian, " student(s) have no guardian on record.");
        issue(issues, "INFO", "NO_PARENT_LOGIN", noParentLogin.size(), noParentLogin, " student(s) have guardians but no parent with a login.");
        issue(issues, "INFO", "GUARDIAN_NOT_LINKED", unlinkedGuardians, List.of(), " guardian row(s) are not linked to a parent record"
                + " (POST /api/v1/student-setup/students/{id}/guardians/{gid}/parent).");
        issue(issues, "INFO", "NO_ROLL_NUMBER", noRoll.size(), noRoll, " student(s) have no roll number.");
        issue(issues, "INFO", "NO_DATE_OF_BIRTH", noDob.size(), noDob, " student(s) have no date of birth.");

        return new EnrollmentOverview(year.getId(), year.getCode(), seen.size(), newAdmissions, left, withoutSection, byStatus, classes,
                parentUsers.size(), parentsWithLogin, studentsWithLogin, pendingPromotion, issues);
    }

    private static void issue(List<Issue> out, String level, String code, long count, List<String> examples, String message) {
        if (count > 0) {
            out.add(new Issue(level, code, count, count + message, examples.stream().limit(5).toList()));
        }
    }

    private Student student(UUID id) {
        return studentRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }
}
