package com.vidya.tenantdata.calendar.dto;

import com.vidya.tenantdata.calendar.enums.SaturdayPattern;

import java.util.UUID;

public record WorkingDayConfigResponse(
        UUID id,
        UUID academicYearId,
        String academicYearName,
        boolean monday,
        boolean tuesday,
        boolean wednesday,
        boolean thursday,
        boolean friday,
        boolean saturday,
        boolean sunday,
        SaturdayPattern saturdayPattern,
        boolean isDefault
) {}
