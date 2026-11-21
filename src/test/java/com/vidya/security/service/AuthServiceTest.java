package com.vidya.security.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.UnauthorizedException;
import com.vidya.security.access.AccessScope;
import com.vidya.security.access.SeedRoleMatrix;
import com.vidya.security.access.service.EffectiveAccessService;
import com.vidya.security.dto.AuthResponse;
import com.vidya.security.dto.LoginRequest;
import com.vidya.security.dto.RefreshTokenRequest;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.RefreshToken;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.jwt.JwtTokenProvider;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.repository.RefreshTokenRepository;
import com.vidya.security.session.entity.LoginEventType;
import com.vidya.security.session.entity.SessionStatus;
import com.vidya.security.session.entity.UserSession;
import com.vidya.security.session.service.ClientInfo;
import com.vidya.security.session.service.LoginHistoryService;
import com.vidya.security.session.service.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private PlatformUserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private UserPermissionMatrixService userPermissionMatrixService;
    @Mock private SessionService sessionService;
    @Mock private LoginHistoryService loginHistoryService;
    @Mock private EffectiveAccessService effectiveAccessService;

    private JwtTokenProvider jwtTokenProvider;
    private AuthServiceImpl authService;
    private UUID userId;
    private PlatformUser user;
    private UserSession session;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "vidya-dev-secret-key-change-in-production-min-256-bits-length", 86400, 604800);

        authService = new AuthServiceImpl(userRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider,
                securityAuditService, userPermissionMatrixService, null, sessionService, loginHistoryService,
                effectiveAccessService);

        userId = UUID.randomUUID();
        user = new PlatformUser("USR-ADMIN01", "admin@vidya.io", "hashed_password", "Super", "Admin",
                "+919999999991", PlatformRole.PLATFORM_SUPER_ADMIN);
        user.setId(userId);

        session = new UserSession(userId, null, AccessScope.PLATFORM, ClientInfo.of("10.0.0.1", "Mozilla/5.0 Chrome/120"),
                Instant.now().plusSeconds(604800));

        lenient().when(effectiveAccessService.resolveFresh(any(PlatformUser.class)))
                .thenAnswer(inv -> SeedRoleMatrix.accessFor(inv.getArgument(0)));
        lenient().when(effectiveAccessService.getAccess(any(PlatformUser.class)))
                .thenAnswer(inv -> SeedRoleMatrix.accessFor(inv.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("login opens a session, binds both tokens to it and returns DB-resolved permissions")
    void login_Success() {
        when(userRepository.findByEmailIgnoreCase("admin@vidya.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hashed_password")).thenReturn(true);
        when(userRepository.save(any(PlatformUser.class))).thenReturn(user);
        when(sessionService.open(eq(user), any(ClientInfo.class), any(Instant.class))).thenReturn(session);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("admin@vidya.io", "Password@123"));

        assertThat(jwtTokenProvider.getSessionIdFromToken(response.getAccessToken())).isEqualTo(session.getId());
        assertThat(jwtTokenProvider.getSessionIdFromToken(response.getRefreshToken())).isEqualTo(session.getId());
        assertThat(response.getUser().getSessionId()).isEqualTo(session.getId());
        assertThat(response.getUser().getPlatformRole()).isEqualTo(PlatformRole.PLATFORM_SUPER_ADMIN);
        assertThat(response.getUser().getPermissions()).contains("ACCESS_CONTROL_MANAGE", "TENANT_PROVISION");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getSessionId()).isEqualTo(session.getId());
        verify(securityAuditService).logLoginSuccess(eq(userId), eq("admin@vidya.io"), any(), any());
        verify(loginHistoryService).record(eq(user), eq("admin@vidya.io"), eq(LoginEventType.LOGIN_SUCCESS), isNull(),
                eq(session.getId()), any(), any());
    }

    @Test
    @DisplayName("wrong password records the failure (counter + history) and throws UnauthorizedException")
    void login_WrongPassword_ThrowsException() {
        when(userRepository.findByEmailIgnoreCase("admin@vidya.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@vidya.io", "WrongPassword")))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository).save(user);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(securityAuditService).logLoginFailure(eq("admin@vidya.io"), eq("INVALID_CREDENTIALS"), any(), any());
        verify(loginHistoryService).record(eq(user), eq("admin@vidya.io"), eq(LoginEventType.LOGIN_FAILED),
                eq("INVALID_CREDENTIALS"), isNull(), any(), any());
        verify(sessionService, never()).open(any(), any(), any());
    }

    @Test
    @DisplayName("unknown email throws UnauthorizedException and is recorded without a user")
    void login_UnknownEmail_ThrowsException() {
        when(userRepository.findByEmailIgnoreCase("unknown@vidya.io")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@vidya.io", "Password@123")))
                .isInstanceOf(UnauthorizedException.class);

        verify(securityAuditService).logLoginFailure(eq("unknown@vidya.io"), eq("INVALID_CREDENTIALS"), any(), any());
        verify(loginHistoryService).record(isNull(), eq("unknown@vidya.io"), eq(LoginEventType.LOGIN_FAILED),
                eq("UNKNOWN_EMAIL"), isNull(), any(), any());
    }

    @Test
    @DisplayName("locked user cannot log in")
    void login_LockedUser_ThrowsException() {
        user.setLockedUntil(Instant.now().plusSeconds(900));
        when(userRepository.findByEmailIgnoreCase("admin@vidya.io")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@vidya.io", "Password@123")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("inactive user cannot log in")
    void login_InactiveUser_ThrowsException() {
        user.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmailIgnoreCase("admin@vidya.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hashed_password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@vidya.io", "Password@123")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("refresh rotates tokens inside the same session and slides its expiry")
    void refreshToken_Success() {
        String refresh = jwtTokenProvider.generateRefreshToken(userId, "admin@vidya.io", session.getId());
        String hash = jwtTokenProvider.hashToken(refresh);
        RefreshToken stored = new RefreshToken(userId, hash, Instant.now().plusSeconds(3600), session.getId());

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(sessionService.find(session.getId())).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.refreshToken(new RefreshTokenRequest(refresh));

        assertThat(stored.isRevoked()).isTrue();
        assertThat(jwtTokenProvider.getSessionIdFromToken(response.getAccessToken())).isEqualTo(session.getId());
        verify(sessionService).markRefreshed(eq(session), any(Instant.class));
        verify(securityAuditService).logTokenRefresh(eq(userId), eq("admin@vidya.io"), any(), any());
    }

    @Test
    @DisplayName("refresh is rejected once the session has been ended (e.g. remote sign-out)")
    void refreshToken_EndedSession_Rejected() {
        session.end(SessionStatus.REVOKED, "REVOKED_BY_ADMIN", null);
        String refresh = jwtTokenProvider.generateRefreshToken(userId, "admin@vidya.io", session.getId());
        String hash = jwtTokenProvider.hashToken(refresh);
        when(refreshTokenRepository.findByTokenHash(hash))
                .thenReturn(Optional.of(new RefreshToken(userId, hash, Instant.now().plusSeconds(3600), session.getId())));
        when(sessionService.find(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest(refresh)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Session has ended");
    }

    @Test
    @DisplayName("refresh token issued before sessions existed (no session_id) is rejected")
    void refreshToken_LegacyTokenWithoutSession_Rejected() {
        String refresh = jwtTokenProvider.generateRefreshToken(userId, "admin@vidya.io");
        String hash = jwtTokenProvider.hashToken(refresh);
        when(refreshTokenRepository.findByTokenHash(hash))
                .thenReturn(Optional.of(new RefreshToken(userId, hash, Instant.now().plusSeconds(3600))));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest(refresh)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("reuse of a revoked refresh token ends every session of the user (theft detection)")
    void refreshToken_RevokedToken_RevokesAll() {
        String refresh = jwtTokenProvider.generateRefreshToken(userId, "admin@vidya.io", session.getId());
        String hash = jwtTokenProvider.hashToken(refresh);
        RefreshToken stored = new RefreshToken(userId, hash, Instant.now().plusSeconds(3600), session.getId());
        stored.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest(refresh)))
                .isInstanceOf(UnauthorizedException.class);

        verify(sessionService).endAllForUser(eq(userId), eq(SessionStatus.REVOKED), eq(SessionService.REASON_TOKEN_REUSE), isNull(), isNull());
        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any());
        verify(loginHistoryService).record(any(), any(), eq(LoginEventType.TOKEN_REUSE_DETECTED), any(), eq(session.getId()), any(), any());
    }

    @Test
    @DisplayName("an access token is not accepted as a refresh token")
    void refreshToken_withAccessToken_throwsUnauthorizedException() {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, "admin@vidya.io", "USR-ADMIN01", "PLATFORM_SUPER_ADMIN");

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest(accessToken)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid token type");

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("logout revokes the refresh token and ends its session")
    void logout_Success() {
        String refresh = jwtTokenProvider.generateRefreshToken(userId, "admin@vidya.io", session.getId());
        String hash = jwtTokenProvider.hashToken(refresh);
        RefreshToken stored = new RefreshToken(userId, hash, Instant.now().plusSeconds(3600), session.getId());
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.logout(new RefreshTokenRequest(refresh));

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
        verify(sessionService).end(eq(session.getId()), eq(SessionStatus.LOGGED_OUT), eq(SessionService.REASON_LOGOUT), eq(userId));
        verify(loginHistoryService).record(eq(user), eq("admin@vidya.io"), eq(LoginEventType.LOGOUT), isNull(),
                eq(session.getId()), any(), any());
    }
}
