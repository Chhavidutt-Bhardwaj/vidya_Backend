package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.PeriodType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record CreatePeriodRequest(
        @NotBlank(message = "Period name is required")
        @Size(max = 50, message = "Period name cannot exceed 50 characters")
        String name,

        @NotNull(message = "Period sequence is required")
        @Min(value = 1, message = "Period sequence must be greater than zero")
        Integer periodSequence,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @NotNull(message = "Period type is required")
        PeriodType periodType
) {}
