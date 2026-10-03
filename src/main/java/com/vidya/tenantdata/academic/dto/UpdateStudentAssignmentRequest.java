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
@Schema(description = "Payload to update roll number or dates for a student assignment")
public class UpdateStudentAssignmentRequest {

    @Size(max = 50, message = "rollNumber cannot exceed 50 characters")
    @Schema(description = "Updated roll number", example = "14")
    private String rollNumber;

    @Schema(description = "Updated start date", example = "2026-04-01")
    private LocalDate startDate;

    @Schema(description = "Updated end date", example = "2027-03-31")
    private LocalDate endDate;
}
