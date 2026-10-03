package com.vidya.common.util;

import com.vidya.security.user.PlatformUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared security utility to resolve user details and identifiers from the current SecurityContext.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    /**
     * Resolves the current authenticated user's UUID from the SecurityContext.
     *
     * @return Optional containing the authenticated user's UUID, or empty if unauthenticated or not resolvable.
     */
    public static Optional<UUID> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof PlatformUserDetails platformUserDetails) {
            return Optional.ofNullable(platformUserDetails.getUserId());
        }

        if (principal instanceof UUID uuid) {
            return Optional.of(uuid);
        }

        if (principal instanceof String str) {
            try {
                return Optional.of(UUID.fromString(str));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}
