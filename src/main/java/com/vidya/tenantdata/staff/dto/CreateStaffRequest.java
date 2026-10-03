package com.vidya.tenantdata.staff.dto;

import com.vidya.tenantdata.staff.enums.StaffType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for creating a new Staff record linked to a tenant operational user.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create/onboard a staff member")
public class CreateStaffRequest {

    @NotNull(message = "tenantUserId is required")
    @Schema(description = "UUID of the operational TenantUser record in this tenant's database",
            example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID tenantUserId;

    @Size(max = 100, message = "employeeCode cannot exceed 100 characters")
    @Schema(description = "School's own employee identifier (e.g. EMP-1024). Optional.",
            example = "EMP-1024")
    private String employeeCode;

    @PastOrPresent(message = "joiningDate cannot be in the future")
    @Schema(description = "Date the staff member joined the school", example = "2026-04-01")
    private LocalDate joiningDate;

    @NotNull(message = "staffType is required")
    @Schema(description = "Operational staff category", example = "TEACHER")
    private StaffType staffType;

    @Size(max = 150, message = "designation cannot exceed 150 characters")
    @Schema(description = "Job designation or title (e.g. Senior Teacher)", example = "Senior Teacher")
    private String designation;

    @Size(max = 150, message = "department cannot exceed 150 characters")
    @Schema(description = "Department the staff member belongs to (e.g. Mathematics)",
            example = "Mathematics")
    private String department;
}
