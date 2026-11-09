package com.vidya.security.session.entity;

public enum SessionStatus {
    ACTIVE,
    /** Ended by the user (logout / logout-all). */
    LOGGED_OUT,
    /** Ended by an admin, the session limit, token-reuse detection, or an account change. */
    REVOKED,
    /** Passed its expiry without being refreshed. */
    EXPIRED
}
