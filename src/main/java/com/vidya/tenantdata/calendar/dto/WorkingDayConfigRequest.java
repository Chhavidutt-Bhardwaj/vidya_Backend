package com.vidya.tenantdata.calendar.dto;

import com.vidya.tenantdata.calendar.enums.SaturdayPattern;
import jakarta.validation.constraints.NotNull;

public record WorkingDayConfigRequest(
        @NotNull(message = "monday is required")
        Boolean monday,

        @NotNull(message = "tuesday is required")
        Boolean tuesday,

        @NotNull(message = "wednesday is required")
        Boolean wednesday,

        @NotNull(message = "thursday is required")
        Boolean thursday,

        @NotNull(message = "friday is required")
        Boolean friday,

        @NotNull(message = "saturday is required")
        Boolean saturday,

        @NotNull(message = "sunday is required")
        Boolean sunday,

        @NotNull(message = "saturdayPattern is required")
        SaturdayPattern saturdayPattern
) {}
