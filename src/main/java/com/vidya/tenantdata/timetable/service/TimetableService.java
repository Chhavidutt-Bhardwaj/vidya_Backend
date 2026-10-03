package com.vidya.tenantdata.timetable.service;

import com.vidya.tenantdata.timetable.dto.BulkUpsertEntriesResponse;
import com.vidya.tenantdata.timetable.dto.BulkUpsertSectionEntriesRequest;
import com.vidya.tenantdata.timetable.dto.CloneTimetableRequest;
import com.vidya.tenantdata.timetable.dto.CloneTimetableResponse;
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
import com.vidya.tenantdata.timetable.dto.DayRoomOccupancyResponse;
import com.vidya.tenantdata.timetable.dto.DayTeacherLoadResponse;
import com.vidya.tenantdata.timetable.dto.RoomOccupancyResponse;
import com.vidya.tenantdata.timetable.dto.TeacherLoadResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface TimetableService {
    // Timetable master
    TimetableResponse createTimetable(CreateTimetableRequest request);
    TimetableResponse updateTimetable(UUID id, UpdateTimetableRequest request);
    TimetableResponse updateTimetableStatus(UUID id, UpdateTimetableStatusRequest request);
    TimetableDetailResponse getTimetableById(UUID id);
    TimetableResponse getTimetableByCode(String timetableCode);
    Page<TimetableResponse> searchTimetables(UUID academicYearId, UUID bellScheduleId, TimetableStatus status, String search, Pageable pageable);
    void deleteTimetable(UUID id);
    ScheduleValidationResponse validateTimetable(UUID id);
    TimetableResponse publishTimetable(UUID id);

    // Timetable entries
    TimetableEntryResponse createEntry(UUID timetableId, CreateTimetableEntryRequest request);
    TimetableEntryResponse updateEntry(UUID timetableId, UUID entryId, UpdateTimetableEntryRequest request);
    TimetableEntryResponse getEntryById(UUID timetableId, UUID entryId);
    Page<TimetableEntryResponse> searchEntries(UUID timetableId, UUID academicClassId, UUID sectionId, UUID teacherId, UUID subjectId, UUID roomId, DayOfWeek dayOfWeek, UUID periodId, Pageable pageable);
    default Page<TimetableEntryResponse> searchEntries(UUID timetableId, UUID academicClassId, UUID sectionId, UUID teacherId, UUID subjectId, UUID roomId, DayOfWeek dayOfWeek, Pageable pageable) {
        return searchEntries(timetableId, academicClassId, sectionId, teacherId, subjectId, roomId, dayOfWeek, null, pageable);
    }
    void deleteEntry(UUID timetableId, UUID entryId);

    // Lifecycle operations
    CloneTimetableResponse cloneTimetable(UUID id, CloneTimetableRequest request);

    // Bulk operations
    BulkUpsertEntriesResponse bulkUpsertSectionEntries(UUID timetableId, UUID sectionId, BulkUpsertSectionEntriesRequest request);

    // Grid queries
    TimetableGridResponse getClassTimetableGrid(UUID timetableId, UUID academicClassId, UUID sectionId);
    TimetableGridResponse getClassTimetableGrid(UUID timetableId, UUID academicClassId, UUID sectionId, List<DayOfWeek> days);
    TimetableGridResponse getSectionTimetableGrid(UUID timetableId, UUID sectionId, List<DayOfWeek> days);
    TimetableGridResponse getSectionTimetableGrid(UUID timetableId, UUID sectionId, List<DayOfWeek> days, java.time.LocalDate weekOf);
    default TimetableGridResponse getSectionTimetableGrid(UUID timetableId, UUID sectionId) {
        return getSectionTimetableGrid(timetableId, sectionId, null, null);
    }
    TimetableGridResponse getTeacherTimetableGrid(UUID timetableId, UUID teacherId);
    TimetableGridResponse getTeacherTimetableGrid(UUID timetableId, UUID teacherId, List<DayOfWeek> days);
    TimetableGridResponse getTeacherTimetableGrid(UUID timetableId, UUID teacherId, List<DayOfWeek> days, java.time.LocalDate weekOf);
    TimetableGridResponse getRoomTimetableGrid(UUID timetableId, UUID roomId);
    TimetableGridResponse getRoomTimetableGrid(UUID timetableId, UUID roomId, List<DayOfWeek> days);

    // Day views
    com.vidya.tenantdata.timetable.dto.SectionDayScheduleResponse getSectionDaySchedule(UUID timetableId, UUID sectionId, java.time.LocalDate date);
    com.vidya.tenantdata.timetable.dto.TeacherDayScheduleResponse getTeacherDaySchedule(UUID timetableId, UUID teacherId, java.time.LocalDate date);

    // Read analytics / occupancy & load queries
    RoomOccupancyResponse getRoomOccupancy(UUID timetableId, UUID roomId, List<DayOfWeek> days);
    default RoomOccupancyResponse getRoomOccupancy(UUID timetableId, UUID roomId) {
        return getRoomOccupancy(timetableId, roomId, null);
    }
    TeacherLoadResponse getTeacherLoad(UUID timetableId, UUID teacherId, List<DayOfWeek> days);
    default TeacherLoadResponse getTeacherLoad(UUID timetableId, UUID teacherId) {
        return getTeacherLoad(timetableId, teacherId, null);
    }
    TimetableGridResponse getActiveSectionTimetable(UUID academicYearId, UUID sectionId, List<DayOfWeek> days);
    default TimetableGridResponse getActiveSectionTimetable(UUID academicYearId, UUID sectionId) {
        return getActiveSectionTimetable(academicYearId, sectionId, null);
    }
}
