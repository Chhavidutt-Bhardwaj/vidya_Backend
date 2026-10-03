package com.vidya.tenantdata.subject.dto;

import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed DTO for single Subject queries.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed representation of an academic subject")
public class SubjectDetailResponse {

    @Schema(description = "Subject UUID")
    private UUID id;

    @Schema(description = "Stable business subject code (e.g. SUB-A1B2C3D4)", example = "SUB-A1B2C3D4")
    private String subjectCode;

    @Schema(description = "Subject name", example = "Mathematics")
    private String name;

    @Schema(description = "Subject short abbreviation", example = "MATH")
    private String shortName;

    @Schema(description = "Subject description")
    private String description;

    @Schema(description = "Subject type/classification", example = "CORE")
    private SubjectType subjectType;

    @Schema(description = "Current lifecycle status", example = "ACTIVE")
    private SubjectStatus status;

    @Schema(description = "Display order for UI/reports", example = "1")
    private Integer displayOrder;

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
