package com.vidya.tenantdata.attendance.dto;

import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Summary attendance response — used in list / paginated views.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Attendance record summary")
public class AttendanceResponse {

    @Schema(description = "Attendance record UUID")
    private UUID id;

    @Schema(description = "Stable business code (e.g. ATT-A1B2C3D4)", example = "ATT-A1B2C3D4")
    private String attendanceCode;

    @Schema(description = "Student UUID")
    private UUID studentId;

    @Schema(description = "Student business code", example = "STU-A1B2C3D4")
    private String studentCode;

    @Schema(description = "Student full name", example = "Aarav Sharma")
    private String studentName;

    @Schema(description = "Academic Year UUID")
    private UUID academicYearId;

    @Schema(description = "Academic Year code", example = "AY2026-27")
    private String academicYearCode;

    @Schema(description = "Academic Class UUID")
    private UUID academicClassId;

    @Schema(description = "Academic Class name", example = "Class 5")
    private String academicClassName;

    @Schema(description = "Section UUID")
    private UUID sectionId;

    @Schema(description = "Section name", example = "Section A")
    private String sectionName;

    @Schema(description = "Attendance date", example = "2026-07-15")
    private LocalDate attendanceDate;

    @Schema(description = "Attendance status")
    private AttendanceStatus status;

    @Schema(description = "Optional remarks")
    private String remarks;

    @Schema(description = "UUID of staff who marked this attendance")
    private UUID markedByStaffId;

    @Schema(description = "Timestamp when attendance was last marked")
    private Instant markedAt;

    @Schema(description = "Record creation timestamp")
    private Instant createdAt;

    @Schema(description = "Record last update timestamp")
    private Instant updatedAt;
}
