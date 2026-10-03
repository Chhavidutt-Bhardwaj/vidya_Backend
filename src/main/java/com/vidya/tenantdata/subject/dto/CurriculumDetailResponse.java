package com.vidya.tenantdata.subject.dto;

import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed DTO for single Curriculum queries.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed representation of a curriculum")
public class CurriculumDetailResponse {

    @Schema(description = "Curriculum UUID")
    private UUID id;

    @Schema(description = "Stable business curriculum code (e.g. CUR-A1B2C3D4)", example = "CUR-A1B2C3D4")
    private String curriculumCode;

    @Schema(description = "Associated AcademicYear UUID")
    private UUID academicYearId;

    @Schema(description = "AcademicYear code", example = "AY2026-27")
    private String academicYearCode;

    @Schema(description = "AcademicYear name", example = "Academic Year 2026-2027")
    private String academicYearName;

    @Schema(description = "Curriculum name", example = "CBSE 2026-27 Curriculum")
    private String name;

    @Schema(description = "Curriculum description")
    private String description;

    @Schema(description = "Current lifecycle status", example = "DRAFT")
    private CurriculumStatus status;

    @Schema(description = "List of assigned subjects in this curriculum")
    private List<CurriculumSubjectResponse> subjects;

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
