package com.vidya.tenantdata.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create a section for an academic year and class")
public class CreateSectionRequest {

    @NotNull(message = "academicYearId is required")
    @Schema(description = "UUID of the academic year")
    private UUID academicYearId;

    @NotNull(message = "academicClassId is required")
    @Schema(description = "UUID of the academic class")
    private UUID academicClassId;

    @NotBlank(message = "code is required")
    @Size(max = 50, message = "code cannot exceed 50 characters")
    @Schema(description = "Section code (e.g. A, B, SEC-A)", example = "A")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name cannot exceed 100 characters")
    @Schema(description = "Section name", example = "Section A")
    private String name;

    @Positive(message = "capacity must be greater than zero")
    @Schema(description = "Maximum student capacity (optional)", example = "40")
    private Integer capacity;
}
