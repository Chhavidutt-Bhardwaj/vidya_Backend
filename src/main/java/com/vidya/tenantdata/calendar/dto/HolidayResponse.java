package com.vidya.tenantdata.calendar.dto;

import com.vidya.tenantdata.calendar.enums.HolidayType;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(
        UUID id,
        String holidayCode,
        UUID academicYearId,
        String academicYearName,
        String name,
        HolidayType holidayType,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {}
