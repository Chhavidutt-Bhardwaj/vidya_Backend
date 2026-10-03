package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.PeriodType;

import java.time.LocalTime;
import java.util.UUID;

public record TeacherDaySlotResponse(
        UUID periodId,
        String periodCode,
        String periodName,
        int periodSequence,
        LocalTime startTime,
        LocalTime endTime,
        PeriodType periodType,
        UUID entryId,
        String entryCode,
        UUID academicClassId,
        String className,
        UUID sectionId,
        String sectionName,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        UUID roomId,
        String roomCode,
        String roomName,
        String slotType,
        TimetableGridSubstitutionResponse substitution
) {}
