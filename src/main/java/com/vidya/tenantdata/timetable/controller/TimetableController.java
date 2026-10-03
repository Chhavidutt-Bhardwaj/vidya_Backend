package com.vidya.tenantdata.timetable.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
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
import com.vidya.tenantdata.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/timetables")
@RequiredArgsConstructor
@Tag(name = "Timetable & Scheduling", description = "Timetable creation, entries, validation, publish, and grid views for the active school tenant")
public class TimetableController {

    private final TimetableService timetableService;

    // --- Master Timetable Endpoints ---

    @PostMapping
    @PreAuthorize("hasAuthority('TIMETABLE_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create timetable", description = "Creates a new master timetable in DRAFT status for an academic year")
    public ResponseEntity<ApiResponse<TimetableResponse>> createTimetable(
            @Valid @RequestBody CreateTimetableRequest request
    ) {
        log.info("REST: Create timetable [{}]", request.name());
        TimetableResponse response = timetableService.createTimetable(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("TIMETABLE_CREATED", "Timetable created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search timetables", description = "Searches timetables with optional academic year, bell schedule, and status filters")
    public ResponseEntity<PagedApiResponse<TimetableResponse>> searchTimetables(
            @Parameter(description = "Filter by academic year ID")
            @RequestParam(required = false) UUID academicYearId,
            @Parameter(description = "Filter by bell schedule ID")
            @RequestParam(required = false) UUID bellScheduleId,
            @Parameter(description = "Filter by timetable status")
            @RequestParam(required = false) TimetableStatus status,
            @Parameter(description = "Search term by name or code")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<TimetableResponse> page = timetableService.searchTimetables(academicYearId, bellScheduleId, status, search, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("TIMETABLES_RETRIEVED", "Timetables retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get timetable details by ID", description = "Fetches a master timetable including all its scheduled entries")
    public ResponseEntity<ApiResponse<TimetableDetailResponse>> getTimetableById(@PathVariable UUID id) {
        TimetableDetailResponse response = timetableService.getTimetableById(id);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_RETRIEVED", "Timetable retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TIMETABLE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update timetable", description = "Updates timetable basic metadata and bell schedule")
    public ResponseEntity<ApiResponse<TimetableResponse>> updateTimetable(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTimetableRequest request
    ) {
        log.info("REST: Update timetable [{}]", id);
        TimetableResponse response = timetableService.updateTimetable(id, request);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_UPDATED", "Timetable updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('TIMETABLE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update timetable status", description = "Transitions timetable status through its lifecycle (e.g. DRAFT -> PUBLISHED -> ACTIVE -> ARCHIVED)")
    public ResponseEntity<ApiResponse<TimetableResponse>> updateTimetableStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTimetableStatusRequest request
    ) {
        log.info("REST: Update timetable status [{}] to [{}]", id, request.status());
        TimetableResponse response = timetableService.updateTimetableStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_STATUS_UPDATED", "Timetable status updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TIMETABLE_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete timetable", description = "Soft deletes a timetable and all its entries")
    public ResponseEntity<ApiResponse<Void>> deleteTimetable(@PathVariable UUID id) {
        log.info("REST: Delete timetable [{}]", id);
        timetableService.deleteTimetable(id);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_DELETED", "Timetable deleted successfully", null));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('TIMETABLE_VALIDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Validate timetable", description = "Executes complete conflict detection across teachers, classes, rooms, and periods")
    public ResponseEntity<ApiResponse<ScheduleValidationResponse>> validateTimetable(@PathVariable UUID id) {
        log.info("REST: Validate timetable [{}]", id);
        ScheduleValidationResponse response = timetableService.validateTimetable(id);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_VALIDATED", "Timetable validation completed", response));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('TIMETABLE_PUBLISH') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Publish timetable", description = "Validates the timetable and transitions it from DRAFT to PUBLISHED state")
    public ResponseEntity<ApiResponse<TimetableResponse>> publishTimetable(@PathVariable UUID id) {
        log.info("REST: Publish timetable [{}]", id);
        TimetableResponse response = timetableService.publishTimetable(id);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_PUBLISHED", "Timetable published successfully", response));
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAuthority('TIMETABLE_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Clone timetable", description = "Clones an existing timetable and its scheduled entries into a new draft timetable")
    public ResponseEntity<ApiResponse<CloneTimetableResponse>> cloneTimetable(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CloneTimetableRequest request
    ) {
        log.info("REST: Clone timetable [{}]", id);
        CloneTimetableResponse response = timetableService.cloneTimetable(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("TIMETABLE_CLONED", "Timetable cloned successfully", response));
    }

    // --- Timetable Entry Endpoints ---

    @PutMapping("/{timetableId}/sections/{sectionId}/entries")
    @PreAuthorize("hasAuthority('TIMETABLE_ENTRY_UPDATE') or hasAuthority('TIMETABLE_ENTRY_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Bulk upsert section timetable entries", description = "Atomically bulk upserts timetable slot entries for a class section in a draft timetable")
    public ResponseEntity<ApiResponse<BulkUpsertEntriesResponse>> bulkUpsertSectionEntries(
            @PathVariable UUID timetableId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody BulkUpsertSectionEntriesRequest request
    ) {
        log.info("REST: Bulk upsert section timetable entries in timetable [{}] for section [{}]", timetableId, sectionId);
        BulkUpsertEntriesResponse response = timetableService.bulkUpsertSectionEntries(timetableId, sectionId, request);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_ENTRIES_BULK_UPSERTED", "Timetable section entries bulk upserted successfully", response));
    }

    @PostMapping("/{timetableId}/entries")
    @PreAuthorize("hasAuthority('TIMETABLE_ENTRY_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create timetable entry", description = "Adds a scheduled class/subject/teacher/room slot to a timetable with real-time conflict checking")
    public ResponseEntity<ApiResponse<TimetableEntryResponse>> createEntry(
            @PathVariable UUID timetableId,
            @Valid @RequestBody CreateTimetableEntryRequest request
    ) {
        log.info("REST: Create timetable entry in timetable [{}]", timetableId);
        TimetableEntryResponse response = timetableService.createEntry(timetableId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("TIMETABLE_ENTRY_CREATED", "Timetable entry created successfully", response));
    }

    @GetMapping("/{timetableId}/entries")
    @PreAuthorize("hasAuthority('TIMETABLE_ENTRY_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search timetable entries", description = "Searches entries within a timetable with class, teacher, room, and day filters")
    public ResponseEntity<PagedApiResponse<TimetableEntryResponse>> searchEntries(
            @PathVariable UUID timetableId,
            @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) DayOfWeek dayOfWeek,
            @RequestParam(required = false) UUID periodId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<TimetableEntryResponse> page = timetableService.searchEntries(timetableId, academicClassId, sectionId, teacherId, subjectId, roomId, dayOfWeek, periodId, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("TIMETABLE_ENTRIES_RETRIEVED", "Timetable entries retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{timetableId}/entries/{id}")
    @PreAuthorize("hasAuthority('TIMETABLE_ENTRY_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get timetable entry by ID", description = "Fetches a specific timetable slot entry by ID")
    public ResponseEntity<ApiResponse<TimetableEntryResponse>> getEntryById(
            @PathVariable UUID timetableId,
            @PathVariable UUID id
    ) {
        TimetableEntryResponse response = timetableService.getEntryById(timetableId, id);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_ENTRY_RETRIEVED", "Timetable entry retrieved successfully", response));
    }

    @PutMapping("/{timetableId}/entries/{id}")
    @PreAuthorize("hasAuthority('TIMETABLE_ENTRY_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update timetable entry", description = "Updates a timetable slot entry with conflict re-validation")
    public ResponseEntity<ApiResponse<TimetableEntryResponse>> updateEntry(
            @PathVariable UUID timetableId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTimetableEntryRequest request
    ) {
        log.info("REST: Update timetable entry [{}]", id);
        TimetableEntryResponse response = timetableService.updateEntry(timetableId, id, request);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_ENTRY_UPDATED", "Timetable entry updated successfully", response));
    }

    @DeleteMapping("/{timetableId}/entries/{id}")
    @PreAuthorize("hasAuthority('TIMETABLE_ENTRY_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete timetable entry", description = "Soft deletes a timetable slot entry")
    public ResponseEntity<ApiResponse<Void>> deleteEntry(
            @PathVariable UUID timetableId,
            @PathVariable UUID id
    ) {
        log.info("REST: Delete timetable entry [{}]", id);
        timetableService.deleteEntry(timetableId, id);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_ENTRY_DELETED", "Timetable entry deleted successfully", null));
    }

    // --- Timetable Grid / Schedule & Occupancy Views ---

    @GetMapping("/{timetableId}/class/{academicClassId}")
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get class weekly timetable grid", description = "Produces a day-by-period matrix view of the weekly schedule for a class and optional section")
    public ResponseEntity<ApiResponse<TimetableGridResponse>> getClassTimetableGrid(
            @PathVariable UUID timetableId,
            @PathVariable UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) java.util.List<DayOfWeek> days
    ) {
        TimetableGridResponse response = timetableService.getClassTimetableGrid(timetableId, academicClassId, sectionId, days);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_GRID_RETRIEVED", "Class timetable grid retrieved successfully", response));
    }

    @GetMapping("/{timetableId}/sections/{sectionId}/grid")
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get section weekly timetable grid", description = "Produces a day-by-period matrix view of the weekly schedule for a specific section, with optional weekOf substitution overlay")
    public ResponseEntity<ApiResponse<TimetableGridResponse>> getSectionTimetableGrid(
            @PathVariable UUID timetableId,
            @PathVariable UUID sectionId,
            @RequestParam(required = false) java.util.List<DayOfWeek> days,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate weekOf
    ) {
        TimetableGridResponse response = timetableService.getSectionTimetableGrid(timetableId, sectionId, days, weekOf);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_GRID_RETRIEVED", "Section timetable grid retrieved successfully", response));
    }

    @GetMapping({"/{timetableId}/teachers/{teacherId}/grid", "/{timetableId}/teacher/{teacherId}"})
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get teacher weekly timetable grid", description = "Produces a day-by-period matrix view of the weekly teaching schedule for a teacher, with optional weekOf substitution overlay")
    public ResponseEntity<ApiResponse<TimetableGridResponse>> getTeacherTimetableGrid(
            @PathVariable UUID timetableId,
            @PathVariable UUID teacherId,
            @RequestParam(required = false) java.util.List<DayOfWeek> days,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate weekOf
    ) {
        TimetableGridResponse response = timetableService.getTeacherTimetableGrid(timetableId, teacherId, days, weekOf);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_GRID_RETRIEVED", "Teacher timetable grid retrieved successfully", response));
    }

    @GetMapping("/{timetableId}/sections/{sectionId}/day")
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get section day timetable schedule", description = "Produces an effective day schedule for a section on a specific date, overlaying active substitutions")
    public ResponseEntity<ApiResponse<com.vidya.tenantdata.timetable.dto.SectionDayScheduleResponse>> getSectionDaySchedule(
            @PathVariable UUID timetableId,
            @PathVariable UUID sectionId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date
    ) {
        com.vidya.tenantdata.timetable.dto.SectionDayScheduleResponse response = timetableService.getSectionDaySchedule(timetableId, sectionId, date);
        return ResponseEntity.ok(ApiResponse.success("SECTION_DAY_SCHEDULE_RETRIEVED", "Section day schedule retrieved successfully", response));
    }

    @GetMapping("/{timetableId}/teachers/{teacherId}/day")
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get teacher day timetable schedule", description = "Produces an effective day schedule for a teacher on a specific date, marking covered slots and extra substituted periods")
    public ResponseEntity<ApiResponse<com.vidya.tenantdata.timetable.dto.TeacherDayScheduleResponse>> getTeacherDaySchedule(
            @PathVariable UUID timetableId,
            @PathVariable UUID teacherId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date
    ) {
        com.vidya.tenantdata.timetable.dto.TeacherDayScheduleResponse response = timetableService.getTeacherDaySchedule(timetableId, teacherId, date);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_DAY_SCHEDULE_RETRIEVED", "Teacher day schedule retrieved successfully", response));
    }

    @GetMapping({"/{timetableId}/rooms/{roomId}/grid", "/{timetableId}/room/{roomId}"})
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get room weekly timetable grid", description = "Produces a day-by-period matrix view of the weekly occupancy schedule for a room")
    public ResponseEntity<ApiResponse<TimetableGridResponse>> getRoomTimetableGrid(
            @PathVariable UUID timetableId,
            @PathVariable UUID roomId,
            @RequestParam(required = false) java.util.List<DayOfWeek> days
    ) {
        TimetableGridResponse response = timetableService.getRoomTimetableGrid(timetableId, roomId, days);
        return ResponseEntity.ok(ApiResponse.success("TIMETABLE_GRID_RETRIEVED", "Room timetable grid retrieved successfully", response));
    }

    @GetMapping("/{timetableId}/rooms/{roomId}/occupancy")
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get room occupancy and utilisation", description = "Calculates occupied periods and utilisation percentage for a room across selected days")
    public ResponseEntity<ApiResponse<com.vidya.tenantdata.timetable.dto.RoomOccupancyResponse>> getRoomOccupancy(
            @PathVariable UUID timetableId,
            @PathVariable UUID roomId,
            @RequestParam(required = false) java.util.List<DayOfWeek> days
    ) {
        com.vidya.tenantdata.timetable.dto.RoomOccupancyResponse response = timetableService.getRoomOccupancy(timetableId, roomId, days);
        return ResponseEntity.ok(ApiResponse.success("ROOM_OCCUPANCY_RETRIEVED", "Room occupancy retrieved successfully", response));
    }

    @GetMapping("/{timetableId}/teachers/{teacherId}/load")
    @PreAuthorize("hasAuthority('TIMETABLE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get teacher workload and free periods", description = "Calculates assigned teaching periods and free teaching slots per day and per week for a teacher")
    public ResponseEntity<ApiResponse<com.vidya.tenantdata.timetable.dto.TeacherLoadResponse>> getTeacherLoad(
            @PathVariable UUID timetableId,
            @PathVariable UUID teacherId,
            @RequestParam(required = false) java.util.List<DayOfWeek> days
    ) {
        com.vidya.tenantdata.timetable.dto.TeacherLoadResponse response = timetableService.getTeacherLoad(timetableId, teacherId, days);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_LOAD_RETRIEVED", "Teacher load retrieved successfully", response));
    }
}
