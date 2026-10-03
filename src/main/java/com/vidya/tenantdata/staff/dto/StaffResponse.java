package com.vidya.tenantdata.staff.dto;

import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
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
 * Summary DTO for Staff listings and search results.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary representation of a staff member")
public class StaffResponse {

    @Schema(description = "Staff record UUID")
    private UUID id;

    @Schema(description = "Unique stable staff code (e.g. STF-8A92K3LM)", example = "STF-8A92K3LM")
    private String staffCode;

    @Schema(description = "UUID of the associated TenantUser operational identity")
    private UUID tenantUserId;

    @Schema(description = "School's own employee identifier", example = "EMP-1024")
    private String employeeCode;

    @Schema(description = "Staff type/category", example = "TEACHER")
    private StaffType staffType;

    @Schema(description = "Current employment status", example = "ACTIVE")
    private EmploymentStatus employmentStatus;

    @Schema(description = "Job designation", example = "Senior Teacher")
    private String designation;

    @Schema(description = "Department", example = "Mathematics")
    private String department;

    @Schema(description = "Joining date", example = "2026-04-01")
    private LocalDate joiningDate;

    @Schema(description = "Staff full name from operational profile", example = "Priya Sharma")
    private String fullName;

    @Schema(description = "Staff email from operational user", example = "priya.sharma@school.edu.in")
    private String email;

    @Schema(description = "Staff phone from operational user", example = "+919876543210")
    private String phone;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
