package com.vidya.tenantdata.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
 * Request payload for creating a new Student record linked to a tenant user operational identity.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to enroll/create a student")
public class CreateStudentRequest {

    @NotNull(message = "tenantUserId is required")
    @Schema(description = "UUID of the operational TenantUser record", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID tenantUserId;

    @NotBlank(message = "admissionNumber is required")
    @Size(max = 100, message = "admissionNumber cannot exceed 100 characters")
    @Schema(description = "School-assigned admission number (unique within school)", example = "ADM-2026-0042")
    private String admissionNumber;

    @NotNull(message = "admissionDate is required")
    @PastOrPresent(message = "admissionDate cannot be in the future")
    @Schema(description = "Date of admission", example = "2026-06-01")
    private LocalDate admissionDate;
}
