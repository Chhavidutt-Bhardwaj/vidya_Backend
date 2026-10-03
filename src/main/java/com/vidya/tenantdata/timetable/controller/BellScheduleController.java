package com.vidya.tenantdata.timetable.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.CreatePeriodRequest;
import com.vidya.tenantdata.timetable.dto.PeriodResponse;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleStatusRequest;
import com.vidya.tenantdata.timetable.dto.UpdatePeriodRequest;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import com.vidya.tenantdata.timetable.service.BellScheduleService;
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

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/bell-schedules")
@RequiredArgsConstructor
@Tag(name = "Bell Schedule Management", description = "Bell schedule and period timing configuration for the active school tenant")
public class BellScheduleController {

    private final BellScheduleService bellScheduleService;

    @PostMapping
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create bell schedule", description = "Creates a new bell schedule with periods")
    public ResponseEntity<ApiResponse<BellScheduleResponse>> createBellSchedule(
            @Valid @RequestBody CreateBellScheduleRequest request
    ) {
        log.info("REST: Create bell schedule [{}]", request.name());
        BellScheduleResponse response = bellScheduleService.createBellSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("BELL_SCHEDULE_CREATED", "Bell schedule created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search bell schedules", description = "Searches bell schedules with pagination")
    public ResponseEntity<PagedApiResponse<BellScheduleResponse>> searchBellSchedules(
            @Parameter(description = "Search by schedule name or code")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) BellScheduleStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<BellScheduleResponse> page = bellScheduleService.searchBellSchedules(search, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("BELL_SCHEDULES_RETRIEVED", "Bell schedules retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/default")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get default bell schedule", description = "Fetches the active default bell schedule for the school")
    public ResponseEntity<ApiResponse<BellScheduleResponse>> getDefaultSchedule() {
        BellScheduleResponse response = bellScheduleService.getDefaultSchedule();
        return ResponseEntity.ok(ApiResponse.success("BELL_SCHEDULE_RETRIEVED", "Default bell schedule retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get bell schedule by ID", description = "Fetches a bell schedule with all its period slots")
    public ResponseEntity<ApiResponse<BellScheduleResponse>> getBellScheduleById(@PathVariable UUID id) {
        BellScheduleResponse response = bellScheduleService.getBellScheduleById(id);
        return ResponseEntity.ok(ApiResponse.success("BELL_SCHEDULE_RETRIEVED", "Bell schedule retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update bell schedule", description = "Updates an existing bell schedule and its period configuration")
    public ResponseEntity<ApiResponse<BellScheduleResponse>> updateBellSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBellScheduleRequest request
    ) {
        log.info("REST: Update bell schedule [{}]", id);
        BellScheduleResponse response = bellScheduleService.updateBellSchedule(id, request);
        return ResponseEntity.ok(ApiResponse.success("BELL_SCHEDULE_UPDATED", "Bell schedule updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update bell schedule status", description = "Updates the active/inactive status of a bell schedule")
    public ResponseEntity<ApiResponse<BellScheduleResponse>> updateBellScheduleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBellScheduleStatusRequest request
    ) {
        log.info("REST: Update bell schedule status [{}] -> [{}]", id, request.status());
        BellScheduleResponse response = bellScheduleService.updateBellScheduleStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("BELL_SCHEDULE_STATUS_UPDATED", "Bell schedule status updated successfully", response));
    }

    @PostMapping("/{id}/set-default")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Set default bell schedule", description = "Sets the specified bell schedule as the school default")
    public ResponseEntity<ApiResponse<BellScheduleResponse>> setDefaultSchedule(@PathVariable UUID id) {
        log.info("REST: Set default bell schedule [{}]", id);
        BellScheduleResponse response = bellScheduleService.setDefaultSchedule(id);
        return ResponseEntity.ok(ApiResponse.success("BELL_SCHEDULE_SET_DEFAULT", "Bell schedule set as default successfully", response));
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Clone bell schedule", description = "Duplicates an existing bell schedule and all its period slots")
    public ResponseEntity<ApiResponse<BellScheduleResponse>> cloneSchedule(@PathVariable UUID id) {
        log.info("REST: Clone bell schedule [{}]", id);
        BellScheduleResponse response = bellScheduleService.cloneSchedule(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("BELL_SCHEDULE_CLONED", "Bell schedule cloned successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete bell schedule", description = "Soft deletes a bell schedule")
    public ResponseEntity<ApiResponse<Void>> deleteBellSchedule(@PathVariable UUID id) {
        log.info("REST: Delete bell schedule [{}]", id);
        bellScheduleService.deleteBellSchedule(id);
        return ResponseEntity.ok(ApiResponse.success("BELL_SCHEDULE_DELETED", "Bell schedule deleted successfully", null));
    }

    // ── Period Endpoints ──────────────────────────────────────────────────────

    @GetMapping("/{id}/periods")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get periods for schedule", description = "Lists all periods belonging to a bell schedule ordered by sequence")
    public ResponseEntity<ApiResponse<List<PeriodResponse>>> getPeriodsByScheduleId(@PathVariable UUID id) {
        List<PeriodResponse> periods = bellScheduleService.getPeriodsByScheduleId(id);
        return ResponseEntity.ok(ApiResponse.success("PERIODS_RETRIEVED", "Periods retrieved successfully", periods));
    }

    @PostMapping("/{id}/periods")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Add period to schedule", description = "Adds a single period slot to an existing bell schedule")
    public ResponseEntity<ApiResponse<PeriodResponse>> addPeriod(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePeriodRequest request
    ) {
        log.info("REST: Add period [{}] to schedule [{}]", request.name(), id);
        PeriodResponse response = bellScheduleService.addPeriod(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("PERIOD_ADDED", "Period added successfully", response));
    }

    @PutMapping("/{id}/periods/{periodId}")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update period", description = "Updates a period's details or timing")
    public ResponseEntity<ApiResponse<PeriodResponse>> updatePeriod(
            @PathVariable UUID id,
            @PathVariable UUID periodId,
            @Valid @RequestBody UpdatePeriodRequest request
    ) {
        log.info("REST: Update period [{}] in schedule [{}]", periodId, id);
        PeriodResponse response = bellScheduleService.updatePeriod(id, periodId, request);
        return ResponseEntity.ok(ApiResponse.success("PERIOD_UPDATED", "Period updated successfully", response));
    }

    @DeleteMapping("/{id}/periods/{periodId}")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete period", description = "Soft deletes a period slot from a bell schedule")
    public ResponseEntity<ApiResponse<Void>> deletePeriod(
            @PathVariable UUID id,
            @PathVariable UUID periodId
    ) {
        log.info("REST: Delete period [{}] from schedule [{}]", periodId, id);
        bellScheduleService.deletePeriod(id, periodId);
        return ResponseEntity.ok(ApiResponse.success("PERIOD_DELETED", "Period deleted successfully", null));
    }

    @PutMapping("/{id}/periods")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Bulk replace periods", description = "Replaces all period slots for a schedule")
    public ResponseEntity<ApiResponse<List<PeriodResponse>>> bulkReplacePeriods(
            @PathVariable UUID id,
            @Valid @RequestBody List<CreatePeriodRequest> periods
    ) {
        log.info("REST: Bulk replace periods for schedule [{}]", id);
        List<PeriodResponse> response = bellScheduleService.bulkReplacePeriods(id, periods);
        return ResponseEntity.ok(ApiResponse.success("PERIODS_BULK_REPLACED", "Periods replaced successfully", response));
    }
}
