package com.vidya.tenantdata.staff.setup.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.BulkOnboardRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.BulkOnboardResult;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyGroup;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyRequest;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyResponse;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyTypeRequest;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyTypeResponse;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.EndDutyRequest;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.StaffDutiesResponse;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DepartmentRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DepartmentResponse;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DesignationRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DesignationResponse;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OffboardRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OffboardResult;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OnboardResult;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OnboardStaffRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.StaffOverview;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.StaffSubjectsRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.StatusRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.SubjectSkillResponse;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.TeacherSuggestion;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.TemplateRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.TemplateResult;
import com.vidya.tenantdata.staff.setup.service.StaffDutyService;
import com.vidya.tenantdata.staff.setup.service.StaffOnboardingService;
import com.vidya.tenantdata.staff.setup.service.StaffOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/** Staff & teacher setup: departments, designations, onboarding, logins and roles, offboarding, teaching subjects. */
@RestController
@RequestMapping("/api/v1/staff-setup")
@RequiredArgsConstructor
@Tag(name = "Staff Setup", description = "Departments, designations, duties, teachers and non-teaching staff. Logins: /api/v1/tenant/users/{id}/account")
public class StaffSetupController {

    private static final String ALL_STAFF = "@dataAccess.hasFullScope(authentication, 'STAFF')";

    private final StaffOrgService orgService;
    private final StaffOnboardingService onboardingService;
    private final StaffDutyService dutyService;

    // ── Overview & template ──────────────────────────────────────────────────

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('STAFF_READ') and " + ALL_STAFF)
    @Operation(summary = "Staff overview", description = "Counts by type, status, department and designation, plus problems to fix")
    public ResponseEntity<ApiResponse<StaffOverview>> overview() {
        return ResponseEntity.ok(ApiResponse.success("STAFF_OVERVIEW", "Staff overview", orgService.overview()));
    }

    @PostMapping("/templates/indian-school")
    @PreAuthorize("hasAuthority('STAFF_CREATE')")
    @Operation(summary = "Add standard departments and designations",
            description = "18 departments and 29 designations (Principal, PGT/TGT/PRT, Accountant, Driver …) with default login roles. Existing ones are kept.")
    public ResponseEntity<ApiResponse<TemplateResult>> template(@RequestBody(required = false) TemplateRequest request) {
        TemplateResult r = orgService.applyTemplate(request);
        return ResponseEntity.status(r.dryRun() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success("STAFF_TEMPLATE", r.dryRun() ? "Template preview" : "Template applied", r));
    }

    // ── Departments ──────────────────────────────────────────────────────────

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('STAFF_READ')")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> departments() {
        return ResponseEntity.ok(ApiResponse.success("DEPARTMENTS_FETCHED", "Departments", orgService.departments()));
    }

    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('STAFF_CREATE')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("DEPARTMENT_CREATED", "Department created", orgService.createDepartment(request)));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('STAFF_UPDATE')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("DEPARTMENT_UPDATED", "Department updated", orgService.updateDepartment(id, request)));
    }

    @PatchMapping("/departments/{id}/status")
    @PreAuthorize("hasAuthority('STAFF_UPDATE')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> departmentStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("DEPARTMENT_STATUS_UPDATED", "Department status updated", orgService.setDepartmentStatus(id, request.status())));
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('STAFF_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable UUID id) {
        orgService.deleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.success("DEPARTMENT_DELETED", "Department deleted"));
    }

    // ── Designations ─────────────────────────────────────────────────────────

    @GetMapping("/designations")
    @PreAuthorize("hasAuthority('STAFF_READ')")
    public ResponseEntity<ApiResponse<List<DesignationResponse>>> designations() {
        return ResponseEntity.ok(ApiResponse.success("DESIGNATIONS_FETCHED", "Designations", orgService.designations()));
    }

    @PostMapping("/designations")
    @PreAuthorize("hasAuthority('STAFF_CREATE')")
    public ResponseEntity<ApiResponse<DesignationResponse>> createDesignation(@Valid @RequestBody DesignationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("DESIGNATION_CREATED", "Designation created", orgService.createDesignation(request)));
    }

    @PutMapping("/designations/{id}")
    @PreAuthorize("hasAuthority('STAFF_UPDATE')")
    public ResponseEntity<ApiResponse<DesignationResponse>> updateDesignation(@PathVariable UUID id, @Valid @RequestBody DesignationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("DESIGNATION_UPDATED", "Designation updated", orgService.updateDesignation(id, request)));
    }

    @PatchMapping("/designations/{id}/status")
    @PreAuthorize("hasAuthority('STAFF_UPDATE')")
    public ResponseEntity<ApiResponse<DesignationResponse>> designationStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("DESIGNATION_STATUS_UPDATED", "Designation status updated", orgService.setDesignationStatus(id, request.status())));
    }

    @DeleteMapping("/designations/{id}")
    @PreAuthorize("hasAuthority('STAFF_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteDesignation(@PathVariable UUID id) {
        orgService.deleteDesignation(id);
        return ResponseEntity.ok(ApiResponse.success("DESIGNATION_DELETED", "Designation deleted"));
    }

    // ── Onboarding ───────────────────────────────────────────────────────────

    @PostMapping("/onboard")
    @PreAuthorize("hasAuthority('STAFF_CREATE') and " + ALL_STAFF)
    @Operation(summary = "Add a teacher or staff member",
            description = "Person, staff record, teaching subjects, duties (HOD, coordinator …) and (optionally) login in one step. "
                    + "A new login's temporary password is returned once. Logins of existing staff: POST /api/v1/tenant/users/{id}/account.")
    public ResponseEntity<ApiResponse<OnboardResult>> onboard(@Valid @RequestBody OnboardStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("STAFF_ONBOARDED", "Staff member added", onboardingService.onboard(request)));
    }

    @PostMapping("/onboard/bulk")
    @PreAuthorize("hasAuthority('STAFF_CREATE') and " + ALL_STAFF)
    @Operation(summary = "Add many staff at once (all or nothing)", description = "422 with per-row errors when any row is invalid; dryRun validates only")
    public ResponseEntity<ApiResponse<BulkOnboardResult>> bulk(@Valid @RequestBody BulkOnboardRequest request) {
        BulkOnboardResult r = onboardingService.bulkOnboard(request);
        HttpStatus status = !r.valid() ? HttpStatus.UNPROCESSABLE_CONTENT : r.dryRun() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.success("STAFF_BULK_ONBOARD",
                !r.valid() ? "Some rows are invalid; nothing was saved" : r.dryRun() ? "All rows are valid" : r.created() + " staff added", r));
    }

    // ── Duties (HOD, coordinators, in-charges) ───────────────────────────────

    @GetMapping("/duty-types")
    @PreAuthorize("hasAuthority('STAFF_READ')")
    @Operation(summary = "Kinds of duty", description = "Standard ones (HOD, Exam Coordinator, Timetable In-charge …) plus the school's own")
    public ResponseEntity<ApiResponse<List<DutyTypeResponse>>> dutyTypes() {
        return ResponseEntity.ok(ApiResponse.success("DUTY_TYPES", "Duty types", dutyService.dutyTypes()));
    }

    @PostMapping("/duty-types")
    @PreAuthorize("hasAuthority('STAFF_CREATE')")
    public ResponseEntity<ApiResponse<DutyTypeResponse>> createDutyType(@Valid @RequestBody DutyTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("DUTY_TYPE_CREATED", "Duty type created", dutyService.createDutyType(request)));
    }

    @PutMapping("/duty-types/{id}")
    @PreAuthorize("hasAuthority('STAFF_UPDATE')")
    public ResponseEntity<ApiResponse<DutyTypeResponse>> updateDutyType(@PathVariable UUID id, @Valid @RequestBody DutyTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("DUTY_TYPE_UPDATED", "Duty type updated", dutyService.updateDutyType(id, request)));
    }

    @PatchMapping("/duty-types/{id}/status")
    @PreAuthorize("hasAuthority('STAFF_UPDATE')")
    public ResponseEntity<ApiResponse<DutyTypeResponse>> dutyTypeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("DUTY_TYPE_STATUS_UPDATED", "Duty type status updated", dutyService.setDutyTypeStatus(id, request.status())));
    }

    @DeleteMapping("/duty-types/{id}")
    @PreAuthorize("hasAuthority('STAFF_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteDutyType(@PathVariable UUID id) {
        dutyService.deleteDutyType(id);
        return ResponseEntity.ok(ApiResponse.success("DUTY_TYPE_DELETED", "Duty type deleted"));
    }

    @GetMapping("/duties")
    @PreAuthorize("hasAuthority('STAFF_READ')")
    @Operation(summary = "Who does what", description = "Every duty type with its current holders")
    public ResponseEntity<ApiResponse<List<DutyGroup>>> duties() {
        return ResponseEntity.ok(ApiResponse.success("DUTIES", "Duties", dutyService.directory()));
    }

    @GetMapping("/staff/{staffId}/duties")
    @PreAuthorize("hasAuthority('STAFF_READ') and @dataAccess.canAccessStaff(authentication, #staffId, 'STAFF')")
    @Operation(summary = "Everything one person is responsible for", description = "Duties plus class-teacher and subject-teacher assignments")
    public ResponseEntity<ApiResponse<StaffDutiesResponse>> staffDuties(@PathVariable UUID staffId,
                                                                        @RequestParam(defaultValue = "false") boolean includeEnded) {
        return ResponseEntity.ok(ApiResponse.success("STAFF_DUTIES", "Staff duties", dutyService.dutiesOf(staffId, includeEnded)));
    }

    @PostMapping("/staff/{staffId}/duties")
    @PreAuthorize("hasAuthority('STAFF_UPDATE') and " + ALL_STAFF)
    @Operation(summary = "Give a staff member a duty",
            description = "A person can hold many duties at once. The duty's access role (if any) goes on their login while it lasts; "
                    + "that needs SCHOOL_ROLE_MANAGE. For single-holder duties send replace=true to hand over from the current holder.")
    public ResponseEntity<ApiResponse<DutyResponse>> assignDuty(@PathVariable UUID staffId, @Valid @RequestBody DutyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("DUTY_ASSIGNED", "Duty assigned", dutyService.assign(staffId, request)));
    }

    @PostMapping("/duties/{dutyId}/end")
    @PreAuthorize("hasAuthority('STAFF_UPDATE') and " + ALL_STAFF)
    @Operation(summary = "End a duty", description = "Takes the duty's access role off the login unless another active duty still needs it")
    public ResponseEntity<ApiResponse<DutyResponse>> endDuty(@PathVariable UUID dutyId, @RequestBody(required = false) EndDutyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("DUTY_ENDED", "Duty ended", dutyService.end(dutyId, request)));
    }

    // ── Offboarding ──────────────────────────────────────────────────────────

    @PostMapping("/staff/{staffId}/offboard")
    @PreAuthorize("hasAuthority('STAFF_STATUS_UPDATE') and " + ALL_STAFF)
    @Operation(summary = "Staff member leaves or is suspended",
            description = "Sets the status, disables the login, ends duties and reports (or ends) class / subject teacher assignments")
    public ResponseEntity<ApiResponse<OffboardResult>> offboard(@PathVariable UUID staffId, @Valid @RequestBody OffboardRequest request) {
        return ResponseEntity.ok(ApiResponse.success("STAFF_OFFBOARDED", "Staff status updated", onboardingService.offboard(staffId, request)));
    }

    // ── Subjects ─────────────────────────────────────────────────────────────

    @GetMapping("/staff/{staffId}/subjects")
    @PreAuthorize("hasAuthority('STAFF_READ') and @dataAccess.canAccessStaff(authentication, #staffId, 'STAFF')")
    public ResponseEntity<ApiResponse<List<SubjectSkillResponse>>> subjects(@PathVariable UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success("STAFF_SUBJECTS", "Teaching subjects", onboardingService.subjectsOf(staffId)));
    }

    @PutMapping("/staff/{staffId}/subjects")
    @PreAuthorize("hasAuthority('STAFF_UPDATE') and " + ALL_STAFF)
    @Operation(summary = "Set the subjects (and class range) a teacher can teach")
    public ResponseEntity<ApiResponse<List<SubjectSkillResponse>>> setSubjects(@PathVariable UUID staffId, @Valid @RequestBody StaffSubjectsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("STAFF_SUBJECTS_UPDATED", "Teaching subjects saved", onboardingService.setSubjects(staffId, request.subjects())));
    }

    @GetMapping("/teachers-for-subject")
    @PreAuthorize("hasAuthority('STAFF_READ') and " + ALL_STAFF)
    @Operation(summary = "Teachers who can teach a subject", description = "For a class when given; least loaded and primary-subject teachers first")
    public ResponseEntity<ApiResponse<List<TeacherSuggestion>>> teachersFor(@RequestParam UUID subjectId, @RequestParam(required = false) UUID academicClassId) {
        return ResponseEntity.ok(ApiResponse.success("TEACHER_SUGGESTIONS", "Teachers for subject", onboardingService.teachersFor(subjectId, academicClassId)));
    }
}
