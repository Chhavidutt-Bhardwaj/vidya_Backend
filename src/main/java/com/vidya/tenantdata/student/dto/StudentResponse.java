package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Summary DTO for Student listings and search results.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary representation of a student")
public class StudentResponse {

    @Schema(description = "Student record UUID", example = "4e34eb06-69d2-4309-8d19-d0fb0672e88b")
    private UUID id;

    @Schema(description = "Unique human-readable student code", example = "STU-ABC12345")
    private String studentCode;

    @Schema(description = "UUID of the associated TenantUser operational identity", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID tenantUserId;

    @Schema(description = "School admission number", example = "ADM-2026-0042")
    private String admissionNumber;

    @Schema(description = "Admission date", example = "2026-06-01")
    private LocalDate admissionDate;

    @Schema(description = "Student status", example = "ACTIVE")
    private StudentStatus status;

    @Schema(description = "Student full name from operational profile", example = "Aarav Sharma")
    private String fullName;

    @Schema(description = "Student email from operational user", example = "aarav.sharma@school.edu.in")
    private String email;

    @Schema(description = "Student phone from operational user", example = "+919876543210")
    private String phone;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
