package com.vidya.tenantdata.timetable.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateSubstitutionRequest(
        UUID substituteTeacherId,
        UUID substituteRoomId,

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason
) {}
