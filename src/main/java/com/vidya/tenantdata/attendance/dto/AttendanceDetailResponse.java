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
 * Detailed attendance response — used for single-record GET, create, and update responses.
 * Includes full audit fields and optimistic locking version.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed representation of a student attendance record")
public class AttendanceDetailResponse {

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

    @Schema(description = "Academic Year name", example = "2026-2027")
    private String academicYearName;

    @Schema(description = "Academic Class UUID")
    private UUID academicClassId;

    @Schema(description = "Academic Class code", example = "CLS-5")
    private String academicClassCode;

    @Schema(description = "Academic Class name", example = "Class 5")
    private String academicClassName;

    @Schema(description = "Section UUID")
    private UUID sectionId;

    @Schema(description = "Section code", example = "SEC-5A")
    private String sectionCode;

    @Schema(description = "Section name", example = "Section A")
    private String sectionName;

    @Schema(description = "Attendance date", example = "2026-07-15")
    private LocalDate attendanceDate;

    @Schema(description = "Attendance status")
    private AttendanceStatus status;

    @Schema(description = "Optional remarks or correction note")
    private String remarks;

    @Schema(description = "UUID of staff who marked / last corrected this attendance")
    private UUID markedByStaffId;

    @Schema(description = "Staff code of the marker", example = "STF-A1B2C3D4")
    private String markedByStaffCode;

    @Schema(description = "Full name of the staff member who marked attendance")
    private String markedByStaffName;

    @Schema(description = "Timestamp when attendance was last marked or corrected")
    private Instant markedAt;

    @Schema(description = "Record creation timestamp")
    private Instant createdAt;

    @Schema(description = "Record last update timestamp")
    private Instant updatedAt;

    @Schema(description = "User who created the record")
    private UUID createdBy;

    @Schema(description = "User who last modified the record")
    private UUID updatedBy;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
