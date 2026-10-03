package com.vidya.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider — Secret Hardening, Token Types, and Expiry Tests")
class JwtTokenProviderTest {

    private static final String VALID_32_BYTE_SECRET = "12345678901234567890123456789012";
    private static final String SHORT_SECRET = "too-short-secret-1234";

    @Test
    @DisplayName("Startup fails if secret is missing under non-dev/non-test profile")
    void missingSecret_nonDevProfile_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtTokenProvider(env, "", 1800, 604800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vidya.security.jwt.secret must be configured");
    }

    @Test
    @DisplayName("Startup fails if secret is missing and no profile is explicitly active")
    void missingSecret_noActiveProfile_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();

        assertThatThrownBy(() -> new JwtTokenProvider(env, "", 1800, 604800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vidya.security.jwt.secret must be configured");
    }

    @Test
    @DisplayName("Startup fails if secret is missing and env is null")
    void missingSecret_nullEnv_throwsIllegalStateException() {
        assertThatThrownBy(() -> new JwtTokenProvider(null, "", 1800, 604800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vidya.security.jwt.secret must be configured");
    }

    @Test
    @DisplayName("Test profile supplies clearly labeled non-production default when secret is blank")
    void testProfile_blankSecret_usesNonProductionDefault() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        JwtTokenProvider provider = new JwtTokenProvider(env, "", 1800, 604800);
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "test@vidya.io", "USR-1", "SCHOOL_ADMIN");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getTokenType(token)).isEqualTo("ACCESS");
    }

    @Test
    @DisplayName("Startup fails if secret is shorter than 32 bytes (256 bits)")
    void shortSecret_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        assertThatThrownBy(() -> new JwtTokenProvider(env, SHORT_SECRET, 1800, 604800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("Dev and test profiles supply clearly labeled non-production default when secret is blank")
    void devProfile_blankSecret_usesNonProductionDefault() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        JwtTokenProvider provider = new JwtTokenProvider(env, "", 1800, 604800);
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "test@vidya.io", "USR-1", "SCHOOL_ADMIN");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getEmailFromToken(token)).isEqualTo("test@vidya.io");
        assertThat(provider.getTokenType(token)).isEqualTo("ACCESS");
    }

    @Test
    @DisplayName("Valid secret configured starts up successfully and generates ACCESS / REFRESH tokens with type claims")
    void validSecret_generatesCorrectTokenTypes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtTokenProvider provider = new JwtTokenProvider(env, VALID_32_BYTE_SECRET, 1800, 604800);
        UUID userId = UUID.randomUUID();

        String accessToken = provider.generateAccessToken(userId, "admin@vidya.io", "USR-ADMIN", "PLATFORM_SUPER_ADMIN");
        String refreshToken = provider.generateRefreshToken(userId, "admin@vidya.io");

        assertThat(provider.getTokenType(accessToken)).isEqualTo("ACCESS");
        assertThat(provider.isRefreshToken(accessToken)).isFalse();

        assertThat(provider.getTokenType(refreshToken)).isEqualTo("REFRESH");
        assertThat(provider.isRefreshToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("Default access-token expiry is 1800 seconds (30 minutes)")
    void defaultAccessTokenLifetime_is1800Seconds() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        JwtTokenProvider provider = new JwtTokenProvider(env, VALID_32_BYTE_SECRET, 1800, 604800);
        assertThat(provider.getAccessTokenExpirySeconds()).isEqualTo(1800L);
    }
}
