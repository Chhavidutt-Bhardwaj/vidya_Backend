package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.calendar.enums.DayStatus;

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
        List<SectionDaySlotResponse> slots,
        DayStatus dayStatus
) {
    public SectionDayScheduleResponse(
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
    ) {
        this(timetableId, timetableCode, timetableName, academicClassId, className, sectionId, sectionName, date, dayOfWeek, slots, null);
    }
}
