package com.vidya.security.service;

import com.vidya.security.dto.AuthResponse;
import com.vidya.security.dto.LoginRequest;
import com.vidya.security.dto.PlatformUserResponse;
import com.vidya.security.dto.RefreshTokenRequest;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse login(LoginRequest request, String ipAddress, String correlationId);

    AuthResponse refreshToken(RefreshTokenRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String correlationId);

    void logout(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request, String ipAddress, String correlationId);

    PlatformUserResponse getCurrentUser();

    // ---- session-aware variants (client = IP + user agent) ----

    AuthResponse login(LoginRequest request, com.vidya.security.session.service.ClientInfo client, String correlationId);

    AuthResponse refreshToken(RefreshTokenRequest request, com.vidya.security.session.service.ClientInfo client, String correlationId);

    void logout(RefreshTokenRequest request, com.vidya.security.session.service.ClientInfo client, String correlationId);

    /** Ends every session of the current user on every device. */
    void logoutAll(com.vidya.security.session.service.ClientInfo client, String correlationId);
}
