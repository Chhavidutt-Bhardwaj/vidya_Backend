package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.SubstitutionType;

import java.time.LocalDate;
import java.util.UUID;

public record TimetableGridSubstitutionResponse(
        UUID id,
        String code,
        SubstitutionType type,
        LocalDate date,
        UUID substituteTeacherId,
        String substituteTeacherName,
        UUID substituteRoomId,
        String substituteRoomName,
        String reason
) {}
