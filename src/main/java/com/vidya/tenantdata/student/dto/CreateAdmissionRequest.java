package com.vidya.tenantdata.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transactional admission request payload creating Student Identity, User Profile, Academic Enrollment, and optional Guardians/Subjects atomically")
public class CreateAdmissionRequest {

    // Identity / Profile
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    @Schema(description = "Student first name", example = "Rahul")
    private String firstName;

    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    @Schema(description = "Student last name", example = "Sharma")
    private String lastName;

    @Schema(description = "Display name (optional)", example = "Rahul Sharma")
    private String displayName;

    @Schema(description = "Gender", example = "MALE")
    private String gender;

    @Past(message = "Date of birth must be in the past")
    @Schema(description = "Date of birth", example = "2013-05-12")
    private LocalDate dateOfBirth;

    @Schema(description = "Student email (optional, auto-generated if blank)", example = "rahul.sharma@school.vidya.internal")
    private String email;

    @Schema(description = "Contact phone number", example = "+919876543210")
    private String phone;

    @Schema(description = "Address line 1", example = "123 Green Avenue")
    private String addressLine1;

    @Schema(description = "Address line 2", example = "Block C")
    private String addressLine2;

    @Schema(description = "City", example = "New Delhi")
    private String city;

    @Schema(description = "State", example = "Delhi")
    private String state;

    @Schema(description = "Postal code", example = "110001")
    private String postalCode;

    // Admission Information
    @NotBlank(message = "Admission number is required")
    @Size(max = 100, message = "Admission number cannot exceed 100 characters")
    @Schema(description = "School-assigned admission number (unique per tenant)", example = "ADM-2026-00123")
    private String admissionNumber;

    @NotNull(message = "Admission date is required")
    @Schema(description = "Official admission date", example = "2026-06-01")
    private LocalDate admissionDate;

    // Academic Enrollment Information
    @NotNull(message = "Academic year ID is required")
    @Schema(description = "Academic year UUID")
    private UUID academicYearId;

    @NotNull(message = "Academic class ID is required")
    @Schema(description = "Academic class UUID")
    private UUID academicClassId;

    @Schema(description = "Section UUID (optional)")
    private UUID sectionId;

    @Size(max = 50, message = "Roll number cannot exceed 50 characters")
    @Schema(description = "Roll number in the class/section", example = "15")
    private String rollNumber;

    @Schema(description = "Enrollment date (defaults to admissionDate)", example = "2026-06-01")
    private LocalDate enrollmentDate;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Schema(description = "Administrative remarks", example = "Admitted under general category")
    private String remarks;

    // Subject Options
    @Builder.Default
    @Schema(description = "Whether to auto-assign all compulsory subjects configured for this class", example = "true")
    private Boolean autoAssignCompulsorySubjects = true;

    @Schema(description = "Optional additional elective subject IDs to enroll")
    private List<UUID> subjectIds;

    // Optional Guardians List
    @Schema(description = "Guardian / Parent records to create atomically with admission")
    private List<CreateStudentGuardianRequest> guardians;
}
