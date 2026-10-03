package com.vidya.tenantdata.timetable.dto;

import java.time.DayOfWeek;
import java.util.List;

public record TimetableDayGridResponse(
        DayOfWeek dayOfWeek,
        List<TimetableGridSlotResponse> slots
) {}
