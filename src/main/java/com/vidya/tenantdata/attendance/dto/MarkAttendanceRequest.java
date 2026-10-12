package com.vidya.tenantdata.attendance.dto;

import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for marking a single student's attendance.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to mark attendance for a single student")
public class MarkAttendanceRequest {

    @NotNull(message = "studentId is required")
    @Schema(description = "UUID of the student", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID studentId;

    @NotNull(message = "academicYearId is required")
    @Schema(description = "UUID of the academic year", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID academicYearId;

    @NotNull(message = "academicClassId is required")
    @Schema(description = "UUID of the academic class", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID academicClassId;

    @NotNull(message = "sectionId is required")
    @Schema(description = "UUID of the section", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID sectionId;

    @NotNull(message = "attendanceDate is required")
    @Schema(description = "The calendar date for attendance (YYYY-MM-DD)", example = "2026-07-15",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate attendanceDate;

    @NotNull(message = "status is required")
    @Schema(description = "Attendance status", allowableValues = {"PRESENT","ABSENT","LATE","HALF_DAY","EXCUSED"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private AttendanceStatus status;

    @Schema(description = "Optional remarks or reason for the status")
    private String remarks;

    @Schema(description = "Optional flag to allow marking attendance on non-working days or holidays", defaultValue = "false")
    private Boolean allowNonWorkingDay;

    @Schema(description = "Reason for overriding non-working day attendance (required when allowNonWorkingDay is true)")
    private String overrideReason;

    public MarkAttendanceRequest(UUID studentId, UUID academicYearId, UUID academicClassId, UUID sectionId,
                                 LocalDate attendanceDate, AttendanceStatus status, String remarks) {
        this(studentId, academicYearId, academicClassId, sectionId, attendanceDate, status, remarks, false, null);
    }
}
