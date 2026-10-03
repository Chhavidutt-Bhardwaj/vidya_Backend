package com.vidya.tenantdata.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tenant user profile data")
public class TenantUserProfileDto {

    private UUID id;
    private String firstName;
    private String lastName;
    private String displayName;
    private String gender;
    private LocalDate dateOfBirth;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String emergencyContactName;
    private String emergencyContactPhone;
}
