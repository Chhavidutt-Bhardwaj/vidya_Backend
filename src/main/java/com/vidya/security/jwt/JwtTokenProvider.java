package com.vidya.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProvider(
            @Value("${vidya.security.jwt.secret:vidya-dev-secret-key-change-in-production-min-256-bits-length}") String secret,
            @Value("${vidya.security.jwt.expiry-seconds:86400}") long expirySeconds,
            @Value("${vidya.security.jwt.refresh-expiry-seconds:604800}") long refreshExpirySeconds) {

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            this.signingKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        }

        this.accessTokenExpiryMs = expirySeconds * 1000L;
        this.refreshTokenExpiryMs = refreshExpirySeconds * 1000L;
    }

    public String generateAccessToken(UUID userId, String email, String userCode, String role) {
        return generateAccessToken(userId, email, userCode, role, null);
    }

    public String generateAccessToken(UUID userId, String email, String userCode, String role, UUID schoolId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        var builder = Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("userCode", userCode)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry);

        if (schoolId != null) {
            builder.claim("schoolId", schoolId.toString());
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
