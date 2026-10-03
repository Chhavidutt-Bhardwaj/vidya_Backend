package com.vidya.security.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.UnauthorizedException;
import com.vidya.security.dto.AuthResponse;
import com.vidya.security.dto.LoginRequest;
import com.vidya.security.dto.PlatformUserResponse;
import com.vidya.security.dto.RefreshTokenRequest;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.RefreshToken;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.jwt.JwtTokenProvider;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PlatformUserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SecurityAuditService securityAuditService;

    private JwtTokenProvider jwtTokenProvider;
    private AuthServiceImpl authService;

    private UUID userId;
    private PlatformUser user;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "vidya-dev-secret-key-change-in-production-min-256-bits-length",
                86400,
                604800
        );

        authService = new AuthServiceImpl(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenProvider,
                securityAuditService
        );

        userId = UUID.randomUUID();
        user = new PlatformUser(
                "USR-ADMIN01",
                "admin@vidya.io",
                "hashed_password",
                "Super",
                "Admin",
                "+919999999991",
                PlatformRole.PLATFORM_SUPER_ADMIN
        );
        user.setId(userId);
    }

    @Test
    @DisplayName("login with valid credentials should return AuthResponse with tokens and save refresh token")
    void login_Success() {
        LoginRequest request = new LoginRequest("admin@vidya.io", "Password@123");

        when(userRepository.findByEmailIgnoreCase("admin@vidya.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hashed_password")).thenReturn(true);
        when(userRepository.save(any(PlatformUser.class))).thenReturn(user);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotEmpty();
        assertThat(response.getRefreshToken()).isNotEmpty();
        assertThat(response.getUser().getEmail()).isEqualTo("admin@vidya.io");
        assertThat(response.getUser().getPlatformRole()).isEqualTo(PlatformRole.PLATFORM_SUPER_ADMIN);
        verify(securityAuditService).logLoginSuccess(eq(userId), eq("admin@vidya.io"), any(), any());
    }

    @Test
    @DisplayName("login with wrong password should record failure and throw UnauthorizedException")
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("admin@vidya.io", "WrongPassword");

        when(userRepository.findByEmailIgnoreCase("admin@vidya.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository).save(user);
        verify(securityAuditService).logLoginFailure(eq("admin@vidya.io"), eq("INVALID_CREDENTIALS"), any(), any());
    }

    @Test
    @DisplayName("login with non-existent email should throw UnauthorizedException")
    void login_UnknownEmail_ThrowsException() {
        LoginRequest request = new LoginRequest("unknown@vidya.io", "Password@123");

        when(userRepository.findByEmailIgnoreCase("unknown@vidya.io")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(securityAuditService).logLoginFailure(eq("unknown@vidya.io"), eq("INVALID_CREDENTIALS"), any(), any());
    }

    @Test
    @DisplayName("login with locked user should throw BusinessException FORBIDDEN")
    void login_LockedUser_ThrowsException() {
        user.setLockedUntil(Instant.now().plusSeconds(900));
        LoginRequest request = new LoginRequest("admin@vidya.io", "Password@123");

        when(userRepository.findByEmailIgnoreCase("admin@vidya.io")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("login with inactive user should throw BusinessException FORBIDDEN")
    void login_InactiveUser_ThrowsException() {
        user.setStatus(UserStatus.INACTIVE);
        LoginRequest request = new LoginRequest("admin@vidya.io", "Password@123");

        when(userRepository.findByEmailIgnoreCase("admin@vidya.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hashed_password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("refreshToken with valid refresh token should rotate tokens")
    void refreshToken_Success() {
        String validRefreshToken = jwtTokenProvider.generateRefreshToken(userId, "admin@vidya.io");
        String tokenHash = jwtTokenProvider.hashToken(validRefreshToken);
        RefreshToken storedToken = new RefreshToken(userId, tokenHash, Instant.now().plusSeconds(3600));

        RefreshTokenRequest request = new RefreshTokenRequest(validRefreshToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.refreshToken(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotEmpty();
        assertThat(response.getRefreshToken()).isNotEmpty();
        assertThat(storedToken.isRevoked()).isTrue();
        verify(securityAuditService).logTokenRefresh(eq(userId), eq("admin@vidya.io"), any(), any());
    }

    @Test
    @DisplayName("refreshToken with revoked token should revoke all user tokens (theft detection)")
    void refreshToken_RevokedToken_RevokesAll() {
        String validRefreshToken = jwtTokenProvider.generateRefreshToken(userId, "admin@vidya.io");
        String tokenHash = jwtTokenProvider.hashToken(validRefreshToken);
        RefreshToken storedToken = new RefreshToken(userId, tokenHash, Instant.now().plusSeconds(3600));
        storedToken.setRevoked(true);

        RefreshTokenRequest request = new RefreshTokenRequest(validRefreshToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any());
    }

    @Test
    @DisplayName("logout should revoke refresh token")
    void logout_Success() {
        String validRefreshToken = jwtTokenProvider.generateRefreshToken(userId, "admin@vidya.io");
        String tokenHash = jwtTokenProvider.hashToken(validRefreshToken);
        RefreshToken storedToken = new RefreshToken(userId, tokenHash, Instant.now().plusSeconds(3600));

        RefreshTokenRequest request = new RefreshTokenRequest(validRefreshToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));

        authService.logout(request);

        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);
    }
}
