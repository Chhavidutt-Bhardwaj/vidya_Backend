package com.vidya.tenantdata.timetable.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBellScheduleRequest(
        @NotBlank(message = "Schedule name is required")
        @Size(max = 150, message = "Schedule name cannot exceed 150 characters")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        boolean isDefault,

        @NotEmpty(message = "Bell schedule must contain at least one period")
        @Valid
        List<CreatePeriodRequest> periods
) {}
