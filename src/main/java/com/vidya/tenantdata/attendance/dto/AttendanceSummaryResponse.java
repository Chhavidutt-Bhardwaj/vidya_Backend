package com.vidya.tenantdata.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Attendance summary for a student in an academic year.
 * Calculated on-the-fly from attendance records — no summary table required.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Attendance summary statistics for a student in an academic year")
public class AttendanceSummaryResponse {

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

    @Schema(description = "Total attendance records (marked days)")
    private long totalDays;

    @Schema(description = "Days present", example = "120")
    private long presentCount;

    @Schema(description = "Days absent", example = "5")
    private long absentCount;

    @Schema(description = "Days late", example = "3")
    private long lateCount;

    @Schema(description = "Half-day count", example = "2")
    private long halfDayCount;

    @Schema(description = "Excused absences", example = "1")
    private long excusedCount;

    @Schema(description = "Attendance percentage (0.00 – 100.00)", example = "95.45")
    private BigDecimal attendancePercentage;

    @Schema(description = "Count of working days in range for the academic year", example = "180")
    private long workingDaysInRange;

    @Schema(description = "Count of working days with no attendance record for the student", example = "5")
    private long unmarkedWorkingDays;

    @Schema(description = "Percentage of working days attended (present-weighted / workingDaysInRange * 100)", example = "92.50")
    private BigDecimal percentageOfWorkingDays;

    @Schema(description = "Count of records on dates that are now non-working days without override", example = "1")
    private long excludedNonWorkingDayRecords;
}
