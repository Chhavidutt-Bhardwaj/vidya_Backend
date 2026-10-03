package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.SubstitutionStatus;
import com.vidya.tenantdata.timetable.enums.SubstitutionType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SubstitutionResponse(
        UUID id,
        String substitutionCode,
        UUID timetableId,
        String timetableCode,
        LocalDate substitutionDate,
        UUID periodId,
        String periodName,
        Integer periodOrder,
        UUID academicClassId,
        String className,
        UUID sectionId,
        String sectionName,
        SubstitutionType substitutionType,
        UUID originalTeacherId,
        String originalTeacherName,
        UUID originalSubjectId,
        String originalSubjectName,
        UUID substituteTeacherId,
        String substituteTeacherName,
        UUID substituteRoomId,
        String substituteRoomName,
        String reason,
        SubstitutionStatus status,
        Boolean sameSubjectQualified,
        Instant createdAt,
        Instant updatedAt
) {}
