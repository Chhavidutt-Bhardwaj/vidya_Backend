package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed academic enrollment response including enrolled subjects")
public class EnrollmentDetailResponse {

    @Schema(description = "Enrollment UUID")
    private UUID id;

    @Schema(description = "Enrollment business code", example = "ENR-A1B2C3D4")
    private String enrollmentCode;

    @Schema(description = "Student details")
    private StudentResponse student;

    @Schema(description = "Academic year UUID")
    private UUID academicYearId;

    @Schema(description = "Academic year code", example = "AY2026-27")
    private String academicYearCode;

    @Schema(description = "Academic year name", example = "Academic Year 2026-2027")
    private String academicYearName;

    @Schema(description = "Academic class UUID")
    private UUID academicClassId;

    @Schema(description = "Academic class code", example = "CLS-07")
    private String academicClassCode;

    @Schema(description = "Academic class name", example = "Class 7")
    private String academicClassName;

    @Schema(description = "Section UUID")
    private UUID sectionId;

    @Schema(description = "Section code", example = "SEC-A")
    private String sectionCode;

    @Schema(description = "Section name", example = "Section A")
    private String sectionName;

    @Schema(description = "Roll number in this class/section", example = "15")
    private String rollNumber;

    @Schema(description = "Enrollment lifecycle status", example = "ACTIVE")
    private StudentEnrollmentStatus enrollmentStatus;

    @Schema(description = "Admission date", example = "2026-06-01")
    private LocalDate admissionDate;

    @Schema(description = "Enrollment date", example = "2026-06-01")
    private LocalDate enrollmentDate;

    @Schema(description = "Withdrawal date if applicable", example = "2026-10-15")
    private LocalDate withdrawalDate;

    @Schema(description = "Withdrawal reason if applicable")
    private String withdrawalReason;

    @Schema(description = "Remarks")
    private String remarks;

    @Schema(description = "List of subjects enrolled under this academic placement")
    private List<StudentSubjectEnrollmentResponse> enrolledSubjects;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
