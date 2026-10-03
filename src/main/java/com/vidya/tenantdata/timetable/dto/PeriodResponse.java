package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.PeriodType;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record PeriodResponse(
        UUID id,
        String periodCode,
        String name,
        int periodSequence,
        LocalTime startTime,
        LocalTime endTime,
        PeriodType periodType,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {}
