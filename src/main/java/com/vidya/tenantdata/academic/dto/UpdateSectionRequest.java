package com.vidya.tenantdata.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
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
@Schema(description = "Payload to update a section")
public class UpdateSectionRequest {

    @Size(max = 100, message = "name cannot exceed 100 characters")
    @Schema(description = "Updated section name", example = "Section A")
    private String name;

    @Positive(message = "capacity must be greater than zero")
    @Schema(description = "Updated student capacity", example = "45")
    private Integer capacity;
}
