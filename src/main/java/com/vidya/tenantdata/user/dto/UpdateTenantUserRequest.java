package com.vidya.tenantdata.user.dto;

import com.vidya.tenantdata.user.entity.TenantUserType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to update tenant operational user and profile")
public class UpdateTenantUserRequest {

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    @Schema(description = "Phone / Mobile number", example = "+919876543210")
    private String phone;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(description = "First name", example = "Ramesh")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(description = "Last name", example = "Sharma")
    private String lastName;

    @Schema(description = "User operational type (ADMIN, STAFF, TEACHER, STUDENT, PARENT)", example = "TEACHER")
    private TenantUserType userType;

    @Schema(description = "Gender", example = "MALE")
    private String gender;

    @Schema(description = "Date of Birth", example = "1985-05-15")
    private LocalDate dateOfBirth;

    @Schema(description = "Address line 1", example = "42, MG Road")
    private String addressLine1;

    @Schema(description = "Address line 2", example = "Koramangala")
    private String addressLine2;

    @Schema(description = "City", example = "Bengaluru")
    private String city;

    @Schema(description = "State", example = "Karnataka")
    private String state;

    @Schema(description = "Postal code", example = "560001")
    private String postalCode;

    @Schema(description = "Emergency contact name", example = "Suresh Sharma")
    private String emergencyContactName;

    @Schema(description = "Emergency contact phone", example = "+919876543299")
    private String emergencyContactPhone;
}
