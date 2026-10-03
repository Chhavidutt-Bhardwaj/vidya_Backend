package com.vidya.tenantdata.attendance.dto;

import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Single student entry within the daily section attendance view.
 * Contains enough data for the frontend to render a daily attendance sheet row.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single student's attendance entry in a daily section attendance sheet")
public class DailyAttendanceEntryResponse {

    @Schema(description = "Attendance record UUID (null if not yet marked)")
    private UUID attendanceId;

    @Schema(description = "Stable attendance code (null if not yet marked)")
    private String attendanceCode;

    @Schema(description = "Student UUID")
    private UUID studentId;

    @Schema(description = "Student business code", example = "STU-A1B2C3D4")
    private String studentCode;

    @Schema(description = "Student full name", example = "Aarav Sharma")
    private String studentName;

    @Schema(description = "Student roll number in this section")
    private String rollNumber;

    @Schema(description = "Attendance date")
    private LocalDate attendanceDate;

    @Schema(description = "Attendance status (null if not yet marked)")
    private AttendanceStatus status;

    @Schema(description = "Remarks")
    private String remarks;
}
