package com.vidya.tenantdata.staff.dto;

import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
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
 * Detailed representation of a Staff member, including the nested TenantUser and profile.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed representation of a staff member record")
public class StaffDetailResponse {

    @Schema(description = "Staff record UUID")
    private UUID id;

    @Schema(description = "Unique stable staff code (e.g. STF-8A92K3LM)", example = "STF-8A92K3LM")
    private String staffCode;

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

    @Schema(description = "Associated operational user and demographic profile")
    private TenantUserDetailResponse user;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
