package com.vidya.tenantdata.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create an academic year")
public class CreateAcademicYearRequest {

    @NotBlank(message = "code is required")
    @Size(max = 50, message = "code cannot exceed 50 characters")
    @Schema(description = "Unique code for the academic year", example = "AY2026-27")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name cannot exceed 100 characters")
    @Schema(description = "Descriptive name", example = "Academic Year 2026-2027")
    private String name;

    @NotNull(message = "startDate is required")
    @Schema(description = "Start date", example = "2026-04-01")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    @Schema(description = "End date", example = "2027-03-31")
    private LocalDate endDate;

    @Schema(description = "Whether this is immediately set as current operational year", example = "false")
    private boolean isCurrent;
}
