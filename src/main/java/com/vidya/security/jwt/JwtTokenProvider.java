package com.vidya.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    public static final String DEV_TEST_NON_PROD_SECRET = "vidya-dev-test-only-secret-key-do-not-use-in-production-min-256-bits";

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    @Autowired
    public JwtTokenProvider(
            Environment env,
            @Value("${vidya.security.jwt.secret:}") String secret,
            @Value("${vidya.security.jwt.expiry-seconds:1800}") long expirySeconds,
            @Value("${vidya.security.jwt.refresh-expiry-seconds:604800}") long refreshExpirySeconds) {

        String effectiveSecret = secret != null ? secret.trim() : "";
        if (effectiveSecret.isEmpty()) {
            if (env != null && env.acceptsProfiles(Profiles.of("dev", "test"))) {
                log.warn("Using default non-production JWT secret under dev/test profile. DO NOT USE IN PRODUCTION.");
                effectiveSecret = DEV_TEST_NON_PROD_SECRET;
            } else {
                throw new IllegalStateException("Startup failed: vidya.security.jwt.secret must be configured and not empty.");
            }
        }

        byte[] keyBytes = effectiveSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("Startup failed: vidya.security.jwt.secret must be at least 32 bytes (256 bits) long. Current length: " + keyBytes.length);
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiryMs = expirySeconds * 1000L;
        this.refreshTokenExpiryMs = refreshExpirySeconds * 1000L;
    }

    public JwtTokenProvider(String secret, long expirySeconds, long refreshExpirySeconds) {
        this(null, secret, expirySeconds, refreshExpirySeconds);
    }

    public String generateAccessToken(UUID userId, String email, String userCode, String role) {
        return generateAccessToken(userId, email, userCode, role, null, null);
    }

    public String generateAccessToken(UUID userId, String email, String userCode, String role, UUID schoolId) {
        return generateAccessToken(userId, email, userCode, role, schoolId, null);
    }

    public String generateAccessToken(UUID userId, String email, String userCode, String role, UUID schoolId, String tenantCode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        var builder = Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("userCode", userCode)
                .claim("role", role)
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiry);

        if (schoolId != null) {
            builder.claim("schoolId", schoolId.toString());
        }
        if (tenantCode != null && !tenantCode.isBlank()) {
            builder.claim("tenantCode", tenantCode);
        }

        return builder.signWith(signingKey).compact();
    }

    public String generateRefreshToken(UUID userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiryMs);

        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserIdFromToken(String token) {
        String subject = getClaims(token).getSubject();
        return UUID.fromString(subject);
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    public UUID getSchoolIdFromToken(String token) {
        String schoolIdStr = getClaims(token).get("schoolId", String.class);
        return schoolIdStr != null ? UUID.fromString(schoolIdStr) : null;
    }

    public String getTenantCodeFromToken(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.get("tenantCode", String.class) : null;
    }

    public String getTokenType(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.get("type", String.class) : null;
    }

    public boolean isRefreshToken(String token) {
        return "REFRESH".equalsIgnoreCase(getTokenType(token));
    }

    public String getJtiFromToken(String token) {
        return getClaims(token).getId();
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiryMs / 1000L;
    }

    public long getRefreshTokenExpirySeconds() {
        return refreshTokenExpiryMs / 1000L;
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
