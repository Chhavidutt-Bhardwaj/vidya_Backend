package com.vidya.tenantdata.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Daily section attendance view — wraps all student attendance entries for one date.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Daily attendance sheet for a section on a specific date")
public class DailySectionAttendanceResponse {

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

    @Schema(description = "Total students in the section")
    private int totalStudents;

    @Schema(description = "Number of marked attendance records on this date")
    private int markedCount;

    @Schema(description = "Individual student attendance entries")
    private List<DailyAttendanceEntryResponse> entries;
}
