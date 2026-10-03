package com.vidya.tenantdata.subject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request payload for creating a new Curriculum.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create a curriculum")
public class CreateCurriculumRequest {

    @NotNull(message = "academicYearId is required")
    @Schema(description = "UUID of the AcademicYear in this tenant's database", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID academicYearId;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name cannot exceed 150 characters")
    @Schema(description = "Curriculum name", example = "CBSE 2026-27 Curriculum")
    private String name;

    @Size(max = 1000, message = "description cannot exceed 1000 characters")
    @Schema(description = "Curriculum description", example = "School curriculum for academic year 2026-27")
    private String description;
}
