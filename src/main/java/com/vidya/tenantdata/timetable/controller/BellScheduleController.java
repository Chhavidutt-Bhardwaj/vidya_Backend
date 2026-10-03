package com.vidya.tenantdata.timetable.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/tenant/bell-schedules", "/api/v1/bell-schedules"})
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('BELL_SCHEDULE_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete bell schedule", description = "Soft deletes a bell schedule")
    public ResponseEntity<ApiResponse<Void>> deleteBellSchedule(@PathVariable UUID id) {
        log.info("REST: Delete bell schedule [{}]", id);
        bellScheduleService.deleteBellSchedule(id);
        return ResponseEntity.ok(ApiResponse.success("BELL_SCHEDULE_DELETED", "Bell schedule deleted successfully", null));
    }
}
