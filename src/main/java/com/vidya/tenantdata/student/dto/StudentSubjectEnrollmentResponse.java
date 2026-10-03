package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentSubjectEnrollmentStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Subject enrollment details for a student")
public class StudentSubjectEnrollmentResponse {

    @Schema(description = "Subject enrollment UUID")
    private UUID id;

    @Schema(description = "Subject enrollment code", example = "SSE-A1B2C3D4")
    private String subjectEnrollmentCode;

    @Schema(description = "Enrollment UUID")
    private UUID enrollmentId;

    @Schema(description = "Subject UUID")
    private UUID subjectId;

    @Schema(description = "Subject code", example = "SUB-MATH07")
    private String subjectCode;

    @Schema(description = "Subject name", example = "Mathematics")
    private String subjectName;

    @Schema(description = "Subject type", example = "CORE")
    private SubjectType subjectType;

    @Schema(description = "Class subject assignment UUID if linked")
    private UUID classSubjectAssignmentId;

    @Schema(description = "Class subject assignment code if linked", example = "CSA-A1B2C3D4")
    private String classSubjectAssignmentCode;

    @Schema(description = "Whether this subject is compulsory for the class", example = "true")
    private Boolean isCompulsory;

    @Schema(description = "Status of subject enrollment", example = "ACTIVE")
    private StudentSubjectEnrollmentStatus status;

    @Schema(description = "Effective start date", example = "2026-06-01")
    private LocalDate effectiveFrom;

    @Schema(description = "Effective end date", example = "2026-10-15")
    private LocalDate effectiveTo;

    @Schema(description = "Remarks")
    private String remarks;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
