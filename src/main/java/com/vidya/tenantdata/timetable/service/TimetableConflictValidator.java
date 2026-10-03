package com.vidya.tenantdata.timetable.service;

import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.entity.Timetable;

import java.util.UUID;

public interface TimetableConflictValidator {
    ScheduleValidationResponse validateEntry(UUID timetableId, CreateTimetableEntryRequest request, UUID excludeEntryId);
    ScheduleValidationResponse validateTimetable(Timetable timetable);
    ScheduleValidationResponse validateTimetable(UUID timetableId);
}
