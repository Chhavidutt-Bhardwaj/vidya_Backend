package com.vidya.tenantdata.calendar.dto;

import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.enums.HolidayType;

import java.time.DayOfWeek;
import java.time.LocalDate;

public record DayStatusResponse(
        LocalDate date,
        DayOfWeek dayOfWeek,
        DayStatus status,
        String holidayName,
        HolidayType holidayType
) {
    public DayStatusResponse(LocalDate date, DayOfWeek dayOfWeek, DayStatus status) {
        this(date, dayOfWeek, status, null, null);
    }
}
