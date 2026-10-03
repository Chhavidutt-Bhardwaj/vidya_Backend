package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.ConflictSeverity;
import com.vidya.tenantdata.timetable.enums.ScheduleConflictType;

import java.time.DayOfWeek;
import java.util.UUID;

public record ScheduleConflictResponse(
        ScheduleConflictType type,
        ConflictSeverity severity,
        String message,
        UUID entryId,
        UUID conflictingEntryId,
        UUID periodId,
        DayOfWeek dayOfWeek,
        UUID resourceId
) {}
