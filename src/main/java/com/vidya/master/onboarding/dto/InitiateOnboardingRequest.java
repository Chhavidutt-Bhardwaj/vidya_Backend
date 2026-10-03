package com.vidya.master.onboarding.dto;

import com.vidya.master.onboarding.entity.OnboardingSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request payload for starting a new school onboarding wizard.
 */
public record InitiateOnboardingRequest(
        OnboardingSource source,

        UUID communityId,

        UUID communityMemberId,

        @NotBlank(message = "School name is required")
        @Size(max = 300, message = "School name must not exceed 300 characters")
        String schoolName,

        @NotBlank(message = "School contact email is required")
        @Email(message = "Contact email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 20, message = "Phone must not exceed 20 characters")
        String phone
) {}
