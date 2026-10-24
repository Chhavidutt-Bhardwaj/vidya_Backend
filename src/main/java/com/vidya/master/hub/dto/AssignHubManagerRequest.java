package com.vidya.master.hub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignHubManagerRequest(
        @NotNull(message = "Hub Manager User ID is required")
        UUID hubManagerUserId,

        @NotBlank(message = "Hub Manager name is required")
        String hubManagerName,

        UUID hubSubManagerUserId,
        String hubSubManagerName
) {}
