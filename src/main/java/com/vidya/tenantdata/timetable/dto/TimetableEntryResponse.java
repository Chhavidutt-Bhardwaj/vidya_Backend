package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record TimetableEntryResponse(
        UUID id,
        String entryCode,
        UUID timetableId,
        UUID academicYearId,
        UUID periodId,
        String periodCode,
        String periodName,
        int periodSequence,
        LocalTime startTime,
        LocalTime endTime,
        PeriodType periodType,
        DayOfWeek dayOfWeek,
        UUID academicClassId,
        String classCode,
        String className,
        UUID sectionId,
        String sectionCode,
        String sectionName,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        UUID teacherAssignmentId,
        UUID teacherId,
        String teacherCode,
        String teacherName,
        UUID classSubjectAssignmentId,
        UUID roomId,
        String roomCode,
        String roomName,
        TimetableEntryType entryType,
        String warning,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public TimetableEntryResponse(
            UUID id,
            String entryCode,
            UUID timetableId,
            UUID academicYearId,
            UUID periodId,
            String periodCode,
            String periodName,
            int periodSequence,
            LocalTime startTime,
            LocalTime endTime,
            PeriodType periodType,
            DayOfWeek dayOfWeek,
            UUID academicClassId,
            String classCode,
            String className,
            UUID sectionId,
            String sectionCode,
            String sectionName,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            UUID teacherAssignmentId,
            UUID teacherId,
            String teacherCode,
            String teacherName,
            UUID classSubjectAssignmentId,
            UUID roomId,
            String roomCode,
            String roomName,
            TimetableEntryType entryType,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        this(id, entryCode, timetableId, academicYearId, periodId, periodCode, periodName, periodSequence,
             startTime, endTime, periodType, dayOfWeek, academicClassId, classCode, className,
             sectionId, sectionCode, sectionName, subjectId, subjectCode, subjectName,
             teacherAssignmentId, teacherId, teacherCode, teacherName, classSubjectAssignmentId,
             roomId, roomCode, roomName, entryType, null, createdAt, updatedAt, version);
    }
}
