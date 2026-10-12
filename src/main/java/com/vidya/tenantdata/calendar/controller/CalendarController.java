package com.vidya.tenantdata.calendar.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigRequest;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigResponse;
import com.vidya.tenantdata.calendar.dto.WorkingDaysCountResponse;
import com.vidya.tenantdata.calendar.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
@Tag(name = "Academic Calendar", description = "Endpoints for academic calendar and working day configuration")
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('CALENDAR_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get working day config", description = "Retrieves the working day configuration for an academic year")
    public ResponseEntity<ApiResponse<WorkingDayConfigResponse>> getWorkingDayConfig(
            @RequestParam UUID academicYearId
    ) {
        WorkingDayConfigResponse response = calendarService.getWorkingDayConfig(academicYearId);
        return ResponseEntity.ok(ApiResponse.success("WORKING_DAY_CONFIG_RETRIEVED", "Working day config retrieved successfully", response));
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('CALENDAR_MANAGE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update working day config", description = "Creates or updates the working day configuration for an academic year")
    public ResponseEntity<ApiResponse<WorkingDayConfigResponse>> updateWorkingDayConfig(
            @RequestParam UUID academicYearId,
            @Valid @RequestBody WorkingDayConfigRequest request
    ) {
        WorkingDayConfigResponse response = calendarService.updateWorkingDayConfig(academicYearId, request);
        return ResponseEntity.ok(ApiResponse.success("WORKING_DAY_CONFIG_UPDATED", "Working day config updated successfully", response));
    }

    @GetMapping("/days")
    @PreAuthorize("hasAuthority('CALENDAR_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get per-date calendar status", description = "Retrieves per-date status for dates in a specified range")
    public ResponseEntity<ApiResponse<List<DayStatusResponse>>> getDaysStatus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<DayStatusResponse> response = calendarService.getDaysStatus(from, to);
        return ResponseEntity.ok(ApiResponse.success("CALENDAR_DAYS_RETRIEVED", "Calendar days status retrieved successfully", response));
    }

    @GetMapping("/working-days/count")
    @PreAuthorize("hasAuthority('CALENDAR_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Count working days in range", description = "Returns the count of working days in a specified date range")
    public ResponseEntity<ApiResponse<WorkingDaysCountResponse>> countWorkingDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        long count = calendarService.countWorkingDays(from, to);
        return ResponseEntity.ok(ApiResponse.success(
                "WORKING_DAYS_COUNTED",
                "Working days count calculated successfully",
                new WorkingDaysCountResponse(from, to, count)
        ));
    }
}
