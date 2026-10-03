package com.vidya.tenantdata.subject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for updating a Curriculum's details.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update a curriculum")
public class UpdateCurriculumRequest {

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name cannot exceed 150 characters")
    @Schema(description = "Curriculum name", example = "CBSE Revised 2026-27 Curriculum")
    private String name;

    @Size(max = 1000, message = "description cannot exceed 1000 characters")
    @Schema(description = "Curriculum description", example = "Updated school curriculum for academic year 2026-27")
    private String description;
}
