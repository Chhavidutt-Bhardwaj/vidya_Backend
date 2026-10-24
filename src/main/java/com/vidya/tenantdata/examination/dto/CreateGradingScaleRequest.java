package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create or configure a grading scale")
public class CreateGradingScaleRequest {

    @NotBlank(message = "Grading scale name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    @Schema(description = "Scale name", example = "CBSE 9-Point Scale")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Schema(description = "Scale description", example = "Official CBSE Secondary grading framework")
    private String description;

    @Schema(description = "Set as default grading scale for new exams", example = "true")
    private boolean isDefault;

    @Valid
    @NotEmpty(message = "At least one grade tier must be defined")
    @Schema(description = "List of grading tiers")
    private List<GradingScaleTierDto> tiers;
}
