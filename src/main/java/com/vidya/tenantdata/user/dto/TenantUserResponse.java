package com.vidya.tenantdata.user.dto;

import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary response for a tenant operational user")
public class TenantUserResponse {

    @Schema(description = "User unique ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "User code", example = "USR-X7K9P2Q1")
    private String userCode;

    @Schema(description = "Master platform user ID if linked")
    private UUID masterUserId;

    @Schema(description = "User email", example = "teacher.sharma@greenwood.edu.in")
    private String email;

    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;

    @Schema(description = "Full display name", example = "Ramesh Sharma")
    private String fullName;

    @Schema(description = "Operational user type", example = "TEACHER")
    private TenantUserType userType;

    @Schema(description = "Current lifecycle status", example = "ACTIVE")
    private TenantUserStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
