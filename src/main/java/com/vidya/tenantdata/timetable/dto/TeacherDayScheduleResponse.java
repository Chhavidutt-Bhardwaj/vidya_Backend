package com.vidya.tenantdata.timetable.dto;

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
        List<TeacherDaySlotResponse> slots
) {}
