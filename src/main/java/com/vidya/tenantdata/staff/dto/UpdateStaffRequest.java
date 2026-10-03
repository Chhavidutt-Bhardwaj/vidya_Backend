package com.vidya.tenantdata.staff.dto;

import com.vidya.tenantdata.staff.enums.StaffType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Request payload for updating an existing Staff record.
 *
 * <p>Immutable fields — {@code id}, {@code staffCode}, {@code tenantUserId} — are not accepted here.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update staff operational details")
public class UpdateStaffRequest {

    @Size(max = 100, message = "employeeCode cannot exceed 100 characters")
    @Schema(description = "Updated school employee code", example = "EMP-2048")
    private String employeeCode;

    @PastOrPresent(message = "joiningDate cannot be in the future")
    @Schema(description = "Updated joining date", example = "2026-07-01")
    private LocalDate joiningDate;

    @Schema(description = "Updated staff type", example = "ADMINISTRATIVE")
    private StaffType staffType;

    @Size(max = 150, message = "designation cannot exceed 150 characters")
    @Schema(description = "Updated designation", example = "Head of Department")
    private String designation;

    @Size(max = 150, message = "department cannot exceed 150 characters")
    @Schema(description = "Updated department", example = "Science")
    private String department;
}
