package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.calendar.enums.DayStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TeacherDayScheduleResponse(
        UUID timetableId,
        String timetableCode,
        String timetableName,
        UUID teacherId,
        String teacherCode,
        String teacherName,
        LocalDate date,
        DayOfWeek dayOfWeek,
        List<TeacherDaySlotResponse> slots,
        DayStatus dayStatus
) {
    public TeacherDayScheduleResponse(
            UUID timetableId,
            String timetableCode,
            String timetableName,
            UUID teacherId,
            String teacherCode,
            String teacherName,
            LocalDate date,
            DayOfWeek dayOfWeek,
            List<TeacherDaySlotResponse> slots
    ) {
        this(timetableId, timetableCode, timetableName, teacherId, teacherCode, teacherName, date, dayOfWeek, slots, null);
    }
}
