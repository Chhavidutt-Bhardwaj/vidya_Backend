package com.vidya.master.school.dto;

import com.vidya.master.school.entity.SchoolAddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SchoolAddressRequest(
        UUID branchId,

        SchoolAddressType addressType,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
        String addressLine1,

        @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
        String addressLine2,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @NotBlank(message = "Pincode is required")
        @Size(max = 20, message = "Pincode must not exceed 20 characters")
        String pincode,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        @Size(max = 200, message = "Landmark must not exceed 200 characters")
        String landmark,

        BigDecimal latitude,

        BigDecimal longitude,

        Boolean isPrimary
) {}
