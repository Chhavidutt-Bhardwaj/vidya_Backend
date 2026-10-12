package com.vidya.tenantdata.calendar.dto;

import com.vidya.tenantdata.calendar.enums.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateHolidayRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must not exceed 100 characters")
        String name,

        @NotNull(message = "holidayType is required")
        HolidayType holidayType,

        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description
) {}
