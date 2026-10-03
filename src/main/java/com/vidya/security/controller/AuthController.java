package com.vidya.security.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.correlation.MdcKeys;
import com.vidya.security.dto.AuthResponse;
import com.vidya.security.dto.LoginRequest;
import com.vidya.security.dto.PlatformUserResponse;
import com.vidya.security.dto.RefreshTokenRequest;
import com.vidya.security.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Platform authentication and session management APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate platform user and issue JWT tokens")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account locked or inactive")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        String ip = servletRequest.getRemoteAddr();
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);

        AuthResponse response = authService.login(request, ip, correlationId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.LOGIN_SUCCESS,
                "Authentication successful",
                response
        ));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT access token using a valid refresh token")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid, expired, or revoked refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest servletRequest) {

        String ip = servletRequest.getRemoteAddr();
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);

        AuthResponse response = authService.refreshToken(request, ip, correlationId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.TOKEN_REFRESHED,
                "Token refreshed successfully",
                response
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke refresh token and logout the user session")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest servletRequest) {

        String ip = servletRequest.getRemoteAddr();
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);

        authService.logout(request, ip, correlationId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.LOGOUT_SUCCESS,
                "Logged out successfully"
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated platform user details", security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile retrieved successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<ApiResponse<PlatformUserResponse>> getCurrentUser() {
        PlatformUserResponse response = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SUCCESS,
                "Current user profile retrieved successfully",
                response
        ));
    }
}
