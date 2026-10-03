package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;

import java.time.LocalTime;
import java.util.UUID;

public record TimetableGridSlotResponse(
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
        UUID roomId,
        String roomCode,
        String roomName,
        UUID academicClassId,
        String className,
        UUID sectionId,
        String sectionName,
        TimetableEntryType entryType
) {}
