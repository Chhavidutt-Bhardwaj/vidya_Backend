package com.vidya.tenantdata.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to assign a student to a class section for an academic year")
public class AssignStudentRequest {

    @NotNull(message = "academicYearId is required")
    @Schema(description = "UUID of the academic year")
    private UUID academicYearId;

    @NotNull(message = "academicClassId is required")
    @Schema(description = "UUID of the academic class")
    private UUID academicClassId;

    @NotNull(message = "sectionId is required")
    @Schema(description = "UUID of the section")
    private UUID sectionId;

    @Size(max = 50, message = "rollNumber cannot exceed 50 characters")
    @Schema(description = "Assigned roll number within the section", example = "12")
    private String rollNumber;

    @Schema(description = "Placement start date (defaults to today if null)", example = "2026-04-01")
    private LocalDate startDate;
}
