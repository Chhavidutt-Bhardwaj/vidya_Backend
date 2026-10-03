package com.vidya.tenantdata.timetable.dto;

import java.util.List;
import java.util.UUID;

public record TimetableGridResponse(
        UUID timetableId,
        String timetableCode,
        String timetableName,
        String filterType,
        UUID filterId,
        String filterName,
        List<TimetableDayGridResponse> days
) {}
