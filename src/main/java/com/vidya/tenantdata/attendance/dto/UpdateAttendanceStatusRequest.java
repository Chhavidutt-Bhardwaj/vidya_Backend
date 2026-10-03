package com.vidya.tenantdata.attendance.dto;

import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for PATCH status-only update on an attendance record.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update only the status of an attendance record")
public class UpdateAttendanceStatusRequest {

    @NotNull(message = "status is required")
    @Schema(description = "New attendance status", allowableValues = {"PRESENT","ABSENT","LATE","HALF_DAY","EXCUSED"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private AttendanceStatus status;
}
