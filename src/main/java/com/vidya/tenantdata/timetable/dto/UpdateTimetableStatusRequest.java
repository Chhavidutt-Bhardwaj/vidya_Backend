package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTimetableStatusRequest(
        @NotNull(message = "Timetable status is required")
        TimetableStatus status
) {}
