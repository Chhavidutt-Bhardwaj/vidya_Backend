package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.SubstitutionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSubstitutionRequest(
        @NotNull(message = "timetableId is required")
        UUID timetableId,

        @NotNull(message = "substitutionDate is required")
        LocalDate substitutionDate,

        @NotNull(message = "periodId is required")
        UUID periodId,

        @NotNull(message = "academicClassId is required")
        UUID academicClassId,

        UUID sectionId,

        @NotNull(message = "substitutionType is required")
        SubstitutionType substitutionType,

        UUID substituteTeacherId,

        UUID substituteRoomId,

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason
) {}
