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
}
