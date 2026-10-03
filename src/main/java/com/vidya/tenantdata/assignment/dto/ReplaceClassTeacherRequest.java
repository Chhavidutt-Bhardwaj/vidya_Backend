package com.vidya.tenantdata.assignment.dto;

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

/**
 * Request payload to replace the current class teacher with a new teacher.
 *
 * <p>The existing ACTIVE assignment will be COMPLETED and a new ACTIVE assignment created
 * atomically within a single tenant transaction.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to replace the current class teacher with a new teacher")
public class ReplaceClassTeacherRequest {

    @NotNull(message = "newStaffId is required")
    @Schema(description = "UUID of the new Staff member (teacher) to assign", example = "b1aabc99-9c0b-4ef8-bb6d-6bb9bd380b22")
    private UUID newStaffId;

    @Schema(description = "Effective date of the replacement (defaults to today)", example = "2026-06-01")
    private LocalDate effectiveDate;

    @Size(max = 1000, message = "notes must not exceed 1000 characters")
    @Schema(description = "Notes for the replacement (applied to the new assignment)", example = "Mid-year transfer due to resignation of previous teacher")
    private String notes;
}
