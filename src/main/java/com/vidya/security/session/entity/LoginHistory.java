package com.vidya.security.session.entity;

import com.vidya.security.access.AccessScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Append-only login/logout/session event for any user type (platform, community, school). */
@Entity
@Table(name = "login_history")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "email", length = 255, updatable = false)
    private String email;

    @Column(name = "school_id", updatable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_scope", length = 20, updatable = false)
    private AccessScope accountScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30, updatable = false)
    private LoginEventType eventType;

    @Column(name = "failure_reason", length = 100, updatable = false)
    private String failureReason;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @Column(name = "device_type", length = 20, updatable = false)
    private String deviceType;

    @Column(name = "browser", length = 50, updatable = false)
    private String browser;

    @Column(name = "operating_system", length = 50, updatable = false)
    private String operatingSystem;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
