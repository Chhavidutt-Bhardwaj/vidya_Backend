package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateBellScheduleStatusRequest(
        @NotNull(message = "Status is required")
        BellScheduleStatus status
) {}
