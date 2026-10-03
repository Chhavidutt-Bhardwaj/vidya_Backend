package com.vidya.tenantdata.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Payload to update an academic year")
public class UpdateAcademicYearRequest {

    @Size(max = 100, message = "name cannot exceed 100 characters")
    @Schema(description = "Updated name", example = "Academic Year 2026-2027")
    private String name;

    @Schema(description = "Updated start date", example = "2026-04-01")
    private LocalDate startDate;

    @Schema(description = "Updated end date", example = "2027-03-31")
    private LocalDate endDate;
}
