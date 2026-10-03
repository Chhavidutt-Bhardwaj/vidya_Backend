package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.GuardianRelationshipType;
import com.vidya.tenantdata.student.entity.GuardianStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Student guardian response")
public class StudentGuardianResponse {

    @Schema(description = "Guardian UUID")
    private UUID id;

    @Schema(description = "Guardian business code", example = "GRD-A1B2C3D4")
    private String guardianCode;

    @Schema(description = "Student UUID")
    private UUID studentId;

    @Schema(description = "Guardian full name", example = "Ramesh Sharma")
    private String guardianName;

    @Schema(description = "Relationship type", example = "FATHER")
    private GuardianRelationshipType relationshipType;

    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;

    @Schema(description = "Email address", example = "ramesh.sharma@example.com")
    private String email;

    @Schema(description = "Occupation", example = "Software Engineer")
    private String occupation;

    @Schema(description = "Annual income", example = "1200000.00")
    private BigDecimal annualIncome;

    @Schema(description = "Address line 1", example = "123 Green Avenue")
    private String addressLine1;

    @Schema(description = "Address line 2", example = "Opposite Central Park")
    private String addressLine2;

    @Schema(description = "City", example = "New Delhi")
    private String city;

    @Schema(description = "State", example = "Delhi")
    private String state;

    @Schema(description = "Postal code", example = "110001")
    private String postalCode;

    @Schema(description = "Whether primary guardian", example = "true")
    private boolean isPrimary;

    @Schema(description = "Whether emergency contact", example = "true")
    private boolean isEmergencyContact;

    @Schema(description = "Status of guardian record", example = "ACTIVE")
    private GuardianStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
