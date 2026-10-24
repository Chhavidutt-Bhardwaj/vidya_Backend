package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new core community entity.
 * Section details (address, bank, personal, etc.) are populated via their respective endpoints.
 */
public record CreateCommunityRequest(
        @NotBlank(message = "Community name is required")
        @Size(max = 200, message = "Community name must not exceed 200 characters")
        String name,

        String description,

        @NotNull(message = "Community type is required")
        CommunityType type
) {}
