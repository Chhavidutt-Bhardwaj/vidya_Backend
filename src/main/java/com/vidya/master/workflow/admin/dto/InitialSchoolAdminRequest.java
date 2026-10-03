package com.vidya.master.workflow.admin.dto;

import com.vidya.security.entity.PlatformRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for provisioning the initial school administrator")
public class InitialSchoolAdminRequest {

    @Schema(description = "Admin email address (if null, resolved from onboarding contact data or school)", example = "principal@greenwood.edu.in")
    private String email;

    @Schema(description = "First name", example = "Suresh")
    private String firstName;

    @Schema(description = "Last name", example = "Raina")
    private String lastName;

    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;

    @Schema(description = "Role to assign (SCHOOL_OWNER or SCHOOL_ADMIN)", example = "SCHOOL_OWNER")
    private PlatformRole role;

    @Schema(description = "Custom initial password (optional, auto-generated if null)", example = "Admin@Pass123")
    private String password;
}
