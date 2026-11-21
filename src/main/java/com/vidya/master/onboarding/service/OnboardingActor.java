package com.vidya.master.onboarding.service;

import com.vidya.common.util.SecurityUtils;
import com.vidya.master.onboarding.entity.PerformerType;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.user.PlatformUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the actor of an onboarding action for audit purposes.
 *
 * <p>The actor is taken <strong>exclusively</strong> from the authenticated
 * {@link org.springframework.security.core.context.SecurityContext}. Identity fields supplied in
 * request bodies (e.g. {@code approvedBy}, {@code confirmedBy}) are never trusted for auditing.
 * When no user is authenticated (event listeners, background work), the action is attributed
 * to {@link PerformerType#SYSTEM} with a {@code null} actor id.
 */
public final class OnboardingActor {

    private static final Set<PlatformRole> COMMUNITY_ROLES = EnumSet.of(
            PlatformRole.COMMUNITY_ADMIN,
            PlatformRole.COMMUNITY_MANAGER,
            PlatformRole.COMMUNITY_ROLE_MANAGER,
            PlatformRole.FIELD_AGENT);

    private OnboardingActor() {
    }

    /** Authenticated platform user id, or {@code null} for system-initiated actions. */
    public static UUID currentUserId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }

    /** Performer classification derived from the authenticated principal's role. */
    public static PerformerType currentPerformerType() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PlatformUserDetails principal)) {
            return currentUserId() != null ? PerformerType.PLATFORM_USER : PerformerType.SYSTEM;
        }
        return COMMUNITY_ROLES.contains(principal.getPlatformRole())
                ? PerformerType.COMMUNITY_MEMBER
                : PerformerType.PLATFORM_USER;
    }
}
