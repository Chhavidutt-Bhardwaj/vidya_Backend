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
}
