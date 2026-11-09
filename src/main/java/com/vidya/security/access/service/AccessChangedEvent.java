package com.vidya.security.access.service;

import java.util.UUID;

/**
 * Published whenever something that feeds effective permissions changes (roles, grants,
 * assignments, overrides, school features). Cached access is evicted after the transaction
 * commits. {@code userId == null} means "evict everyone".
 */
public record AccessChangedEvent(UUID userId, String reason) {

    public static AccessChangedEvent all(String reason) {
        return new AccessChangedEvent(null, reason);
    }

    public static AccessChangedEvent user(UUID userId, String reason) {
        return new AccessChangedEvent(userId, reason);
    }
}
