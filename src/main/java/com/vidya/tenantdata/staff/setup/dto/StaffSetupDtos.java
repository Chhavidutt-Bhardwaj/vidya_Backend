package com.vidya.tenantdata.staff.setup.dto;

import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyRequest;
import com.vidya.tenantdata.user.account.dto.AccountDtos.LoginInfo;
import com.vidya.security.entity.PlatformRole;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.enums.DepartmentCategory;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.EmploymentType;
import com.vidya.tenantdata.staff.enums.OrgUnitStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Requests and responses of the staff setup API (departments, designations, onboarding, access, offboarding). */
public final class StaffSetupDtos {

    private StaffSetupDtos() {
    }

    // ── Departments & designations ───────────────────────────────────────────

    public record DepartmentRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "code: letters, digits, _ and - only") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 255) String description,
            DepartmentCategory category,
            UUID headStaffId,
            Integer sortOrder) {
    }

    public record DepartmentResponse(UUID id, String code, String name, String description, DepartmentCategory category,
                                     UUID headStaffId, String headName, int sortOrder, OrgUnitStatus status, long staff) {
    }

    /** @param defaultRole login role for this designation (school staff roles only); null = no login by default */
    public record DesignationRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "code: letters, digits, _ and - only") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 255) String description,
            @NotNull StaffType staffType,
            PlatformRole defaultRole,
            UUID defaultDepartmentId,
            @Min(0) @Max(1000) Integer rank) {
    }

    public record DesignationResponse(UUID id, String code, String name, String description, StaffType staffType, PlatformRole defaultRole,
                                      UUID defaultDepartmentId, int rank, OrgUnitStatus status, long staff) {
    }

    public record StatusRequest(@NotNull OrgUnitStatus status) {
    }

    /** Standard Indian-school departments and designations; existing ones (by code or name) are kept. */
    public record TemplateRequest(Boolean departments, Boolean designations, Boolean dryRun) {
    }

    public record TemplateResult(boolean dryRun, List<String> departmentsCreated, List<String> designationsCreated, List<String> skipped) {
    }

    // ── Onboarding ───────────────────────────────────────────────────────────

    /** @param fromGradeLevel / toGradeLevel class range (-3 Pre-Nursery … 12), null = any class */
    public record SubjectSkill(@NotNull UUID subjectId, @Min(-3) @Max(12) Integer fromGradeLevel, @Min(-3) @Max(12) Integer toGradeLevel,
                               Boolean primary) {
    }

    /**
     * @param create      default: true when an email is given and the designation has a default role
     * @param role        default: the designation's role, else TEACHER for teachers and SCHOOL_STAFF for others
     * @param extraRoleIds additional roles (global or this school's custom roles), e.g. Exam Coordinator
     */
    public record LoginRequest(Boolean create, PlatformRole role, List<UUID> extraRoleIds) {
    }

    /**
     * One person joining the school: personal details, staff record, subjects, duties (HOD, coordinator …) and login in one call.
     * staffType and departmentId default from the designation.
     */
    public record OnboardStaffRequest(
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Email @Size(max = 255) String email,
            @Size(max = 20) String phone,
            @Size(max = 20) String gender,
            LocalDate dateOfBirth,
            @Size(max = 100) String employeeCode,
            LocalDate joiningDate,
            StaffType staffType,
            UUID designationId,
            UUID departmentId,
            UUID reportingToStaffId,
            EmploymentType employmentType,
            @Size(max = 255) String qualification,
            @DecimalMin("0") @DecimalMax("60") BigDecimal experienceYears,
            @Min(0) @Max(80) Integer maxPeriodsPerWeek,
            List<@Valid SubjectSkill> subjects,
            List<@Valid DutyRequest> duties,
            @Valid LoginRequest login) {
    }

    public record OnboardResult(StaffDetailResponse staff, LoginInfo login, List<String> warnings) {
    }

    public record BulkOnboardRequest(@NotEmpty @Size(max = 500) List<@Valid OnboardStaffRequest> staff, Boolean dryRun) {
    }

    public record BulkRow(int index, String name, String email, String error, String staffCode, LoginInfo login) {
    }

    /** All or nothing: when {@code valid} is false nothing was saved (HTTP 422). */
    public record BulkOnboardResult(boolean dryRun, boolean valid, int created, int loginsCreated, List<BulkRow> rows) {
    }

    // ── Offboarding ──────────────────────────────────────────────────────────

    /**
     * @param status          RESIGNED, TERMINATED, RETIRED, SUSPENDED or INACTIVE
     * @param revokeLogin     disable the login and sign out everywhere (default true; SUSPENDED/INACTIVE too)
     * @param endAssignments  complete class-teacher and subject-teacher assignments on lastWorkingDate (default false: only reported)
     */
    public record OffboardRequest(@NotNull EmploymentStatus status, LocalDate lastWorkingDate, Boolean revokeLogin, Boolean endAssignments,
                                  @Size(max = 500) String reason) {
    }

    public record OffboardResult(EmploymentStatus status, LocalDate lastWorkingDate, boolean loginDisabled, int sessionsEnded,
                                 long classTeacherAssignments, long subjectAssignments, long activeTimetableSlots, boolean assignmentsEnded,
                                 List<String> departmentsHeaded, List<String> dutiesEnded, long reportees, List<String> nextSteps) {
    }

    // ── Subjects & suggestions ───────────────────────────────────────────────

    public record StaffSubjectsRequest(@NotNull List<@Valid SubjectSkill> subjects) {
    }

    public record SubjectSkillResponse(UUID subjectId, String subjectCode, String subjectName, Integer fromGradeLevel, Integer toGradeLevel,
                                       boolean primary) {
    }

    /**
     * @param periodsPerWeek slots in active timetables
     * @param overloaded     periodsPerWeek ≥ maxPeriodsPerWeek
     */
    public record TeacherSuggestion(UUID staffId, String staffCode, String name, String designation, boolean primarySubject,
                                    long subjectAssignments, long periodsPerWeek, Integer maxPeriodsPerWeek, boolean overloaded) {
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    public record Count(UUID id, String name, long count) {
    }

    public record Issue(String level, String code, long count, String message, List<String> examples) {
    }

    public record StaffOverview(long activeStaff, Map<StaffType, Long> byType, Map<EmploymentStatus, Long> byStatus,
                                List<Count> departments, List<Count> designations, long withLogin, long loginsNotLinked,
                                List<Issue> issues) {
    }
}
