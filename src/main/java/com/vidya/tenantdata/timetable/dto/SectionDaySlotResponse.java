package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.PeriodType;

import java.time.LocalTime;
import java.util.UUID;

public record SectionDaySlotResponse(
        UUID periodId,
        String periodCode,
        String periodName,
        int periodSequence,
        LocalTime startTime,
        LocalTime endTime,
        PeriodType periodType,
        UUID entryId,
        String entryCode,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        UUID teacherId,
        String teacherName,
        UUID originalTeacherId,
        String originalTeacherName,
        UUID roomId,
        String roomCode,
        String roomName,
        UUID originalRoomId,
        String originalRoomName,
        String status,
        boolean isCancelled,
        TimetableGridSubstitutionResponse substitution
) {}
