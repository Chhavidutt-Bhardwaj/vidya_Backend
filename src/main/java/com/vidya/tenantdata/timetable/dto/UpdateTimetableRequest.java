package com.vidya.tenantdata.timetable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateTimetableRequest(
        @NotBlank(message = "Timetable name is required")
        @Size(max = 150, message = "Timetable name cannot exceed 150 characters")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @NotNull(message = "Bell schedule ID is required")
        UUID bellScheduleId,

        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
