package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
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
 * Detailed representation of a Student, including nested operational user and demographic profile.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed representation of a student record")
public class StudentDetailResponse {

    @Schema(description = "Student record UUID", example = "4e34eb06-69d2-4309-8d19-d0fb0672e88b")
    private UUID id;

    @Schema(description = "Unique human-readable student code", example = "STU-ABC12345")
    private String studentCode;

    @Schema(description = "School admission number", example = "ADM-2026-0042")
    private String admissionNumber;

    @Schema(description = "Admission date", example = "2026-06-01")
    private LocalDate admissionDate;

    @Schema(description = "Student lifecycle status", example = "ACTIVE")
    private StudentStatus status;

    @Schema(description = "Associated operational user and demographic profile")
    private TenantUserDetailResponse user;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
