package com.vidya.master.hub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateHubRequest(
        @NotBlank(message = "Hub name is required")
        @Size(max = 200, message = "Hub name cannot exceed 200 characters")
        String name,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State cannot exceed 100 characters")
        String state,

        String region,
        String description,
        UUID leadCommunityId,
        UUID hubManagerUserId,
        String hubManagerName,
        UUID hubSubManagerUserId,
        String hubSubManagerName,
        String contactEmail,
        String contactPhone
) {}
