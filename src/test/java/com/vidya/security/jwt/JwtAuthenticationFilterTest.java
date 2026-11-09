package com.vidya.security.jwt;

import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.service.PlatformUserDetailsService;
import com.vidya.security.session.service.SessionService;
import com.vidya.security.user.PlatformUserDetails;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — token type, session binding and account state")
class JwtAuthenticationFilterTest {

    @Mock
    private PlatformUserDetailsService userDetailsService;

    @Mock
    private SessionService sessionService;

    @Mock
    private FilterChain filterChain;

    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;
    private UUID userId;
    private UUID sessionId;
    private PlatformUser user;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(
                "vidya-dev-secret-key-change-in-production-min-256-bits-length", 1800, 604800);
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService, sessionService);
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        user = new PlatformUser("USR-1", "admin@vidya.io", "hash", "Admin", "User", null, PlatformRole.SCHOOL_ADMIN);
        user.setId(userId);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String accessToken(UUID sid) {
        return tokenProvider.generateAccessToken(userId, "admin@vidya.io", "USR-1", "SCHOOL_ADMIN", null, null, sid);
    }

    @Test
    @DisplayName("Bearer token with type=REFRESH is rejected and does not set SecurityContext authentication")
    void refreshToken_asBearerToken_isRejected() throws Exception {
        MockHttpServletRequest request = bearer(tokenProvider.generateRefreshToken(userId, "admin@vidya.io", sessionId));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("ACCESS token bound to an active session authenticates with DB-resolved principal")
    void accessToken_withActiveSession_isAccepted() throws Exception {
        when(sessionService.validateAndTouch(sessionId, userId)).thenReturn(true);
        when(userDetailsService.loadPrincipal("admin@vidya.io", sessionId))
                .thenReturn(new PlatformUserDetails(user, null, null, sessionId));

        MockHttpServletRequest request = bearer(accessToken(sessionId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin@vidya.io");
        PlatformUserDetails principal = (PlatformUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getSessionId()).isEqualTo(sessionId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("authenticated responses carry X-Access-Version so the UI can detect a stale token snapshot")
    void accessVersionHeader() throws Exception {
        user.setSchoolId(UUID.randomUUID());
        var access = com.vidya.security.access.SeedRoleMatrix.accessFor(user);
        when(sessionService.validateAndTouch(sessionId, userId)).thenReturn(true);
        when(userDetailsService.loadPrincipal("admin@vidya.io", sessionId))
                .thenReturn(new PlatformUserDetails(user, null, access, sessionId));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(bearer(accessToken(sessionId)), response, filterChain);

        assertThat(response.getHeader(JwtAuthenticationFilter.ACCESS_VERSION_HEADER)).isEqualTo(access.version());
    }

    @Test
    @DisplayName("ACCESS token of a revoked/logged-out session is rejected immediately")
    void accessToken_withEndedSession_isRejected() throws Exception {
        when(sessionService.validateAndTouch(sessionId, userId)).thenReturn(false);

        MockHttpServletRequest request = bearer(accessToken(sessionId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadPrincipal(any(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("ACCESS token without a session id (pre-session token) is rejected")
    void accessToken_withoutSession_isRejected() throws Exception {
        String legacy = tokenProvider.generateAccessToken(userId, "admin@vidya.io", "USR-1", "SCHOOL_ADMIN");

        MockHttpServletRequest request = bearer(legacy);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(sessionService, never()).validateAndTouch(any(), any());
    }

    @Test
    @DisplayName("Suspended account is not authenticated even with a live session")
    void accessToken_suspendedUser_isRejected() throws Exception {
        user.setStatus(UserStatus.SUSPENDED);
        when(sessionService.validateAndTouch(sessionId, userId)).thenReturn(true);
        when(userDetailsService.loadPrincipal("admin@vidya.io", sessionId))
                .thenReturn(new PlatformUserDetails(user, null, null, sessionId));

        MockHttpServletRequest request = bearer(accessToken(sessionId));
        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
