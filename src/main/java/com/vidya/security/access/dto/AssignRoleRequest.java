package com.vidya.security.access.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Give a user an extra role, optionally time-boxed (e.g. acting principal for a term). */
public record AssignRoleRequest(
        @NotNull UUID roleId,
        Instant validFrom,
        Instant validUntil,
        String notes
) {}
