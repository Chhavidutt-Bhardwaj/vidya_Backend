package com.vidya.tenantdata.student.enrollment.dto;

import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.EnrollmentResponse;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.entity.GuardianRelationshipType;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.user.account.dto.AccountDtos.LoginInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Student & parent enrollment: admissions, profiles, parent mapping, leaving and re-admission. */
public final class StudentSetupDtos {

    private StudentSetupDtos() {
    }

    // ── Admission numbers ────────────────────────────────────────────────────

    /**
     * @param academicYearId null = one school-wide sequence
     * @param prefix         e.g. "2027/" or "GPS-"; default: start year + "/"
     * @param nextNumber     the next number to give
     * @param padding        zero padding (0001 = 4)
     */
    public record AdmissionSequenceRequest(UUID academicYearId, @Size(max = 20) String prefix, @Min(1) Integer nextNumber,
                                           @Min(0) @Max(8) Integer padding) {
    }

    public record AdmissionSequenceResponse(UUID id, UUID academicYearId, String academicYear, String prefix, int nextNumber, int padding,
                                            String nextAdmissionNumber) {
    }

    // ── Admission ────────────────────────────────────────────────────────────

    /**
     * A parent / guardian of the student. The same person can be the parent of several children: when
     * {@code parentUserId} is given, or an existing parent with the same email / phone is found, the guardian
     * is linked to that parent record (and their login, if any).
     *
     * @param parentUserId  existing parent (tenant user of type PARENT)
     * @param linkExisting  look for an existing parent by email / phone and link (default true)
     * @param createParent  create a parent record when none exists (default true when email or phone is given)
     * @param login         also create the parent's login (needs email; default false). Temporary password returned once.
     */
    public record GuardianRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull GuardianRelationshipType relationship,
            @Size(max = 30) String phone,
            @Email @Size(max = 255) String email,
            @Size(max = 100) String occupation,
            BigDecimal annualIncome,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String postalCode,
            Boolean primary,
            Boolean emergencyContact,
            UUID parentUserId,
            Boolean linkExisting,
            Boolean createParent,
            Boolean login) {
    }

    public record ProfileDetails(
            @Pattern(regexp = "^(A|B|AB|O)[+-]$") String bloodGroup,
            @Size(max = 60) String nationality,
            @Size(max = 60) String motherTongue,
            @Size(max = 50) String house,
            @Size(max = 200) String previousSchool,
            @Size(max = 50) String previousClass,
            @Size(max = 60) String tcNumberIn,
            @Size(max = 500) String medicalNotes,
            @Size(max = 500) String photoUrl) {
    }

    /**
     * One new admission: person, student record, class placement, subjects, guardians (with parent linking) and
     * optional logins, in one call.
     *
     * @param admissionNumber  blank = generated from the year's sequence
     * @param sectionId        optional; the student can be placed later
     * @param studentLogin     create a login for the student (needs a real email; default false)
     */
    public record AdmitStudentRequest(
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 20) String gender,
            LocalDate dateOfBirth,
            @Email @Size(max = 255) String email,
            @Size(max = 20) String phone,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String postalCode,
            @Size(max = 100) String admissionNumber,
            LocalDate admissionDate,
            @NotNull UUID academicYearId,
            @NotNull UUID academicClassId,
            UUID sectionId,
            @Size(max = 50) String rollNumber,
            Boolean autoAssignCompulsorySubjects,
            List<UUID> subjectIds,
            @Size(max = 500) String remarks,
            @Valid ProfileDetails details,
            @Size(max = 6) List<@Valid GuardianRequest> guardians,
            Boolean studentLogin) {
    }

    /** A guardian with the parent record / login behind it. */
    public record GuardianWithParent(StudentGuardianResponse guardian, UUID parentUserId, String parentUserCode, boolean parentHasLogin,
                                     LoginInfo parentLogin, List<SiblingRef> otherChildren) {
    }

    public record SiblingRef(UUID studentId, String studentCode, String name, String admissionNumber, String className, String sectionName,
                             StudentStatus status) {
    }

    public record AdmitResult(StudentDetailResponse student, EnrollmentResponse enrollment, List<GuardianWithParent> guardians,
                              LoginInfo studentLogin, List<SiblingRef> siblings, List<String> warnings) {
    }

    public record BulkAdmitRequest(@NotEmpty @Size(max = 500) List<@Valid AdmitStudentRequest> students, Boolean dryRun) {
    }

    public record BulkAdmitRow(int index, String name, String admissionNumber, String error, String studentCode, UUID studentId,
                               List<String> warnings) {
    }

    /** All or nothing: when {@code valid} is false nothing was saved (HTTP 422). */
    public record BulkAdmitResult(boolean dryRun, boolean valid, int admitted, int parentLoginsCreated, List<BulkAdmitRow> rows) {
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    public record AttendanceSummary(UUID academicYearId, long present, long absent, long late, long total, Integer percentage) {
    }

    /** Everything about one student on one screen. */
    public record StudentFullProfile(StudentDetailResponse student, ProfileDetailsResponse details, CurrentEnrollmentResponse currentEnrollment,
                                     List<EnrollmentResponse> enrollmentHistory, List<GuardianWithParent> guardians, List<SiblingRef> siblings,
                                     AttendanceSummary attendance, UUID tenantUserId, boolean hasLogin) {
    }

    public record ProfileDetailsResponse(String bloodGroup, String nationality, String motherTongue, String house, String previousSchool,
                                         String previousClass, String tcNumberIn, String tcNumberOut, String medicalNotes, String photoUrl,
                                         String leavingType, LocalDate leavingDate, String leavingReason) {
    }

    /** Person fields and school details in one update; null fields are left unchanged. */
    public record UpdateProfileRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 20) String gender,
            LocalDate dateOfBirth,
            @Email @Size(max = 255) String email,
            @Size(max = 20) String phone,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String postalCode,
            @Valid ProfileDetails details) {
    }

    // ── Parents ──────────────────────────────────────────────────────────────

    public record ParentSummary(UUID parentUserId, String userCode, String name, String email, String phone, boolean hasLogin,
                                List<SiblingRef> children) {
    }

    /** A household: parent records and the children they share. */
    public record Family(String familyKey, List<ParentSummary> parents, List<SiblingRef> children, int logins) {
    }

    /** @param mergeParentUserId the duplicate parent record to fold into the one in the path */
    public record MergeParentsRequest(@NotNull UUID mergeParentUserId) {
    }

    public record MergeParentsResult(ParentSummary parent, UUID mergedParentUserId, int guardianRowsMoved, boolean loginMoved,
                                     boolean duplicateLoginDisabled, List<String> warnings) {
    }

    /**
     * Link a guardian row to a parent record (one parent for all siblings).
     *
     * @param parentUserId existing parent; null = find by email / phone, else create from the guardian's details
     * @param login        also create the parent's login (needs email)
     */
    public record LinkParentRequest(UUID parentUserId, Boolean login) {
    }

    // ── Leaving / re-admission ───────────────────────────────────────────────

    public enum LeavingType { TRANSFERRED, WITHDRAWN, GRADUATED }

    /**
     * @param disableLogins disable the student's login and parents' logins that have no other active child (default true)
     */
    public record LeaveRequest(@NotNull LeavingType type, LocalDate date, @Size(max = 255) String reason, @Size(max = 60) String tcNumber,
                               @Size(max = 500) String remarks, Boolean disableLogins) {
    }

    public record LeaveResult(StudentStatus status, LocalDate date, String tcNumber, int enrollmentsClosed, boolean studentLoginDisabled,
                              List<String> parentLoginsDisabled, List<String> parentLoginsKept, List<String> nextSteps) {
    }

    public record ReadmitRequest(@NotNull UUID academicYearId, @NotNull UUID academicClassId, UUID sectionId, @Size(max = 50) String rollNumber,
                                 LocalDate date, Boolean autoAssignCompulsorySubjects, @Size(max = 500) String remarks, Boolean enableLogins) {
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    public record SectionCount(UUID sectionId, String section, long students, Integer capacity, boolean full) {
    }

    public record ClassCount(UUID classId, String className, long students, long withoutSection, List<SectionCount> sections) {
    }

    public record Issue(String level, String code, long count, String message, List<String> examples) {
    }

    public record EnrollmentOverview(UUID academicYearId, String academicYear, long activeStudents, long newAdmissions, long leftThisYear,
                                     long withoutSection, Map<StudentStatus, Long> byStatus, List<ClassCount> classes, long parents,
                                     long parentsWithLogin, long studentsWithLogin, long pendingPromotion, List<Issue> issues) {
    }
}
