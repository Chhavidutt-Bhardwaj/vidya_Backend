package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.GuardianRelationshipType;
import com.vidya.tenantdata.student.entity.GuardianStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update student guardian details")
public class UpdateStudentGuardianRequest {

    @Size(max = 150, message = "Guardian name cannot exceed 150 characters")
    @Schema(description = "Guardian full name", example = "Ramesh Sharma")
    private String guardianName;

    @Schema(description = "Relationship type to student", example = "FATHER")
    private GuardianRelationshipType relationshipType;

    @Size(max = 30, message = "Phone number cannot exceed 30 characters")
    @Schema(description = "Contact phone number", example = "+919876543210")
    private String phone;

    @Size(max = 255, message = "Email cannot exceed 255 characters")
    @Schema(description = "Email address", example = "ramesh.sharma@example.com")
    private String email;

    @Size(max = 100, message = "Occupation cannot exceed 100 characters")
    @Schema(description = "Occupation", example = "Software Engineer")
    private String occupation;

    @Schema(description = "Annual income", example = "1200000.00")
    private BigDecimal annualIncome;

    @Size(max = 255, message = "Address line 1 cannot exceed 255 characters")
    @Schema(description = "Address line 1", example = "123 Green Avenue")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 cannot exceed 255 characters")
    @Schema(description = "Address line 2", example = "Opposite Central Park")
    private String addressLine2;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    @Schema(description = "City", example = "New Delhi")
    private String city;

    @Size(max = 100, message = "State cannot exceed 100 characters")
    @Schema(description = "State", example = "Delhi")
    private String state;

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    @Schema(description = "Postal code", example = "110001")
    private String postalCode;

    @Schema(description = "Whether primary guardian", example = "true")
    private Boolean isPrimary;

    @Schema(description = "Whether emergency contact", example = "true")
    private Boolean isEmergencyContact;

    @Schema(description = "Status of guardian record", example = "ACTIVE")
    private GuardianStatus status;
}
