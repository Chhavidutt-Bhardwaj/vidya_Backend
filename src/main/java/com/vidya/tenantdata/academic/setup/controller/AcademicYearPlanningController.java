package com.vidya.tenantdata.academic.setup.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanRequest;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanResponse;
import com.vidya.tenantdata.academic.setup.dto.HolidaySuggestionsResponse;
import com.vidya.tenantdata.academic.setup.dto.TimetableCarryOverRequest;
import com.vidya.tenantdata.academic.setup.dto.TimetableCarryOverResponse;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.BulkResult;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.BulkSectionsRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.ClassTeachersRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.FromCurriculumRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.StartYearRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.StartYearResponse;
import com.vidya.tenantdata.academic.setup.service.TimetableCarryOverService;
import com.vidya.tenantdata.academic.setup.service.YearHolidayService;
import com.vidya.tenantdata.academic.setup.service.YearSetupActionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Completes an academic year's setup from the dashboard: holidays, timetable carry-over, bulk
 * sections / class subjects / class teachers, and starting the year.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/academic-years/{id}")
@RequiredArgsConstructor
@Tag(name = "Academic Year Setup", description = "Set up a new academic year, track setup progress, and promote students")
public class AcademicYearPlanningController {

    private final YearHolidayService holidayService;
    private final TimetableCarryOverService timetableCarryOverService;
    private final YearSetupActionService actions;

    // ---- holidays --------------------------------------------------------------------------

    @GetMapping("/holidays/suggestions")
    @PreAuthorize("hasAuthority('CALENDAR_READ')")
    @Operation(summary = "Suggested holidays for the year",
            description = "National fixed-date holidays, festivals and state holidays from the configured holiday provider "
                    + "(for the school's state, or ?region=MH), and last year's holidays moved to this year. Nothing is saved.")
    public ResponseEntity<ApiResponse<HolidaySuggestionsResponse>> holidaySuggestions(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID fromAcademicYearId,
            @RequestParam(required = false) String region) {
        return ResponseEntity.ok(ApiResponse.success("HOLIDAY_SUGGESTIONS", "Holiday suggestions",
                holidayService.suggestions(id, fromAcademicYearId, region)));
    }

    @PostMapping("/holidays/bulk")
    @PreAuthorize("hasAuthority('CALENDAR_MANAGE')")
    @Operation(summary = "Add the year's holidays in one call",
            description = "Every row is validated (inside the year, no overlaps). If any row is invalid nothing is saved and "
                    + "the response is 422 with the reason per row. dryRun=true only validates.")
    public ResponseEntity<ApiResponse<HolidayPlanResponse>> bulkHolidays(@PathVariable UUID id, @Valid @RequestBody HolidayPlanRequest request) {
        HolidayPlanResponse r = holidayService.plan(id, request);
        if (!r.valid()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(ApiResponse.success("HOLIDAYS_INVALID", "Some holidays are invalid; nothing was saved", r));
        }
        return ResponseEntity.status(r.dryRun() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(r.dryRun() ? "HOLIDAYS_VALID" : "HOLIDAYS_CREATED",
                        r.dryRun() ? "All holidays are valid" : r.created() + " holiday(s) added", r));
    }

    // ---- timetable -------------------------------------------------------------------------

    @PostMapping("/timetable/carry-over")
    @PreAuthorize("hasAuthority('TIMETABLE_CREATE') and @dataAccess.hasFullScope(authentication, 'TIMETABLE')")
    @Operation(summary = "Start this year's timetable from last year's",
            description = "Copies a previous year's timetable as a DRAFT, mapped to this year's sections, class subjects and "
                    + "subject teachers. Then validate and publish it.")
    public ResponseEntity<ApiResponse<TimetableCarryOverResponse>> carryOverTimetable(
            @PathVariable UUID id, @Valid @RequestBody TimetableCarryOverRequest request) {
        log.info("REST: Carry timetable [{}] into academic year [{}]", request.sourceTimetableId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("TIMETABLE_CARRIED_OVER",
                "Timetable copied as draft", timetableCarryOverService.carryOver(id, request)));
    }

    // ---- bulk setup ------------------------------------------------------------------------

    @PostMapping("/sections/bulk")
    @PreAuthorize("hasAuthority('SECTION_CREATE')")
    @Operation(summary = "Create the same sections for many classes", description = "e.g. A and B for every class; existing ones are skipped")
    public ResponseEntity<ApiResponse<BulkResult>> bulkSections(@PathVariable UUID id, @Valid @RequestBody BulkSectionsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SECTIONS_CREATED", "Sections created", actions.bulkSections(id, request)));
    }

    @PostMapping("/class-subjects/from-curriculum")
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_CREATE')")
    @Operation(summary = "Create class subjects from the curriculum", description = "One class-level subject per curriculum subject; existing ones are skipped")
    public ResponseEntity<ApiResponse<BulkResult>> classSubjectsFromCurriculum(
            @PathVariable UUID id, @RequestBody(required = false) FromCurriculumRequest request) {
        return ResponseEntity.ok(ApiResponse.success("CLASS_SUBJECTS_CREATED", "Class subjects created",
                actions.classSubjectsFromCurriculum(id, request)));
    }

    @PostMapping("/class-teachers/bulk")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_CREATE') and @dataAccess.hasFullScope(authentication, 'STAFF')")
    @Operation(summary = "Assign class teachers to many sections", description = "All or nothing; sections that already have one are skipped")
    public ResponseEntity<ApiResponse<BulkResult>> bulkClassTeachers(@PathVariable UUID id, @Valid @RequestBody ClassTeachersRequest request) {
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHERS_ASSIGNED", "Class teachers assigned",
                actions.bulkClassTeachers(id, request)));
    }

    // ---- start -----------------------------------------------------------------------------

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_UPDATE')")
    @Operation(summary = "Start the academic year",
            description = "Checks required setup, activates the year, makes it current and activates its published timetable")
    public ResponseEntity<ApiResponse<StartYearResponse>> start(@PathVariable UUID id, @RequestBody(required = false) StartYearRequest request) {
        log.info("REST: Start academic year [{}]", id);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_STARTED", "Academic year started", actions.start(id, request)));
    }
}
