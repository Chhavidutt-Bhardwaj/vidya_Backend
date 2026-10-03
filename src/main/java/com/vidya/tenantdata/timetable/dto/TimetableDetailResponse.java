package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.TimetableStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TimetableDetailResponse(
        UUID id,
        String timetableCode,
        UUID academicYearId,
        String academicYearCode,
        String academicYearName,
        BellScheduleResponse bellSchedule,
        String name,
        String description,
        TimetableStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<TimetableEntryResponse> entries,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {}
