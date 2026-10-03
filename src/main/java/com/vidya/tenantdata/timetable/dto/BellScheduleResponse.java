package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BellScheduleResponse(
        UUID id,
        String scheduleCode,
        String name,
        String description,
        boolean isDefault,
        BellScheduleStatus status,
        List<PeriodResponse> periods,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {}
