package com.vidya.tenantdata.calendar.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.calendar.dto.CreateHolidayRequest;
import com.vidya.tenantdata.calendar.dto.HolidayResponse;
import com.vidya.tenantdata.calendar.dto.UpdateHolidayRequest;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.service.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
@Tag(name = "School Holidays", description = "Endpoints for managing school holidays and academic breaks")
public class HolidayController {

    private final HolidayService holidayService;

    @PostMapping
    @PreAuthorize("hasAuthority('CALENDAR_MANAGE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create holiday", description = "Creates a new school holiday or break for an academic year")
    public ResponseEntity<ApiResponse<HolidayResponse>> createHoliday(@Valid @RequestBody CreateHolidayRequest request) {
        HolidayResponse response = holidayService.createHoliday(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("HOLIDAY_CREATED", "Holiday created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CALENDAR_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get holiday by ID", description = "Retrieves a holiday record by ID")
    public ResponseEntity<ApiResponse<HolidayResponse>> getHolidayById(@PathVariable UUID id) {
        HolidayResponse response = holidayService.getHolidayById(id);
        return ResponseEntity.ok(ApiResponse.success("HOLIDAY_RETRIEVED", "Holiday retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CALENDAR_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search holidays", description = "Search holidays with filters for academic year, type, and date range")
    public ResponseEntity<PagedApiResponse<HolidayResponse>> searchHolidays(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) HolidayType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<HolidayResponse> page = holidayService.searchHolidays(academicYearId, type, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("HOLIDAYS_RETRIEVED", "Holidays retrieved successfully", page.getContent(), page));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CALENDAR_MANAGE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update holiday", description = "Updates an existing holiday record")
    public ResponseEntity<ApiResponse<HolidayResponse>> updateHoliday(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHolidayRequest request
    ) {
        HolidayResponse response = holidayService.updateHoliday(id, request);
        return ResponseEntity.ok(ApiResponse.success("HOLIDAY_UPDATED", "Holiday updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CALENDAR_MANAGE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete holiday", description = "Soft deletes a holiday record")
    public ResponseEntity<ApiResponse<Void>> deleteHoliday(@PathVariable UUID id) {
        holidayService.deleteHoliday(id);
        return ResponseEntity.ok(ApiResponse.success("HOLIDAY_DELETED", "Holiday deleted successfully", null));
    }
}
