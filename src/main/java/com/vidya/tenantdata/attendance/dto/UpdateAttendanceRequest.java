package com.vidya.tenantdata.attendance.dto;

import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for updating attendance status and remarks on an existing record.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update an attendance record (status correction)")
public class UpdateAttendanceRequest {

    @NotNull(message = "status is required")
    @Schema(description = "New attendance status", allowableValues = {"PRESENT","ABSENT","LATE","HALF_DAY","EXCUSED"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private AttendanceStatus status;

    @Schema(description = "Optional correction remarks")
    private String remarks;
}
