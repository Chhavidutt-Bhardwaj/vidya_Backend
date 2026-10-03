package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.TimetableStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimetableResponse(
        UUID id,
        String timetableCode,
        UUID academicYearId,
        String academicYearCode,
        String academicYearName,
        UUID bellScheduleId,
        String bellScheduleCode,
        String bellScheduleName,
        String name,
        String description,
        TimetableStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        int totalEntriesCount,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {}
