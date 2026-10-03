package com.vidya.tenantdata.subject.dto;

import com.vidya.tenantdata.subject.enums.SubjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating a new academic Subject.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create an academic subject")
public class CreateSubjectRequest {

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name cannot exceed 150 characters")
    @Schema(description = "Subject name", example = "Mathematics")
    private String name;

    @Size(max = 50, message = "shortName cannot exceed 50 characters")
    @Schema(description = "Subject short abbreviation", example = "MATH")
    private String shortName;

    @Size(max = 1000, message = "description cannot exceed 1000 characters")
    @Schema(description = "Subject description", example = "Core mathematics covering algebra, geometry and calculus")
    private String description;

    @NotNull(message = "subjectType is required")
    @Schema(description = "Controlled subject classification", example = "CORE")
    private SubjectType subjectType;

    @Min(value = 0, message = "displayOrder cannot be negative")
    @Schema(description = "Display order for UI/reporting", example = "1")
    private Integer displayOrder;
}
