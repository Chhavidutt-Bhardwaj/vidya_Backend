package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateBellScheduleRequest(
        @NotBlank(message = "Schedule name is required")
        @Size(max = 150, message = "Schedule name cannot exceed 150 characters")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        boolean isDefault,

        @NotNull(message = "Status is required")
        BellScheduleStatus status,

        @Valid
        List<CreatePeriodRequest> periods
) {}
