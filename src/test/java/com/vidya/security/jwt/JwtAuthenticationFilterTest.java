package com.vidya.security.jwt;

import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.service.PlatformUserDetailsService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — Token Type Rejection Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private PlatformUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(
                "vidya-dev-secret-key-change-in-production-min-256-bits-length",
                1800,
                604800
        );
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Bearer token with type=REFRESH is rejected and does not set SecurityContext authentication")
    void refreshToken_asBearerToken_isRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        String refreshToken = tokenProvider.generateRefreshToken(userId, "admin@vidya.io");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer token with type=ACCESS is accepted and sets SecurityContext authentication")
    void accessToken_asBearerToken_isAccepted() throws Exception {
        UUID userId = UUID.randomUUID();
        String accessToken = tokenProvider.generateAccessToken(userId, "admin@vidya.io", "USR-1", "SCHOOL_ADMIN");

        PlatformUser user = new PlatformUser("USR-1", "admin@vidya.io", "hash", "Admin", "User", null, PlatformRole.SCHOOL_ADMIN);
        user.setId(userId);
        PlatformUserDetails userDetails = new PlatformUserDetails(user);

        when(userDetailsService.loadUserByUsername("admin@vidya.io")).thenReturn(userDetails);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin@vidya.io");
        verify(filterChain).doFilter(request, response);
    }
}
