package com.vidya.tenantdata.timetable.service;

import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.CreateTimetableRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.dto.TimetableDetailResponse;
import com.vidya.tenantdata.timetable.dto.TimetableEntryResponse;
import com.vidya.tenantdata.timetable.dto.TimetableGridResponse;
import com.vidya.tenantdata.timetable.dto.TimetableResponse;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableRequest;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.util.UUID;

public interface TimetableService {
    // Timetable master
    TimetableResponse createTimetable(CreateTimetableRequest request);
    TimetableResponse updateTimetable(UUID id, UpdateTimetableRequest request);
    TimetableResponse updateTimetableStatus(UUID id, UpdateTimetableStatusRequest request);
    TimetableDetailResponse getTimetableById(UUID id);
    TimetableResponse getTimetableByCode(String timetableCode);
    Page<TimetableResponse> searchTimetables(UUID academicYearId, TimetableStatus status, String search, Pageable pageable);
    void deleteTimetable(UUID id);
    ScheduleValidationResponse validateTimetable(UUID id);
    TimetableResponse publishTimetable(UUID id);

    // Timetable entries
    TimetableEntryResponse createEntry(UUID timetableId, CreateTimetableEntryRequest request);
    TimetableEntryResponse updateEntry(UUID timetableId, UUID entryId, UpdateTimetableEntryRequest request);
    TimetableEntryResponse getEntryById(UUID timetableId, UUID entryId);
    Page<TimetableEntryResponse> searchEntries(UUID timetableId, UUID academicClassId, UUID sectionId, UUID teacherId, UUID subjectId, UUID roomId, DayOfWeek dayOfWeek, Pageable pageable);
    void deleteEntry(UUID timetableId, UUID entryId);

    // Grid queries
    TimetableGridResponse getClassTimetableGrid(UUID timetableId, UUID academicClassId, UUID sectionId);
    TimetableGridResponse getTeacherTimetableGrid(UUID timetableId, UUID teacherId);
    TimetableGridResponse getRoomTimetableGrid(UUID timetableId, UUID roomId);
}
