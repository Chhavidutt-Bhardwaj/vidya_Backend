package com.vidya.tenantdata.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create an academic class / grade")
public class CreateAcademicClassRequest {

    @NotBlank(message = "code is required")
    @Size(max = 50, message = "code cannot exceed 50 characters")
    @Schema(description = "Unique code for the class level", example = "CLASS_5")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name cannot exceed 100 characters")
    @Schema(description = "Class name", example = "Class 5")
    private String name;

    @Size(max = 150, message = "displayName cannot exceed 150 characters")
    @Schema(description = "Display name (defaults to name if blank)", example = "Grade 5 (Primary)")
    private String displayName;

    @Schema(description = "Sort order for UI / listings", example = "5")
    private int sortOrder;

    @jakarta.validation.constraints.Min(-3)
    @jakarta.validation.constraints.Max(12)
    @Schema(description = "Numeric level: Pre-Nursery -3, Nursery -2, LKG -1, UKG 0, Class I 1 … XII 12", example = "5")
    private Integer gradeLevel;

    @Schema(description = "School stage; defaults from gradeLevel", example = "PRIMARY")
    private com.vidya.tenantdata.academic.enums.ClassStage stage;

    @Schema(description = "Sections are split into streams (XI–XII)")
    private Boolean hasStreams;
}
