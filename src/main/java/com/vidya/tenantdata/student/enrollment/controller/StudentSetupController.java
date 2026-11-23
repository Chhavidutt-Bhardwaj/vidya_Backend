package com.vidya.tenantdata.student.enrollment.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmissionSequenceRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmissionSequenceResponse;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmitResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmitStudentRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.BulkAdmitRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.BulkAdmitResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.EnrollmentOverview;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.Family;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.GuardianWithParent;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LeaveRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LeaveResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.LinkParentRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.MergeParentsRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.MergeParentsResult;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.ParentSummary;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.ReadmitRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.SiblingRef;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.StudentFullProfile;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.UpdateProfileRequest;
import com.vidya.tenantdata.student.enrollment.service.AdmissionNumberService;
import com.vidya.tenantdata.student.enrollment.service.ParentLinkService;
import com.vidya.tenantdata.student.enrollment.service.StudentEnrollmentSetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

/**
 * Student & parent enrollment: admissions, student profiles, parent mapping, leaving / re-admission, overview.
 * Promotion of existing students: {@code POST /api/v1/academic-years/{id}/promotion}.
 */
@RestController
@RequestMapping("/api/v1/student-setup")
@RequiredArgsConstructor
@Tag(name = "Student Setup", description = "New admissions, student profiles, parent mapping and logins, leaving and re-admission. "
        + "Promotion: /api/v1/academic-years/{id}/promotion")
public class StudentSetupController {

    private static final String ALL_STUDENTS = "@dataAccess.hasFullScope(authentication, 'STUDENT')";
    private static final String THIS_STUDENT = "@dataAccess.canAccessStudent(authentication, #studentId, 'STUDENT')";

    private final StudentEnrollmentSetupService setup;
    private final AdmissionNumberService admissionNumbers;
    private final ParentLinkService parents;

    // ── Overview ─────────────────────────────────────────────────────────────

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('STUDENT_READ') and " + ALL_STUDENTS)
    @Operation(summary = "Enrollment overview", description = "Students per class and section, new admissions, parents and logins, pending promotion, and problems to fix")
    public ResponseEntity<ApiResponse<EnrollmentOverview>> overview(@RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("ENROLLMENT_OVERVIEW", "Enrollment overview", setup.overview(academicYearId)));
    }

    // ── Admission numbers ────────────────────────────────────────────────────

    @GetMapping("/admission-numbers")
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    @Operation(summary = "Admission number sequences", description = "Per academic year or school-wide; shows the next number each would give")
    public ResponseEntity<ApiResponse<List<AdmissionSequenceResponse>>> sequences() {
        return ResponseEntity.ok(ApiResponse.success("ADMISSION_SEQUENCES", "Admission number sequences", admissionNumbers.list()));
    }

    @PutMapping("/admission-numbers")
    @PreAuthorize("hasAuthority('STUDENT_CREATE') and " + ALL_STUDENTS)
    @Operation(summary = "Configure admission numbers", description = "Set prefix, next number and padding for a year (or school-wide when academicYearId is omitted)")
    public ResponseEntity<ApiResponse<AdmissionSequenceResponse>> configure(@Valid @RequestBody AdmissionSequenceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ADMISSION_SEQUENCE_SAVED", "Admission numbers configured", admissionNumbers.configure(request)));
    }

    // ── Admission ────────────────────────────────────────────────────────────

    @PostMapping("/admit")
    @PreAuthorize("hasAuthority('STUDENT_CREATE') and " + ALL_STUDENTS)
    @Operation(summary = "Admit a new student",
            description = "Person + student record + class placement + subjects + guardians in one call. Guardians are linked to an existing "
                    + "parent (same email / phone) or a new parent record; login=true on a guardian creates the parent's login (temporary "
                    + "password returned once). Blank admissionNumber = next from the year's sequence.")
    public ResponseEntity<ApiResponse<AdmitResult>> admit(@Valid @RequestBody AdmitStudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("STUDENT_ADMITTED", "Student admitted", setup.admit(request)));
    }

    @PostMapping("/admit/bulk")
    @PreAuthorize("hasAuthority('STUDENT_CREATE') and " + ALL_STUDENTS)
    @Operation(summary = "Admit many students", description = "Up to 500 rows, all or nothing. dryRun=true validates only (422 when any row is invalid).")
    public ResponseEntity<ApiResponse<BulkAdmitResult>> bulk(@Valid @RequestBody BulkAdmitRequest request) {
        BulkAdmitResult r = setup.bulkAdmit(request);
        HttpStatus status = !r.valid() ? HttpStatus.UNPROCESSABLE_ENTITY : r.dryRun() ? HttpStatus.OK : HttpStatus.CREATED;
        String code = !r.valid() ? "BULK_ADMISSION_INVALID" : r.dryRun() ? "BULK_ADMISSION_PREVIEW" : "BULK_ADMISSION_DONE";
        return ResponseEntity.status(status).body(ApiResponse.success(code, r.valid() ? r.admitted() + " student(s) admitted" : "Fix the rows with errors", r));
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAuthority('STUDENT_READ') and " + THIS_STUDENT)
    @Operation(summary = "Full student profile", description = "Person, school details, current class, history, guardians with parent logins, siblings, attendance")
    public ResponseEntity<ApiResponse<StudentFullProfile>> profile(@PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_PROFILE", "Student profile", setup.profile(studentId)));
    }

    @PutMapping("/students/{studentId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') and " + ALL_STUDENTS)
    @Operation(summary = "Update student profile", description = "Person fields and school details; omitted fields are left unchanged")
    public ResponseEntity<ApiResponse<StudentFullProfile>> update(@PathVariable UUID studentId, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_PROFILE_UPDATED", "Profile updated", setup.updateProfile(studentId, request)));
    }

    @GetMapping("/students/{studentId}/siblings")
    @PreAuthorize("hasAuthority('STUDENT_READ') and " + THIS_STUDENT)
    @Operation(summary = "Siblings", description = "Other students who share a parent record with this one")
    public ResponseEntity<ApiResponse<List<SiblingRef>>> siblings(@PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_SIBLINGS", "Siblings", parents.siblingsOf(studentId)));
    }

    // ── Parents ──────────────────────────────────────────────────────────────

    @GetMapping("/parents")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_READ') and " + ALL_STUDENTS)
    @Operation(summary = "Parent directory", description = "Every parent record with its children and login state")
    public ResponseEntity<ApiResponse<List<ParentSummary>>> parents(@RequestParam(required = false) String search,
                                                                    @RequestParam(required = false) Boolean withLogin) {
        return ResponseEntity.ok(ApiResponse.success("PARENTS", "Parents", parents.directory(search, withLogin)));
    }

    @GetMapping("/families")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_READ') and " + ALL_STUDENTS)
    @Operation(summary = "Families", description = "Households: parent records and the children they share (two parents with three children = one family)")
    public ResponseEntity<ApiResponse<List<Family>>> families(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success("FAMILIES", "Families", parents.families(search)));
    }

    @PostMapping("/parents/{parentUserId}/merge")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_UPDATE') and " + ALL_STUDENTS)
    @Operation(summary = "Merge a duplicate parent record",
            description = "Folds mergeParentUserId into this parent: guardian rows of all children move over, blank contact details are filled, "
                    + "the login is moved (or the duplicate's disabled when both have one), and the duplicate record is deleted")
    public ResponseEntity<ApiResponse<MergeParentsResult>> merge(@PathVariable UUID parentUserId, @Valid @RequestBody MergeParentsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PARENTS_MERGED", "Parent records merged", parents.merge(parentUserId, request)));
    }

    @GetMapping("/students/{studentId}/guardians")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_READ') and " + THIS_STUDENT)
    @Operation(summary = "Guardians with parent records", description = "Each guardian with the parent record / login behind it and the parent's other children")
    public ResponseEntity<ApiResponse<List<GuardianWithParent>>> guardians(@PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_GUARDIANS", "Guardians", parents.guardiansOf(studentId)));
    }

    @PostMapping("/students/{studentId}/guardians/{guardianId}/parent")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_UPDATE') and " + ALL_STUDENTS)
    @Operation(summary = "Link guardian to a parent record",
            description = "Links to parentUserId, else to an existing parent with the same email / phone, else creates one. login=true also creates "
                    + "the parent's login. Parent logins for the other children of the same parent are the same login.")
    public ResponseEntity<ApiResponse<GuardianWithParent>> link(@PathVariable UUID studentId, @PathVariable UUID guardianId,
                                                                @RequestBody(required = false) LinkParentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("GUARDIAN_PARENT_LINKED", "Parent linked", parents.link(studentId, guardianId, request)));
    }

    @DeleteMapping("/students/{studentId}/guardians/{guardianId}/parent")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_UPDATE') and " + ALL_STUDENTS)
    @Operation(summary = "Unlink guardian from its parent record", description = "The parent record, its login and other children are untouched")
    public ResponseEntity<ApiResponse<GuardianWithParent>> unlink(@PathVariable UUID studentId, @PathVariable UUID guardianId) {
        return ResponseEntity.ok(ApiResponse.success("GUARDIAN_PARENT_UNLINKED", "Parent unlinked", parents.unlink(studentId, guardianId)));
    }

    // ── Leaving / re-admission ───────────────────────────────────────────────

    @PostMapping("/students/{studentId}/leave")
    @PreAuthorize("hasAuthority('STUDENT_WITHDRAW') and " + ALL_STUDENTS)
    @Operation(summary = "Student leaves the school",
            description = "TRANSFERRED / WITHDRAWN / GRADUATED: closes open enrollments, records the TC number, disables the student's login and "
                    + "parents' logins that have no other active child (disableLogins=false keeps them)")
    public ResponseEntity<ApiResponse<LeaveResult>> leave(@PathVariable UUID studentId, @Valid @RequestBody LeaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_LEFT", "Student marked as " + request.type(), setup.leave(studentId, request)));
    }

    @PostMapping("/students/{studentId}/readmit")
    @PreAuthorize("hasAuthority('STUDENT_ENROLL') and " + ALL_STUDENTS)
    @Operation(summary = "Re-admit a withdrawn student", description = "Same student record and admission number; new enrollment in the given class. enableLogins=true re-enables logins.")
    public ResponseEntity<ApiResponse<AdmitResult>> readmit(@PathVariable UUID studentId, @Valid @RequestBody ReadmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("STUDENT_READMITTED", "Student re-admitted", setup.readmit(studentId, request)));
    }
}
