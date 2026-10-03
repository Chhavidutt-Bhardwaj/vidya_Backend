package com.vidya.tenantdata.assignment.dto;

import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed DTO for single ClassSubjectAssignment queries.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed representation of a class subject assignment")
public class ClassSubjectAssignmentDetailResponse {

    @Schema(description = "Assignment UUID")
    private UUID id;

    @Schema(description = "Stable business assignment code (e.g. CSA-A1B2C3D4)", example = "CSA-A1B2C3D4")
    private String assignmentCode;

    @Schema(description = "Academic Year UUID")
    private UUID academicYearId;

    @Schema(description = "Academic Year Code", example = "AY2026-27")
    private String academicYearCode;

    @Schema(description = "Academic Class UUID")
    private UUID academicClassId;

    @Schema(description = "Academic Class Code", example = "CLS-10")
    private String academicClassCode;

    @Schema(description = "Academic Class Name", example = "Class 10")
    private String academicClassName;

    @Schema(description = "Section UUID (optional)")
    private UUID sectionId;

    @Schema(description = "Section Code", example = "SEC-10A")
    private String sectionCode;

    @Schema(description = "Section Name", example = "Section A")
    private String sectionName;

    @Schema(description = "Subject UUID")
    private UUID subjectId;

    @Schema(description = "Subject Code", example = "SUB-A1B2C3D4")
    private String subjectCode;

    @Schema(description = "Subject Name", example = "Mathematics")
    private String subjectName;

    @Schema(description = "Curriculum UUID (optional)")
    private UUID curriculumId;

    @Schema(description = "Curriculum Code", example = "CUR-A1B2C3D4")
    private String curriculumCode;

    @Schema(description = "Whether subject is compulsory for this class/section")
    private boolean isCompulsory;

    @Schema(description = "Display order for UI/reporting")
    private Integer displayOrder;

    @Schema(description = "Scheduled periods per week")
    private Integer periodsPerWeek;

    @Schema(description = "Assignment lifecycle status", example = "ACTIVE")
    private ClassSubjectAssignmentStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "User who created the record")
    private UUID createdBy;

    @Schema(description = "User who last modified the record")
    private UUID updatedBy;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
