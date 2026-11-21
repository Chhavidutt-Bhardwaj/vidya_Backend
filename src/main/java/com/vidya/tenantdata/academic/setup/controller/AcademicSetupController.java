package com.vidya.tenantdata.academic.setup.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.academic.setup.dto.AcademicYearSetupRequest;
import com.vidya.tenantdata.academic.setup.dto.AcademicYearSetupResponse;
import com.vidya.tenantdata.academic.setup.dto.PromotionRequest;
import com.vidya.tenantdata.academic.setup.dto.PromotionResult;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse;
import com.vidya.tenantdata.academic.setup.service.AcademicSetupService;
import com.vidya.tenantdata.academic.setup.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Academic year planning for the school dashboard: one-call setup, the setup checklist, and
 * year-end promotion into the next year.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/academic-years")
@RequiredArgsConstructor
@Tag(name = "Academic Year Setup", description = "Set up a new academic year, track setup progress, and promote students")
public class AcademicSetupController {

    private final AcademicSetupService setupService;
    private final PromotionService promotionService;

    @PostMapping("/setup")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_CREATE')")
    @Operation(summary = "Create an academic year in one step",
            description = "Creates the year, its terms and working days, and optionally copies sections, curricula, class subjects, "
                    + "terms, working days and teacher assignments from an earlier year. All or nothing.")
    public ResponseEntity<ApiResponse<AcademicYearSetupResponse>> setUp(@Valid @RequestBody AcademicYearSetupRequest request) {
        log.info("REST: Set up academic year [{}]", request.code());
        AcademicYearSetupResponse response = setupService.setUpYear(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ACADEMIC_YEAR_SET_UP", "Academic year created and set up", response));
    }

    @GetMapping("/{id}/setup-status")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_READ')")
    @Operation(summary = "Setup checklist of an academic year",
            description = "Ordered steps (classes, sections, subjects, teachers, terms, calendar, timetable, students) with done/pending counts")
    public ResponseEntity<ApiResponse<SetupStatusResponse>> status(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_SETUP_STATUS", "Setup status retrieved", setupService.status(id)));
    }

    @PostMapping("/{id}/promotion/preview")
    @PreAuthorize("hasAuthority('STUDENT_PROMOTE') and @dataAccess.hasFullScope(authentication, 'STUDENT')")
    @Operation(summary = "Preview year-end promotion",
            description = "Shows what promoting this year's students into the target year would do. Nothing is saved.")
    public ResponseEntity<ApiResponse<PromotionResult>> preview(@PathVariable UUID id, @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PROMOTION_PREVIEW", "Promotion preview", promotionService.preview(id, request)));
    }

    @PostMapping("/{id}/promotion")
    @PreAuthorize("hasAuthority('STUDENT_PROMOTE') and @dataAccess.hasFullScope(authentication, 'STUDENT')")
    @Operation(summary = "Promote students into the next academic year",
            description = "Ends this year's enrollments (PROMOTED / COMPLETED / WITHDRAWN) and enrolls students in the target year. "
                    + "One transaction; safe to re-run (students already enrolled in the target year are skipped).")
    public ResponseEntity<ApiResponse<PromotionResult>> promote(@PathVariable UUID id, @Valid @RequestBody PromotionRequest request) {
        log.info("REST: Promote students of academic year [{}] into [{}]", id, request.targetAcademicYearId());
        return ResponseEntity.ok(ApiResponse.success("STUDENTS_PROMOTED", "Students promoted", promotionService.promote(id, request)));
    }
}
