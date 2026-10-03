package com.vidya.tenantdata.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Payload to update an academic class")
public class UpdateAcademicClassRequest {

    @Size(max = 100, message = "name cannot exceed 100 characters")
    @Schema(description = "Updated class name", example = "Class 5")
    private String name;

    @Size(max = 150, message = "displayName cannot exceed 150 characters")
    @Schema(description = "Updated display name", example = "Grade 5 (Primary)")
    private String displayName;

    @Schema(description = "Updated sort order", example = "5")
    private Integer sortOrder;
}
