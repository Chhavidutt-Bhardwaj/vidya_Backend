package com.vidya.security.dto;

import com.vidya.security.access.AccessScope;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUserResponse {
    private UUID id;
    private String userCode;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private UserStatus status;
    private PlatformRole platformRole;
    private UUID schoolId;
    private String tenantCode;
    /** Account scope: PLATFORM, SCHOOL, SCHOOL_PORTAL or COMMUNITY. */
    private AccessScope accountScope;
    /** Every role currently held: account-type role + assigned roles (global or school custom). */
    private Set<String> roles;
    /** Effective permission codes (DB-resolved, after overrides and subscription-feature gating). */
    private Set<String> permissions;
    /** Subscription features enabled for the user's school (empty for platform/community users). */
    private Set<String> enabledFeatures;
    /** Server-side session this token pair belongs to. */
    private UUID sessionId;

    /** Row-level scope per area, e.g. {"STUDENT": "OWN", "ATTENDANCE": "ALL"}. Same as the token's "ds" claim. */
    private java.util.Map<String, String> dataScopes;

    /** Fingerprint of this access; equals the token's "av" claim and the X-Access-Version header while fresh. */
    private String accessVersion;
    /** Universal module/section code -> granted action codes matrix */
    private Map<Integer, Set<Integer>> permissionsMatrix;
    private Instant lastLoginAt;
    private Instant createdAt;
}
