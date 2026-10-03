package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request payload to enroll a student into an academic year, class, and section")
public class CreateEnrollmentRequest {

    @NotNull(message = "Academic year ID is required")
    @Schema(description = "Academic year UUID")
    private UUID academicYearId;

    @NotNull(message = "Academic class ID is required")
    @Schema(description = "Academic class UUID")
    private UUID academicClassId;

    @Schema(description = "Section UUID (optional if class has no sections)")
    private UUID sectionId;

    @Size(max = 50, message = "Roll number cannot exceed 50 characters")
    @Schema(description = "Roll number assigned for this academic class/section", example = "15")
    private String rollNumber;

    @Schema(description = "Enrollment lifecycle status (defaults to ACTIVE)")
    private StudentEnrollmentStatus enrollmentStatus;

    @Schema(description = "Date of admission", example = "2026-06-01")
    private LocalDate admissionDate;

    @Schema(description = "Date of academic enrollment (defaults to today)", example = "2026-06-01")
    private LocalDate enrollmentDate;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Schema(description = "Administrative remarks", example = "Direct admission from previous school")
    private String remarks;

    @Builder.Default
    @Schema(description = "Auto-assign all compulsory subjects deployed for this class/section", example = "true")
    private Boolean autoAssignCompulsorySubjects = true;

    @Schema(description = "Optional explicit list of subject IDs to assign during enrollment")
    private List<UUID> subjectIds;
}
