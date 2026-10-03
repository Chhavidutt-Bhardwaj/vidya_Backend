package com.vidya.master.workflow.admin.dto;

import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
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
@Schema(description = "Response payload for provisioned initial school administrator")
public class InitialSchoolAdminResponse {

    @Schema(description = "Platform user ID")
    private UUID userId;

    @Schema(description = "User unique code", example = "USR-A1B2C3D4")
    private String userCode;

    @Schema(description = "Admin email address", example = "principal@greenwood.edu.in")
    private String email;

    @Schema(description = "Full name", example = "Suresh Raina")
    private String fullName;

    @Schema(description = "Contact phone", example = "+919876543210")
    private String phone;

    @Schema(description = "Assigned role", example = "SCHOOL_OWNER")
    private PlatformRole role;

    @Schema(description = "Associated school ID")
    private UUID schoolId;

    @Schema(description = "User status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Generated temporary password (only returned on creation)", example = "Tmp#9a8b7c")
    private String temporaryPassword;

    @Schema(description = "Timestamp of creation")
    private Instant createdAt;
}
