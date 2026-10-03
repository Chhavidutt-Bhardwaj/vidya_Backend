package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating an existing community.
 */
public record UpdateCommunityRequest(
        @NotBlank(message = "Community name is required")
        @Size(max = 200, message = "Community name must not exceed 200 characters")
        String name,

        String description,

        @NotNull(message = "Community type is required")
        CommunityType type,

        @Email(message = "Contact email must be valid")
        @Size(max = 255, message = "Contact email must not exceed 255 characters")
        String contactEmail,

        @Size(max = 20, message = "Contact phone must not exceed 20 characters")
        String contactPhone,

        @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
        String addressLine1,

        @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
        String addressLine2,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @Size(max = 20, message = "Pincode must not exceed 20 characters")
        String pincode,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country
) {}
