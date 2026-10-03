package com.vidya.tenantdata.timetable.dto;

import java.util.List;

public record ScheduleValidationResponse(
        boolean valid,
        int totalConflicts,
        List<ScheduleConflictResponse> conflicts,
        int checkedEntriesCount
) {}
