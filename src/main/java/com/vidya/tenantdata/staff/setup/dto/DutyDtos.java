package com.vidya.tenantdata.staff.setup.dto;

import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.staff.enums.DutyCategory;
import com.vidya.tenantdata.staff.enums.DutyScope;
import com.vidya.tenantdata.staff.enums.DutyStatus;
import com.vidya.tenantdata.staff.enums.OrgUnitStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Duties (responsibilities) of staff: HOD, coordinators, in-charges. A person can hold many at once. */
public final class DutyDtos {

    private DutyDtos() {
    }

    /**
     * @param accessRoleCode role code (global such as COORDINATOR, or one of this school's roles) granted to the holder
     *                       while the duty is active; null for duties without system access
     */
    public record DutyTypeRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$") @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 255) String description,
            DutyCategory category,
            DutyScope scopeKind,
            Boolean singleHolder,
            @Size(max = 50) String accessRoleCode) {
    }

    public record DutyTypeResponse(UUID id, String code, String name, String description, DutyCategory category, DutyScope scopeKind,
                                   boolean singleHolder, String accessRoleCode, boolean system, OrgUnitStatus status, long activeHolders) {
    }

    /**
     * Give a staff member a duty. Exactly the scope fields the duty type needs must be set
     * (department for HOD, stage for a coordinator, subject for a subject in-charge …).
     *
     * @param dutyTypeId   or {@code dutyTypeCode}
     * @param academicYearId optional; the year the duty is for (defaults to open-ended)
     * @param startDate    default today
     * @param replace      for single-holder duties: end the current holder's duty today instead of failing
     */
    public record DutyRequest(
            UUID dutyTypeId,
            @Size(max = 40) String dutyTypeCode,
            UUID departmentId,
            ClassStage classStage,
            UUID academicClassId,
            UUID sectionId,
            UUID subjectId,
            UUID academicYearId,
            LocalDate startDate,
            @Size(max = 255) String notes,
            Boolean replace) {
    }

    public record EndDutyRequest(LocalDate endDate) {
    }

    /** @param roleGranted the duty's access role is on the holder's login (false when the holder has no login yet) */
    public record DutyResponse(UUID id, UUID staffId, String staffName, UUID dutyTypeId, String dutyCode, String dutyName, DutyCategory category,
                               DutyScope scopeKind, String scopeLabel, UUID departmentId, ClassStage classStage, UUID academicClassId, UUID sectionId,
                               UUID subjectId, UUID academicYearId, LocalDate startDate, LocalDate endDate, DutyStatus status, String notes,
                               String accessRoleCode, boolean roleGranted) {
    }

    /** A class-teacher or subject-teacher assignment, shown next to duties. */
    public record TeachingAssignment(UUID assignmentId, String kind, String label, UUID academicYearId, UUID academicClassId, UUID sectionId,
                                     UUID subjectId, LocalDate startDate) {
    }

    /** Everything one person is responsible for. */
    public record StaffDutiesResponse(UUID staffId, String staffCode, String name, String designation, List<DutyResponse> duties,
                                      List<TeachingAssignment> classTeacherOf, List<TeachingAssignment> teaches) {
    }

    /** Who holds a duty right now. */
    public record DutyGroup(DutyTypeResponse dutyType, List<DutyResponse> holders) {
    }

    public record BulkDutyRequest(@NotNull UUID staffId, @NotNull DutyRequest duty) {
    }
}
