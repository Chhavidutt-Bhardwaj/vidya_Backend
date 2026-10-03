package com.vidya.tenantdata.timetable.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SectionDayScheduleResponse(
        UUID timetableId,
        String timetableCode,
        String timetableName,
        UUID academicClassId,
        String className,
        UUID sectionId,
        String sectionName,
        LocalDate date,
        DayOfWeek dayOfWeek,
        List<SectionDaySlotResponse> slots
) {}
