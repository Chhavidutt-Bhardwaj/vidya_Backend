package com.vidya.security.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.security.dto.AuthResponse;
import com.vidya.security.dto.LoginRequest;
import com.vidya.security.dto.PlatformUserResponse;
import com.vidya.security.dto.RefreshTokenRequest;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private AuthResponse sampleAuthResponse;
    private PlatformUserResponse sampleUserResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();

        sampleUserResponse = PlatformUserResponse.builder()
                .id(UUID.randomUUID())
                .userCode("USR-ADMIN01")
                .email("admin@vidya.io")
                .firstName("Super")
                .lastName("Admin")
                .fullName("Super Admin")
                .phone("+919999999991")
                .status(UserStatus.ACTIVE)
                .platformRole(PlatformRole.PLATFORM_SUPER_ADMIN)
                .lastLoginAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        sampleAuthResponse = AuthResponse.builder()
                .accessToken("mock-jwt-access-token")
                .refreshToken("mock-jwt-refresh-token")
                .tokenType("Bearer")
                .expiresIn(86400)
                .user(sampleUserResponse)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return tokens and user info")
    void login_Success() throws Exception {
        LoginRequest request = new LoginRequest("admin@vidya.io", "Password@123");

        when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", equalTo("mock-jwt-access-token")))
                .andExpect(jsonPath("$.data.user.email", equalTo("admin@vidya.io")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh should return new tokens")
    void refreshToken_Success() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("mock-jwt-refresh-token");

        when(authService.refreshToken(any(RefreshTokenRequest.class), any(), any())).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", equalTo("mock-jwt-access-token")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout should revoke session")
    void logout_Success() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("mock-jwt-refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.code", equalTo("LOGOUT_SUCCESS")));

        verify(authService).logout(any(RefreshTokenRequest.class), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me should return current user profile")
    void getCurrentUser_Success() throws Exception {
        when(authService.getCurrentUser()).thenReturn(sampleUserResponse);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.email", equalTo("admin@vidya.io")));
    }
}
