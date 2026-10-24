package com.vidya.master.hub.dto;

import com.vidya.master.hub.entity.HubStatus;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateHubRequest(
        @Size(max = 200, message = "Hub name cannot exceed 200 characters")
        String name,

        String state,
        String region,
        String description,
        HubStatus status,
        UUID leadCommunityId,
        UUID hubManagerUserId,
        String hubManagerName,
        UUID hubSubManagerUserId,
        String hubSubManagerName,
        String contactEmail,
        String contactPhone
) {}
