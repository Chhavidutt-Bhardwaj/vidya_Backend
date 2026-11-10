package com.vidya.tenantdata.staff.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.security.access.account.SchoolAccountService;
import com.vidya.security.access.dto.AssignRoleRequest;
import com.vidya.security.access.service.AccessControlService;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Department;
import com.vidya.tenantdata.staff.entity.DutyType;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.entity.StaffDuty;
import com.vidya.tenantdata.staff.enums.DutyCategory;
import com.vidya.tenantdata.staff.enums.DutyScope;
import com.vidya.tenantdata.staff.enums.DutyStatus;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.OrgUnitStatus;
import com.vidya.tenantdata.staff.repository.DepartmentRepository;
import com.vidya.tenantdata.staff.repository.DutyTypeRepository;
import com.vidya.tenantdata.staff.repository.StaffDutyRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyGroup;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyRequest;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyResponse;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyTypeRequest;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyTypeResponse;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.EndDutyRequest;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.StaffDutiesResponse;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.TeachingAssignment;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.account.AccountLinkedEvent;
import com.vidya.tenantdata.user.account.SchoolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Duties (responsibilities) of staff. A teacher can at the same time be HOD of Science, coordinator of the
 * middle stage and exam coordinator; each duty has its own scope, period and, optionally, an access role that
 * is put on the holder's login while the duty is active and taken off when it ends.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffDutyService {

    /** Roles created in the school (with a standard permission set) the first time a duty needs them. */
    record StandardRole(String name, String description, Set<String> permissions) {
    }

    static final Map<String, StandardRole> STANDARD_ROLES = Map.of(
            "EXAM_COORDINATOR", new StandardRole("Exam Coordinator", "Plans exams, enters and locks marks, publishes results and report cards",
                    Set.of("EXAM_READ", "EXAM_CREATE", "EXAM_UPDATE", "EXAM_PUBLISH", "MARKS_READ", "MARKS_ENTRY", "MARKS_LOCK",
                            "REPORT_CARD_READ", "REPORT_CARD_GENERATE", "REPORT_CARD_PUBLISH", "REPORT_CARD_REMARK_MANAGE",
                            "STUDENT_READ", "STUDENT_SCOPE_ALL")),
            "TIMETABLE_INCHARGE", new StandardRole("Timetable In-charge", "Builds and publishes the timetable, arranges substitutions",
                    Set.of("TIMETABLE_READ", "TIMETABLE_CREATE", "TIMETABLE_UPDATE", "TIMETABLE_PUBLISH", "TIMETABLE_VALIDATE",
                            "TIMETABLE_ENTRY_READ", "TIMETABLE_ENTRY_CREATE", "TIMETABLE_ENTRY_UPDATE", "TIMETABLE_ENTRY_DELETE",
                            "TIMETABLE_SCOPE_ALL", "SUBSTITUTION_READ", "SUBSTITUTION_MANAGE", "BELL_SCHEDULE_READ", "BELL_SCHEDULE_CREATE",
                            "BELL_SCHEDULE_UPDATE", "STAFF_READ", "STAFF_SCOPE_ALL")),
            "ATTENDANCE_INCHARGE", new StandardRole("Attendance In-charge", "Marks and corrects attendance for the whole school",
                    Set.of("ATTENDANCE_READ", "ATTENDANCE_CREATE", "ATTENDANCE_UPDATE", "ATTENDANCE_BULK_MARK", "ATTENDANCE_STATUS_UPDATE",
                            "ATTENDANCE_OVERRIDE_NON_WORKING", "ATTENDANCE_SCOPE_ALL", "STUDENT_READ", "STUDENT_SCOPE_ALL")),
            "ADMISSION_INCHARGE", new StandardRole("Admissions In-charge", "Admits, enrols and transfers students and their guardians",
                    Set.of("STUDENT_READ", "STUDENT_CREATE", "STUDENT_UPDATE", "STUDENT_ENROLL", "STUDENT_TRANSFER", "STUDENT_WITHDRAW",
                            "STUDENT_SCOPE_ALL", "STUDENT_GUARDIAN_READ", "STUDENT_GUARDIAN_CREATE", "STUDENT_GUARDIAN_UPDATE",
                            "STUDENT_ACADEMIC_ASSIGNMENT_READ", "STUDENT_ACADEMIC_ASSIGNMENT_CREATE", "STUDENT_ACADEMIC_ASSIGNMENT_UPDATE")),
            "LIBRARY_INCHARGE", new StandardRole("Library In-charge", "Manages the catalogue and issues books",
                    Set.of("LIBRARY_READ", "LIBRARY_MANAGE")));

    static final String HOD = "HOD";

    private final DutyTypeRepository dutyTypeRepository;
    private final StaffDutyRepository dutyRepository;
    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final AcademicClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicYearRepository yearRepository;
    private final ClassTeacherAssignmentRepository classTeacherRepository;
    private final TeacherSubjectAssignmentRepository subjectTeacherRepository;
    private final AccessControlService accessControlService;
    private final SchoolAccountService accounts;
    private final SchoolContext context;

    /** A validated duty request, ready to apply to a staff member. */
    public record Prepared(DutyType type, DutyRequest req, UUID departmentId, ClassStage stage, UUID classId, UUID sectionId, UUID subjectId,
                           UUID yearId, LocalDate start, String scopeLabel) {
    }

    // ── Duty types ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<DutyTypeResponse> dutyTypes() {
        Map<UUID, Long> holders = activeHolders();
        return dutyTypeRepository.findAllNotDeleted().stream().map(t -> toResponse(t, holders)).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DutyTypeResponse createDutyType(DutyTypeRequest r) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        dutyTypeRepository.findByCode(code).ifPresent(x -> {
            throw new DuplicateResourceException("DutyType", "code", code);
        });
        dutyTypeRepository.findByName(r.name().trim()).ifPresent(x -> {
            throw new DuplicateResourceException("DutyType", "name", r.name());
        });
        DutyType t = new DutyType(code, r.name().trim(), r.category() != null ? r.category() : DutyCategory.ADMINISTRATION,
                r.scopeKind() != null ? r.scopeKind() : DutyScope.SCHOOL, r.singleHolder() == null || r.singleHolder(), roleCode(r.accessRoleCode()));
        t.setDescription(r.description());
        return toResponse(dutyTypeRepository.save(t), Map.of());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DutyTypeResponse updateDutyType(UUID id, DutyTypeRequest r) {
        DutyType t = dutyType(id);
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        dutyTypeRepository.findByCode(code).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
            throw new DuplicateResourceException("DutyType", "code", code);
        });
        dutyTypeRepository.findByName(r.name().trim()).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
            throw new DuplicateResourceException("DutyType", "name", r.name());
        });
        if (t.isSystem()) {
            if (!t.getCode().equals(code) || (r.scopeKind() != null && r.scopeKind() != t.getScopeKind())) {
                throw new BusinessException("The code and scope of the standard duty " + t.getName() + " cannot be changed");
            }
        } else {
            t.setCode(code);
            if (r.scopeKind() != null && r.scopeKind() != t.getScopeKind() && dutyRepository.existsByDutyTypeIdAndStatus(id, DutyStatus.ACTIVE)) {
                throw new BusinessException("End the active " + t.getName() + " duties before changing what the duty is for");
            }
            if (r.scopeKind() != null) {
                t.setScopeKind(r.scopeKind());
            }
        }
        t.setName(r.name().trim());
        t.setDescription(r.description());
        if (r.category() != null) {
            t.setCategory(r.category());
        }
        if (r.singleHolder() != null) {
            t.setSingleHolder(r.singleHolder());
        }
        t.setAccessRoleCode(roleCode(r.accessRoleCode()));
        return toResponse(t, activeHolders());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DutyTypeResponse setDutyTypeStatus(UUID id, OrgUnitStatus status) {
        if (status == OrgUnitStatus.DELETED) {
            throw new BusinessException("Use DELETE to remove a duty type");
        }
        DutyType t = dutyType(id);
        t.setStatus(status);
        return toResponse(t, activeHolders());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteDutyType(UUID id) {
        DutyType t = dutyType(id);
        if (t.isSystem()) {
            throw new BusinessException("Standard duties cannot be deleted; set " + t.getName() + " INACTIVE instead");
        }
        if (dutyRepository.countByDutyTypeId(id) > 0) {
            throw new BusinessException(t.getName() + " has been assigned to staff; set it INACTIVE instead");
        }
        t.setStatus(OrgUnitStatus.DELETED);
        t.setDeletedAt(Instant.now());
    }

    // ── Assigning and ending duties ──────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public DutyResponse assign(UUID staffId, DutyRequest r) {
        Staff staff = activeStaff(staffId);
        List<String> errors = new ArrayList<>();
        Prepared p = prepare(r, staffId, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("; ", errors));
        }
        if (p.type().getAccessRoleCode() != null && staff.getTenantUser().getMasterUserId() != null) {
            context.requireAuthority("SCHOOL_ROLE_MANAGE", "give the " + p.type().getAccessRoleCode() + " role that comes with this duty");
        }
        StaffDuty d = apply(staff, p);
        return toResponse(d, p.type(), Map.of(staff.getId(), staff));
    }

    /** Validates a request; returns null and adds to {@code errors} when it cannot be applied. */
    public Prepared prepare(DutyRequest r, UUID staffId, List<String> errors) {
        Optional<DutyType> found = r.dutyTypeId() != null ? dutyTypeRepository.findByIdAndNotDeleted(r.dutyTypeId())
                : r.dutyTypeCode() != null && !r.dutyTypeCode().isBlank() ? dutyTypeRepository.findByCode(r.dutyTypeCode().trim()) : Optional.empty();
        if (found.isEmpty()) {
            errors.add("duty " + (r.dutyTypeId() != null ? r.dutyTypeId() : r.dutyTypeCode()) + " not found (send dutyTypeId or dutyTypeCode)");
            return null;
        }
        DutyType type = found.get();
        if (!type.isActive()) {
            errors.add("duty " + type.getName() + " is inactive");
            return null;
        }
        UUID departmentId = null;
        ClassStage stage = null;
        UUID classId = null;
        UUID sectionId = null;
        UUID subjectId = null;
        UUID yearId = r.academicYearId();
        String label = "School";
        int before = errors.size();
        switch (type.getScopeKind()) {
            case SCHOOL -> {
                if (r.departmentId() != null || r.classStage() != null || r.academicClassId() != null || r.sectionId() != null || r.subjectId() != null) {
                    errors.add(type.getName() + " is a school-wide duty; it takes no department, stage, class, section or subject");
                }
            }
            case DEPARTMENT -> {
                Department d = r.departmentId() != null ? departmentRepository.findByIdAndNotDeleted(r.departmentId()).orElse(null) : null;
                if (d == null || !d.isActive()) {
                    errors.add(type.getName() + " needs an active departmentId");
                } else {
                    departmentId = d.getId();
                    label = d.getName();
                }
            }
            case STAGE -> {
                if (r.classStage() == null) {
                    errors.add(type.getName() + " needs a classStage (PRE_PRIMARY, PRIMARY, MIDDLE, SECONDARY, SENIOR_SECONDARY)");
                } else {
                    stage = r.classStage();
                    label = stageLabel(stage);
                }
            }
            case CLASS -> {
                AcademicClass c = r.academicClassId() != null ? classRepository.findByIdAndNotDeleted(r.academicClassId()).orElse(null) : null;
                if (c == null) {
                    errors.add(type.getName() + " needs an academicClassId");
                } else {
                    classId = c.getId();
                    label = className(c);
                }
            }
            case SECTION -> {
                Section s = r.sectionId() != null ? sectionRepository.findByIdAndNotDeleted(r.sectionId()).orElse(null) : null;
                if (s == null) {
                    errors.add(type.getName() + " needs a sectionId");
                } else {
                    sectionId = s.getId();
                    classId = s.getAcademicClass().getId();
                    yearId = s.getAcademicYear().getId();
                    label = className(s.getAcademicClass()) + " " + s.getName();
                }
            }
            case SUBJECT -> {
                Subject s = r.subjectId() != null ? subjectRepository.findByIdAndNotDeleted(r.subjectId()).orElse(null) : null;
                if (s == null) {
                    errors.add(type.getName() + " needs a subjectId");
                } else {
                    subjectId = s.getId();
                    label = s.getName();
                }
            }
        }
        if (yearId != null && yearRepository.findByIdAndNotDeleted(yearId).isEmpty()) {
            errors.add("academic year " + yearId + " not found");
        }
        if (errors.size() > before) {
            return null;
        }
        LocalDate start = r.startDate() != null ? r.startDate() : LocalDate.now();
        Prepared p = new Prepared(type, r, departmentId, stage, classId, sectionId, subjectId, yearId, start, label);
        StaffDuty probe = toEntity(null, p);
        for (StaffDuty cur : dutyRepository.findByDutyTypeIdAndStatus(type.getId(), DutyStatus.ACTIVE)) {
            if (!cur.sameScope(probe)) {
                continue;
            }
            if (cur.getStaffId().equals(staffId)) {
                errors.add("already " + type.getName() + (label.equals("School") ? "" : " of " + label));
                return null;
            }
            if (type.isSingleHolder() && !Boolean.TRUE.equals(r.replace())) {
                String holder = staffRepository.findByIdAndNotDeleted(cur.getStaffId()).map(StaffNames::name).orElse("someone else");
                errors.add(holder + " is already " + type.getName() + " of " + label + "; send replace=true to hand over");
                return null;
            }
        }
        return p;
    }

    /** Saves the duty, hands over from the previous holder when asked, syncs the department head and grants the role. */
    public StaffDuty apply(Staff staff, Prepared p) {
        StaffDuty duty = toEntity(staff.getId(), p);
        if (p.type().isSingleHolder()) {
            for (StaffDuty cur : dutyRepository.findByDutyTypeIdAndStatus(p.type().getId(), DutyStatus.ACTIVE)) {
                if (cur.sameScope(duty) && !cur.getStaffId().equals(staff.getId())) {
                    endDuty(cur, p.type(), p.start().minusDays(1).isBefore(cur.getStartDate()) ? cur.getStartDate() : p.start().minusDays(1));
                    log.info("Duty {} of {} handed over from staff {} to {}", p.type().getCode(), p.scopeLabel(), cur.getStaffId(), staff.getId());
                }
            }
        }
        duty.setCreatedBy(context.actorId());
        duty = dutyRepository.save(duty);
        if (HOD.equals(p.type().getCode()) && duty.getDepartmentId() != null) {
            departmentRepository.findByIdAndNotDeleted(duty.getDepartmentId()).ifPresent(d -> d.setHeadStaffId(staff.getId()));
        }
        grantRole(duty, p.type(), staff);
        log.info("Duty {} ({}) given to staff {}", p.type().getCode(), p.scopeLabel(), staff.getStaffCode());
        return duty;
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DutyResponse end(UUID dutyId, EndDutyRequest r) {
        StaffDuty d = dutyRepository.findById(dutyId).orElseThrow(() -> new ResourceNotFoundException("StaffDuty", dutyId));
        if (d.getStatus() != DutyStatus.ACTIVE) {
            throw new BusinessException("This duty already ended on " + d.getEndDate());
        }
        DutyType type = dutyTypeRepository.findById(d.getDutyTypeId()).orElseThrow();
        endDuty(d, type, r != null && r.endDate() != null ? r.endDate() : LocalDate.now());
        Staff staff = staffRepository.findById(d.getStaffId()).orElse(null);
        return toResponse(d, type, staff != null ? Map.of(staff.getId(), staff) : Map.of());
    }

    /** Ends every active duty of a staff member (offboarding). Returns the duty names ended. */
    public List<String> endAllFor(UUID staffId, LocalDate on) {
        Map<UUID, DutyType> types = types();
        List<String> ended = new ArrayList<>();
        for (StaffDuty d : dutyRepository.findByStaffIdAndStatus(staffId, DutyStatus.ACTIVE)) {
            DutyType t = types.get(d.getDutyTypeId());
            endDuty(d, t, on);
            ended.add(t.getName() + (d.getDepartmentId() != null || d.getClassStage() != null || d.getSubjectId() != null || d.getAcademicClassId() != null
                    ? " (" + scopeLabel(d) + ")" : ""));
        }
        return ended;
    }

    /** Department head as a duty: null ends the current HOD duty, a staff id hands the department over. */
    public void setDepartmentHead(Department department, UUID staffId) {
        DutyType hod = dutyTypeRepository.findByCode(HOD).orElse(null);
        if (hod == null) {
            department.setHeadStaffId(staffId);
            return;
        }
        if (staffId == null) {
            for (StaffDuty d : dutyRepository.findByDutyTypeIdAndStatus(hod.getId(), DutyStatus.ACTIVE)) {
                if (department.getId().equals(d.getDepartmentId())) {
                    endDuty(d, hod, LocalDate.now());
                }
            }
            department.setHeadStaffId(null);
            return;
        }
        if (staffId.equals(department.getHeadStaffId())) {
            return;
        }
        Staff staff = activeStaff(staffId);
        List<String> errors = new ArrayList<>();
        Prepared p = prepare(new DutyRequest(hod.getId(), null, department.getId(), null, null, null, null, null, null, null, true), staffId, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("; ", errors));
        }
        apply(staff, p);
    }

    private void endDuty(StaffDuty d, DutyType type, LocalDate on) {
        d.end(on);
        d.setUpdatedBy(context.actorId());
        if (HOD.equals(type.getCode()) && d.getDepartmentId() != null) {
            departmentRepository.findByIdAndNotDeleted(d.getDepartmentId())
                    .filter(dep -> d.getStaffId().equals(dep.getHeadStaffId())).ifPresent(dep -> dep.setHeadStaffId(null));
        }
        revokeRole(d, type);
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StaffDutiesResponse dutiesOf(UUID staffId, boolean includeEnded) {
        Staff staff = staffRepository.findByIdAndNotDeleted(staffId).orElseThrow(() -> new ResourceNotFoundException("Staff", staffId));
        Map<UUID, DutyType> types = types();
        List<DutyResponse> duties = dutyRepository.findByStaffIdOrderByStartDateDesc(staffId).stream()
                .filter(d -> includeEnded || d.getStatus() == DutyStatus.ACTIVE)
                .map(d -> toResponse(d, types.get(d.getDutyTypeId()), Map.of(staffId, staff))).toList();
        List<TeachingAssignment> classTeacher = classTeacherRepository.findActiveByStaffId(staffId, null).stream().map(StaffDutyService::toAssignment).toList();
        List<TeachingAssignment> teaches = subjectTeacherRepository.findActiveByTeacherId(staffId, null).stream().map(StaffDutyService::toAssignment).toList();
        return new StaffDutiesResponse(staff.getId(), staff.getStaffCode(), StaffNames.name(staff), staff.getDesignation(), duties, classTeacher, teaches);
    }

    /** Who does what: every duty type with its current holders. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<DutyGroup> directory() {
        List<StaffDuty> active = dutyRepository.findByStatus(DutyStatus.ACTIVE);
        Map<UUID, Staff> staff = staffRepository.findAllById(active.stream().map(StaffDuty::getStaffId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));
        Map<UUID, Long> holders = active.stream().collect(Collectors.groupingBy(StaffDuty::getDutyTypeId, Collectors.counting()));
        List<DutyGroup> out = new ArrayList<>();
        for (DutyType t : dutyTypeRepository.findAllNotDeleted()) {
            List<DutyResponse> h = active.stream().filter(d -> d.getDutyTypeId().equals(t.getId())).map(d -> toResponse(d, t, staff))
                    .sorted((a, b) -> a.scopeLabel().compareToIgnoreCase(b.scopeLabel())).toList();
            out.add(new DutyGroup(toResponse(t, holders), h));
        }
        return out;
    }

    /** Active duties of many staff at once (for lists). */
    public Map<UUID, List<String>> activeDutyNames(Set<UUID> staffIds) {
        Map<UUID, DutyType> types = types();
        Map<UUID, List<String>> out = new HashMap<>();
        for (StaffDuty d : dutyRepository.findByStaffIdInAndStatus(staffIds, DutyStatus.ACTIVE)) {
            DutyType t = types.get(d.getDutyTypeId());
            out.computeIfAbsent(d.getStaffId(), k -> new ArrayList<>()).add(t.getName() + (t.getScopeKind() == DutyScope.SCHOOL ? "" : " · " + scopeLabel(d)));
        }
        return out;
    }

    // ── Roles that come with duties ──────────────────────────────────────────

    /** When a staff member gets a login later, the roles of their active duties are put on it. */
    @EventListener
    public void onAccountLinked(AccountLinkedEvent e) {
        staffRepository.findByTenantUserIdAndNotDeleted(e.tenantUserId()).ifPresent(staff -> {
            Map<UUID, DutyType> types = types();
            for (StaffDuty d : dutyRepository.findByStaffIdAndStatus(staff.getId(), DutyStatus.ACTIVE)) {
                DutyType t = types.get(d.getDutyTypeId());
                if (t.getAccessRoleCode() != null) {
                    grantRole(d, t, staff);
                }
            }
        });
    }

    private void grantRole(StaffDuty duty, DutyType type, Staff staff) {
        String code = type.getAccessRoleCode();
        UUID userId = staff.getTenantUser() != null ? staff.getTenantUser().getMasterUserId() : null;
        if (code == null || userId == null) {
            return;
        }
        UUID schoolId = context.schoolId();
        if (accounts.get(schoolId, userId).role().name().equals(code)) {
            return; // already their account type
        }
        UUID roleId = roleId(code, schoolId);
        var a = accessControlService.assignRole(userId, new AssignRoleRequest(roleId, null, null, "Duty: " + type.getName()), schoolId);
        duty.setRoleAssignmentId(a.assignmentId());
    }

    private void revokeRole(StaffDuty duty, DutyType type) {
        if (duty.getRoleAssignmentId() == null) {
            return;
        }
        UUID assignmentId = duty.getRoleAssignmentId();
        duty.setRoleAssignmentId(null);
        Map<UUID, DutyType> types = types();
        boolean stillNeeded = dutyRepository.findByStaffIdAndStatus(duty.getStaffId(), DutyStatus.ACTIVE).stream()
                .filter(o -> !o.getId().equals(duty.getId()))
                .anyMatch(o -> type.getAccessRoleCode().equals(types.get(o.getDutyTypeId()).getAccessRoleCode()));
        if (stillNeeded) {
            return;
        }
        staffRepository.findById(duty.getStaffId()).map(s -> s.getTenantUser().getMasterUserId()).ifPresent(userId -> {
            try {
                accessControlService.removeAssignment(userId, assignmentId, context.schoolId());
            } catch (ResourceNotFoundException e) {
                log.debug("Role assignment {} was already removed", assignmentId);
            }
        });
    }

    private UUID roleId(String code, UUID schoolId) {
        StandardRole std = STANDARD_ROLES.get(code);
        if (std != null) {
            return accessControlService.ensureSchoolRole(schoolId, code, std.name(), std.description(), std.permissions());
        }
        return accessControlService.findRoleId(code, schoolId)
                .orElseThrow(() -> new BusinessException("Role " + code + " does not exist for this school; create it under Access → Roles first"));
    }

    /** A role code a duty may carry: a standard duty role, or an existing global / school role. */
    private String roleCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.trim().toUpperCase(Locale.ROOT);
        if (!STANDARD_ROLES.containsKey(c) && accessControlService.findRoleId(c, context.schoolId()).isEmpty()) {
            throw new BusinessException("Role " + c + " does not exist; use one of the school's roles or " + STANDARD_ROLES.keySet());
        }
        return c;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Staff activeStaff(UUID staffId) {
        Staff s = staffRepository.findByIdAndNotDeleted(staffId).orElseThrow(() -> new ResourceNotFoundException("Staff", staffId));
        if (s.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new BusinessException(StaffNames.name(s) + " is " + s.getEmploymentStatus() + "; only active staff can hold duties");
        }
        return s;
    }

    private DutyType dutyType(UUID id) {
        return dutyTypeRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("DutyType", id));
    }

    private Map<UUID, DutyType> types() {
        return dutyTypeRepository.findAll().stream().collect(Collectors.toMap(DutyType::getId, Function.identity()));
    }

    private Map<UUID, Long> activeHolders() {
        return dutyRepository.findByStatus(DutyStatus.ACTIVE).stream().collect(Collectors.groupingBy(StaffDuty::getDutyTypeId, Collectors.counting()));
    }

    private static StaffDuty toEntity(UUID staffId, Prepared p) {
        StaffDuty d = new StaffDuty(staffId, p.type().getId(), p.start(), p.req().notes());
        d.setDepartmentId(p.departmentId());
        d.setClassStage(p.stage());
        d.setAcademicClassId(p.classId());
        d.setSectionId(p.sectionId());
        d.setSubjectId(p.subjectId());
        d.setAcademicYearId(p.yearId());
        return d;
    }

    private String scopeLabel(StaffDuty d) {
        if (d.getDepartmentId() != null) {
            return departmentRepository.findById(d.getDepartmentId()).map(Department::getName).orElse("department");
        }
        if (d.getClassStage() != null) {
            return stageLabel(d.getClassStage());
        }
        if (d.getSectionId() != null) {
            return sectionRepository.findById(d.getSectionId()).map(s -> className(s.getAcademicClass()) + " " + s.getName()).orElse("section");
        }
        if (d.getAcademicClassId() != null) {
            return classRepository.findById(d.getAcademicClassId()).map(StaffDutyService::className).orElse("class");
        }
        if (d.getSubjectId() != null) {
            return subjectRepository.findById(d.getSubjectId()).map(Subject::getName).orElse("subject");
        }
        return "School";
    }

    private static String className(AcademicClass c) {
        return c.getDisplayName() != null ? c.getDisplayName() : c.getName();
    }

    static String stageLabel(ClassStage s) {
        return switch (s) {
            case PRE_PRIMARY -> "Pre-Primary";
            case PRIMARY -> "Primary (I–V)";
            case MIDDLE -> "Middle (VI–VIII)";
            case SECONDARY -> "Secondary (IX–X)";
            case SENIOR_SECONDARY -> "Senior Secondary (XI–XII)";
        };
    }

    private DutyResponse toResponse(StaffDuty d, DutyType t, Map<UUID, Staff> staff) {
        Staff s = staff.get(d.getStaffId());
        return new DutyResponse(d.getId(), d.getStaffId(), s != null ? StaffNames.name(s) : null, t.getId(), t.getCode(), t.getName(), t.getCategory(),
                t.getScopeKind(), scopeLabel(d), d.getDepartmentId(), d.getClassStage(), d.getAcademicClassId(), d.getSectionId(), d.getSubjectId(),
                d.getAcademicYearId(), d.getStartDate(), d.getEndDate(), d.getStatus(), d.getNotes(), t.getAccessRoleCode(), d.getRoleAssignmentId() != null);
    }

    private static DutyTypeResponse toResponse(DutyType t, Map<UUID, Long> holders) {
        return new DutyTypeResponse(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.getCategory(), t.getScopeKind(), t.isSingleHolder(),
                t.getAccessRoleCode(), t.isSystem(), t.getStatus(), holders.getOrDefault(t.getId(), 0L));
    }

    private static TeachingAssignment toAssignment(ClassTeacherAssignment a) {
        return new TeachingAssignment(a.getId(), "CLASS_TEACHER", "Class teacher · " + className(a.getAcademicClass()) + " " + a.getSection().getName()
                + " (" + a.getAcademicYear().getName() + ")", a.getAcademicYear().getId(), a.getAcademicClass().getId(), a.getSection().getId(), null,
                a.getStartDate());
    }

    private static TeachingAssignment toAssignment(TeacherSubjectAssignment a) {
        String where = className(a.getAcademicClass()) + (a.getSection() != null ? " " + a.getSection().getName() : "");
        return new TeachingAssignment(a.getId(), "SUBJECT_TEACHER", a.getSubject().getName() + " · " + where + " (" + a.getAcademicYear().getName() + ")",
                a.getAcademicYear().getId(), a.getAcademicClass().getId(), a.getSection() != null ? a.getSection().getId() : null, a.getSubject().getId(),
                a.getStartDate());
    }
}
