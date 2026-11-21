package com.vidya.tenantdata.staff.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.security.access.account.SchoolAccountService;
import com.vidya.security.access.account.SchoolAccountService.Account;
import com.vidya.tenantdata.user.account.SchoolContext;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import com.vidya.tenantdata.staff.entity.Department;
import com.vidya.tenantdata.staff.entity.Designation;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.DepartmentCategory;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.OrgUnitStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.DepartmentRepository;
import com.vidya.tenantdata.staff.repository.DesignationRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.Count;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DepartmentRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DepartmentResponse;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DesignationRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DesignationResponse;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.Issue;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.StaffOverview;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.TemplateRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.TemplateResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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

/** Departments, designations, the standard Indian-school template and the staff overview. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffOrgService {

    /** code, name, category */
    static final List<String[]> DEPARTMENTS = List.of(
            new String[]{"PRE_PRIMARY", "Pre-Primary", "ACADEMIC"},
            new String[]{"LANGUAGES", "Languages", "ACADEMIC"},
            new String[]{"MATHEMATICS", "Mathematics", "ACADEMIC"},
            new String[]{"SCIENCE", "Science", "ACADEMIC"},
            new String[]{"SOCIAL_SCIENCE", "Social Science", "ACADEMIC"},
            new String[]{"COMMERCE", "Commerce", "ACADEMIC"},
            new String[]{"COMPUTER", "Computer Science", "ACADEMIC"},
            new String[]{"PHYSICAL_ED", "Physical Education & Sports", "ACADEMIC"},
            new String[]{"ARTS", "Arts, Music & Dance", "ACADEMIC"},
            new String[]{"COUNSELLING", "Counselling & Special Education", "ACADEMIC"},
            new String[]{"LIBRARY", "Library", "ACADEMIC"},
            new String[]{"ADMIN", "Administration", "ADMINISTRATIVE"},
            new String[]{"ACCOUNTS", "Accounts & Fees", "ADMINISTRATIVE"},
            new String[]{"IT", "IT & Systems", "ADMINISTRATIVE"},
            new String[]{"TRANSPORT", "Transport", "SUPPORT"},
            new String[]{"ESTATE", "Housekeeping & Maintenance", "SUPPORT"},
            new String[]{"SECURITY", "Security", "SUPPORT"},
            new String[]{"HEALTH", "Health & Infirmary", "SUPPORT"});

    /** code, name, staff type, default role ("" = no login), rank, default department code */
    static final List<String[]> DESIGNATIONS = List.of(
            new String[]{"PRINCIPAL", "Principal", "ADMINISTRATIVE", "PRINCIPAL", "10", "ADMIN"},
            new String[]{"VICE_PRINCIPAL", "Vice Principal", "ADMINISTRATIVE", "PRINCIPAL", "20", "ADMIN"},
            new String[]{"HEADMISTRESS", "Headmistress / Headmaster", "ADMINISTRATIVE", "PRINCIPAL", "25", "ADMIN"},
            new String[]{"ADMIN_OFFICER", "Administrative Officer", "ADMINISTRATIVE", "SCHOOL_ADMIN", "30", "ADMIN"},
            new String[]{"COORDINATOR", "Academic Coordinator", "TEACHER", "COORDINATOR", "35", ""},
            new String[]{"HOD", "Head of Department", "TEACHER", "COORDINATOR", "38", ""},
            new String[]{"PGT", "Post Graduate Teacher (PGT)", "TEACHER", "TEACHER", "40", ""},
            new String[]{"TGT", "Trained Graduate Teacher (TGT)", "TEACHER", "TEACHER", "45", ""},
            new String[]{"PRT", "Primary Teacher (PRT)", "TEACHER", "TEACHER", "50", ""},
            new String[]{"NTT", "Nursery Teacher (NTT)", "TEACHER", "TEACHER", "55", "PRE_PRIMARY"},
            new String[]{"PET", "Physical Education Teacher", "TEACHER", "TEACHER", "55", "PHYSICAL_ED"},
            new String[]{"ART_TEACHER", "Art / Music / Dance Teacher", "TEACHER", "TEACHER", "58", "ARTS"},
            new String[]{"SPECIAL_EDUCATOR", "Special Educator", "TEACHER", "TEACHER", "58", "COUNSELLING"},
            new String[]{"COUNSELLOR", "Counsellor", "NON_TEACHING", "SCHOOL_STAFF", "60", "COUNSELLING"},
            new String[]{"LIBRARIAN", "Librarian", "NON_TEACHING", "SCHOOL_STAFF", "60", "LIBRARY"},
            new String[]{"ACCOUNTANT", "Accountant", "ADMINISTRATIVE", "ACCOUNTS", "60", "ACCOUNTS"},
            new String[]{"OFFICE_SUPDT", "Office Superintendent", "ADMINISTRATIVE", "SCHOOL_STAFF", "62", "ADMIN"},
            new String[]{"LAB_ASSISTANT", "Lab Assistant", "NON_TEACHING", "SCHOOL_STAFF", "65", "SCIENCE"},
            new String[]{"IT_ADMIN", "IT Administrator", "ADMINISTRATIVE", "SCHOOL_STAFF", "65", "IT"},
            new String[]{"CLERK", "Clerk / Office Assistant", "ADMINISTRATIVE", "SCHOOL_STAFF", "70", "ADMIN"},
            new String[]{"TRANSPORT_INCHARGE", "Transport In-charge", "SUPPORT", "SCHOOL_STAFF", "70", "TRANSPORT"},
            new String[]{"RECEPTIONIST", "Receptionist", "ADMINISTRATIVE", "SCHOOL_STAFF", "72", "ADMIN"},
            new String[]{"NURSE", "School Nurse", "SUPPORT", "", "75", "HEALTH"},
            new String[]{"DRIVER", "Driver", "SUPPORT", "", "85", "TRANSPORT"},
            new String[]{"BUS_ATTENDANT", "Bus Attendant / Conductor", "SUPPORT", "", "88", "TRANSPORT"},
            new String[]{"AYAH", "Ayah / Caretaker", "SUPPORT", "", "90", "PRE_PRIMARY"},
            new String[]{"PEON", "Peon / Office Attendant", "SUPPORT", "", "90", "ADMIN"},
            new String[]{"HOUSEKEEPING", "Housekeeping Staff", "SUPPORT", "", "90", "ESTATE"},
            new String[]{"SECURITY_GUARD", "Security Guard", "SUPPORT", "", "90", "SECURITY"});

    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final StaffRepository staffRepository;
    private final SchoolAccountService accountService;
    private final StaffDutyService duties;
    private final SchoolContext context;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ── Departments ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<DepartmentResponse> departments() {
        Map<UUID, Long> counts = counts("department_id");
        Map<UUID, String> names = staffNames();
        return departmentRepository.findAllNotDeleted().stream().map(d -> toResponse(d, counts, names)).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DepartmentResponse createDepartment(DepartmentRequest r) {
        String code = code(r.code());
        departmentRepository.findByCode(code).ifPresent(x -> {
            throw new DuplicateResourceException("Department", "code", code);
        });
        departmentRepository.findByName(r.name().trim()).ifPresent(x -> {
            throw new DuplicateResourceException("Department", "name", r.name());
        });
        Department d = departmentRepository.save(new Department(code, r.name().trim(), r.category() != null ? r.category() : DepartmentCategory.ACADEMIC,
                r.description(), r.sortOrder() != null ? r.sortOrder() : 0));
        if (r.headStaffId() != null) {
            duties.setDepartmentHead(d, r.headStaffId());
        }
        return toResponse(d, Map.of(), staffNames());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DepartmentResponse updateDepartment(UUID id, DepartmentRequest r) {
        Department d = department(id);
        String code = code(r.code());
        departmentRepository.findByCode(code).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
            throw new DuplicateResourceException("Department", "code", code);
        });
        departmentRepository.findByName(r.name().trim()).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
            throw new DuplicateResourceException("Department", "name", r.name());
        });
        boolean renamed = !d.getName().equals(r.name().trim());
        d.setCode(code);
        d.setName(r.name().trim());
        d.setDescription(r.description());
        if (r.category() != null) {
            d.setCategory(r.category());
        }
        if (r.sortOrder() != null) {
            d.setSortOrder(r.sortOrder());
        }
        duties.setDepartmentHead(d, r.headStaffId());
        if (renamed) {
            syncText("department", "department_id", id, d.getName());
        }
        return toResponse(d, counts("department_id"), staffNames());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DepartmentResponse setDepartmentStatus(UUID id, OrgUnitStatus status) {
        if (status == OrgUnitStatus.DELETED) {
            throw new BusinessException("Use DELETE to remove a department");
        }
        Department d = department(id);
        d.setStatus(status);
        return toResponse(d, counts("department_id"), staffNames());
    }

    /** Only departments without active staff can be deleted. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteDepartment(UUID id) {
        Department d = department(id);
        long used = counts("department_id").getOrDefault(id, 0L);
        if (used > 0) {
            throw new BusinessException(used + " active staff belong to " + d.getName() + "; move them first or set the department INACTIVE");
        }
        d.setStatus(OrgUnitStatus.DELETED);
        d.setDeletedAt(Instant.now());
    }

    // ── Designations ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<DesignationResponse> designations() {
        Map<UUID, Long> counts = counts("designation_id");
        return designationRepository.findAllNotDeleted().stream().map(d -> toResponse(d, counts)).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DesignationResponse createDesignation(DesignationRequest r) {
        String code = code(r.code());
        designationRepository.findByCode(code).ifPresent(x -> {
            throw new DuplicateResourceException("Designation", "code", code);
        });
        designationRepository.findByName(r.name().trim()).ifPresent(x -> {
            throw new DuplicateResourceException("Designation", "name", r.name());
        });
        Designation d = new Designation(code, r.name().trim(), r.staffType(), role(r.defaultRole()), r.rank() != null ? r.rank() : 100);
        d.setDescription(r.description());
        d.setDefaultDepartmentId(activeDepartment(r.defaultDepartmentId()));
        return toResponse(designationRepository.save(d), Map.of());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DesignationResponse updateDesignation(UUID id, DesignationRequest r) {
        Designation d = designation(id);
        String code = code(r.code());
        designationRepository.findByCode(code).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
            throw new DuplicateResourceException("Designation", "code", code);
        });
        designationRepository.findByName(r.name().trim()).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
            throw new DuplicateResourceException("Designation", "name", r.name());
        });
        boolean renamed = !d.getName().equals(r.name().trim());
        d.setCode(code);
        d.setName(r.name().trim());
        d.setDescription(r.description());
        d.setStaffType(r.staffType());
        d.setDefaultRole(role(r.defaultRole()));
        d.setDefaultDepartmentId(activeDepartment(r.defaultDepartmentId()));
        if (r.rank() != null) {
            d.setRank(r.rank());
        }
        if (renamed) {
            syncText("designation", "designation_id", id, d.getName());
        }
        return toResponse(d, counts("designation_id"));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DesignationResponse setDesignationStatus(UUID id, OrgUnitStatus status) {
        if (status == OrgUnitStatus.DELETED) {
            throw new BusinessException("Use DELETE to remove a designation");
        }
        Designation d = designation(id);
        d.setStatus(status);
        return toResponse(d, counts("designation_id"));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteDesignation(UUID id) {
        Designation d = designation(id);
        long used = counts("designation_id").getOrDefault(id, 0L);
        if (used > 0) {
            throw new BusinessException(used + " active staff have designation " + d.getName() + "; change them first or set it INACTIVE");
        }
        d.setStatus(OrgUnitStatus.DELETED);
        d.setDeletedAt(Instant.now());
    }

    // ── Template ─────────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public TemplateResult applyTemplate(TemplateRequest r) {
        boolean deps = r == null || r.departments() == null || r.departments();
        boolean dess = r == null || r.designations() == null || r.designations();
        boolean dry = r != null && Boolean.TRUE.equals(r.dryRun());
        List<String> depCreated = new ArrayList<>();
        List<String> desCreated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, UUID> depIds = new HashMap<>();
        int order = 1;
        for (String[] t : DEPARTMENTS) {
            var existing = departmentRepository.findByCode(t[0]).or(() -> departmentRepository.findByName(t[1]));
            if (existing.isPresent()) {
                depIds.put(t[0], existing.get().getId());
                if (deps) {
                    skipped.add("Department " + t[1]);
                }
            } else if (deps) {
                depCreated.add(t[1]);
                if (!dry) {
                    depIds.put(t[0], departmentRepository.save(new Department(t[0], t[1], DepartmentCategory.valueOf(t[2]), null, order)).getId());
                }
            }
            order++;
        }
        if (dess) {
            for (String[] t : DESIGNATIONS) {
                if (designationRepository.findByCode(t[0]).or(() -> designationRepository.findByName(t[1])).isPresent()) {
                    skipped.add("Designation " + t[1]);
                    continue;
                }
                desCreated.add(t[1]);
                if (!dry) {
                    Designation d = new Designation(t[0], t[1], StaffType.valueOf(t[2]), t[3].isEmpty() ? null : t[3], Integer.parseInt(t[4]));
                    d.setDefaultDepartmentId(t[5].isEmpty() ? null : depIds.get(t[5]));
                    designationRepository.save(d);
                }
            }
        }
        log.info("Staff template {}: {} departments, {} designations created, {} existing kept",
                dry ? "(dry run)" : "applied", depCreated.size(), desCreated.size(), skipped.size());
        return new TemplateResult(dry, depCreated, desCreated, skipped);
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StaffOverview overview() {
        List<Staff> all = em.createQuery("SELECT s FROM Staff s JOIN FETCH s.tenantUser u LEFT JOIN FETCH u.profile WHERE s.deletedAt IS NULL", Staff.class)
                .getResultList();
        List<Staff> active = all.stream().filter(s -> s.getEmploymentStatus() == EmploymentStatus.ACTIVE).toList();
        Map<StaffType, Long> byType = new EnumMap<>(StaffType.class);
        active.forEach(s -> byType.merge(s.getStaffType(), 1L, Long::sum));
        Map<EmploymentStatus, Long> byStatus = new EnumMap<>(EmploymentStatus.class);
        all.forEach(s -> byStatus.merge(s.getEmploymentStatus(), 1L, Long::sum));

        Map<UUID, Long> depCounts = counts("department_id");
        Map<UUID, Long> desCounts = counts("designation_id");
        List<Count> departments = departmentRepository.findAllNotDeleted().stream()
                .map(d -> new Count(d.getId(), d.getName(), depCounts.getOrDefault(d.getId(), 0L))).toList();
        List<Count> designations = designationRepository.findAllNotDeleted().stream()
                .map(d -> new Count(d.getId(), d.getName(), desCounts.getOrDefault(d.getId(), 0L))).toList();

        List<Issue> issues = new ArrayList<>();
        issue(issues, "WARNING", "NO_DESIGNATION", active.stream().filter(s -> s.getDesignationId() == null).toList(),
                "active staff have no designation");
        issue(issues, "WARNING", "NO_DEPARTMENT", active.stream().filter(s -> s.getDepartmentId() == null).toList(),
                "active staff have no department");
        @SuppressWarnings("unchecked")
        Set<UUID> withSubjects = new HashSet<>(em.createNativeQuery("SELECT DISTINCT staff_id FROM staff_subject").getResultList());
        issue(issues, "WARNING", "TEACHER_WITHOUT_SUBJECTS", active.stream()
                        .filter(s -> s.getStaffType() == StaffType.TEACHER && !withSubjects.contains(s.getId())).toList(),
                "teachers have no subjects listed; subject-teacher suggestions cannot include them");
        issue(issues, "WARNING", "TEACHER_WITHOUT_LOGIN", active.stream()
                        .filter(s -> s.getStaffType() == StaffType.TEACHER && s.getTenantUser().getMasterUserId() == null).toList(),
                "teachers have no login; they cannot take attendance or enter marks");

        UUID schoolId = context.schoolId();
        Map<UUID, Account> accounts = accountService.schoolAccounts(schoolId).stream().collect(Collectors.toMap(Account::userId, a -> a));
        Set<UUID> linked = all.stream().map(s -> s.getTenantUser().getMasterUserId()).filter(Objects::nonNull).collect(Collectors.toSet());
        linked.addAll(context.linkedMasterUserIds());
        issue(issues, "ERROR", "LOGIN_ACTIVE_AFTER_EXIT", all.stream().filter(s -> s.getEmploymentStatus() != EmploymentStatus.ACTIVE
                        && s.getTenantUser().getMasterUserId() != null
                        && accounts.containsKey(s.getTenantUser().getMasterUserId())
                        && accounts.get(s.getTenantUser().getMasterUserId()).status() == UserStatus.ACTIVE).toList(),
                "people who are no longer active staff can still sign in; disable their logins");
        List<Account> unlinked = accounts.values().stream()
                .filter(a -> SchoolAccountService.STAFF_ROLES.contains(a.role()) && !linked.contains(a.userId())).toList();
        if (!unlinked.isEmpty()) {
            issues.add(new Issue("WARNING", "LOGIN_WITHOUT_STAFF", unlinked.size(),
                    unlinked.size() + " staff logins are not linked to a staff record; data-level access (own classes) will not work for them",
                    unlinked.stream().limit(5).map(Account::email).toList()));
        }

        Map<UUID, Staff> byId = all.stream().collect(Collectors.toMap(Staff::getId, s -> s));
        List<String> noHead = new ArrayList<>();
        for (Department d : departmentRepository.findAllNotDeleted()) {
            if (!d.isActive() || d.getCategory() != DepartmentCategory.ACADEMIC) {
                continue;
            }
            if (d.getHeadStaffId() == null) {
                if (depCounts.getOrDefault(d.getId(), 0L) > 0) {
                    noHead.add(d.getName());
                }
            } else if (!byId.containsKey(d.getHeadStaffId()) || byId.get(d.getHeadStaffId()).getEmploymentStatus() != EmploymentStatus.ACTIVE) {
                issues.add(new Issue("WARNING", "HEAD_NOT_ACTIVE", 1, "The head of " + d.getName() + " is no longer active staff", List.of(d.getName())));
            }
        }
        if (!noHead.isEmpty()) {
            issues.add(new Issue("INFO", "DEPARTMENT_WITHOUT_HEAD", noHead.size(), noHead.size() + " academic department(s) with staff have no head (HOD duty)",
                    noHead.stream().limit(5).toList()));
        }
        List<String> overloaded = overloadedTeachers(active);
        if (!overloaded.isEmpty()) {
            issues.add(new Issue("WARNING", "TEACHER_OVERLOADED", overloaded.size(),
                    overloaded.size() + " teachers have more periods in the active timetable than their weekly limit", overloaded.stream().limit(5).toList()));
        }
        long withLogin = active.stream().filter(s -> s.getTenantUser().getMasterUserId() != null).count();
        return new StaffOverview(active.size(), byType, byStatus, departments, designations, withLogin, unlinked.size(), issues);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Active-staff periods in ACTIVE timetables above max_periods_per_week → "Name (34/30)". */
    private List<String> overloadedTeachers(List<Staff> active) {
        Map<UUID, Long> periods = periodsPerWeek(em);
        List<String> out = new ArrayList<>();
        for (Staff s : active) {
            long p = periods.getOrDefault(s.getId(), 0L);
            if (s.getMaxPeriodsPerWeek() != null && p > s.getMaxPeriodsPerWeek()) {
                out.add(StaffNames.name(s) + " (" + p + "/" + s.getMaxPeriodsPerWeek() + ")");
            }
        }
        return out;
    }

    static Map<UUID, Long> periodsPerWeek(EntityManager em) {
        Map<UUID, Long> out = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT e.teacher_id, count(*) FROM timetable_entry e JOIN timetable t ON t.id = e.timetable_id
                        WHERE t.status = 'ACTIVE' AND t.deleted_at IS NULL AND e.deleted_at IS NULL AND e.teacher_id IS NOT NULL
                        GROUP BY e.teacher_id
                        """).getResultList();
        rows.forEach(r -> out.put((UUID) r[0], ((Number) r[1]).longValue()));
        return out;
    }

    private static void issue(List<Issue> issues, String level, String code, List<Staff> staff, String message) {
        if (!staff.isEmpty()) {
            issues.add(new Issue(level, code, staff.size(), staff.size() + " " + message, staff.stream().limit(5).map(StaffNames::name).toList()));
        }
    }

    /** Active staff per department_id / designation_id column. */
    private Map<UUID, Long> counts(String column) {
        Map<UUID, Long> out = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT " + column + ", count(*) FROM staff WHERE deleted_at IS NULL AND employment_status = 'ACTIVE' AND "
                + column + " IS NOT NULL GROUP BY " + column).getResultList();
        rows.forEach(r -> out.put((UUID) r[0], ((Number) r[1]).longValue()));
        return out;
    }

    private Map<UUID, String> staffNames() {
        Map<UUID, String> out = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT s.id, trim(coalesce(p.first_name, '') || ' ' || coalesce(p.last_name, ''))
                        FROM staff s JOIN tenant_user u ON u.id = s.tenant_user_id LEFT JOIN tenant_user_profile p ON p.user_id = u.id
                        WHERE s.id IN (SELECT head_staff_id FROM department WHERE head_staff_id IS NOT NULL)
                        """).getResultList();
        rows.forEach(r -> out.put((UUID) r[0], (String) r[1]));
        return out;
    }

    /** Keeps the legacy text column in step after a rename. */
    private void syncText(String textColumn, String idColumn, UUID id, String name) {
        em.createNativeQuery("UPDATE staff SET " + textColumn + " = :n WHERE " + idColumn + " = :id").setParameter("n", name).setParameter("id", id)
                .executeUpdate();
    }

    private UUID activeDepartment(UUID id) {
        if (id == null) {
            return null;
        }
        if (!department(id).isActive()) {
            throw new BusinessException("Department " + id + " is inactive");
        }
        return id;
    }

    private Department department(UUID id) {
        return departmentRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    private Designation designation(UUID id) {
        return designationRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("Designation", id));
    }

    private static String role(PlatformRole role) {
        if (role == null) {
            return null;
        }
        if (!SchoolAccountService.STAFF_ROLES.contains(role)) {
            throw new BusinessException("Default role must be one of " + SchoolAccountService.STAFF_ROLES);
        }
        return role.name();
    }

    private static String code(String c) {
        return c.trim().toUpperCase(Locale.ROOT);
    }

    private static DepartmentResponse toResponse(Department d, Map<UUID, Long> counts, Map<UUID, String> names) {
        return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getCategory(), d.getHeadStaffId(),
                d.getHeadStaffId() != null ? names.get(d.getHeadStaffId()) : null, d.getSortOrder(), d.getStatus(),
                counts.getOrDefault(d.getId(), 0L));
    }

    private static DesignationResponse toResponse(Designation d, Map<UUID, Long> counts) {
        return new DesignationResponse(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getStaffType(),
                d.getDefaultRole() != null ? PlatformRole.valueOf(d.getDefaultRole()) : null, d.getDefaultDepartmentId(), d.getRank(),
                d.getStatus(), counts.getOrDefault(d.getId(), 0L));
    }
}
