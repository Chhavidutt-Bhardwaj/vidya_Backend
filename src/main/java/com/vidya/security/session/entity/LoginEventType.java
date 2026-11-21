package com.vidya.security.session.entity;

public enum LoginEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    LOGOUT,
    LOGOUT_ALL,
    TOKEN_REFRESHED,
    SESSION_REVOKED,
    TOKEN_REUSE_DETECTED
}
