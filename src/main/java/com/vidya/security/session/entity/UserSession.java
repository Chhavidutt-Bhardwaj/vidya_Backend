package com.vidya.security.session.entity;

import com.vidya.security.access.AccessScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One login on one device. Access and refresh tokens carry this id as the {@code sid} claim; a
 * request is only authenticated while its session is ACTIVE and not expired.
 */
@Entity
@Table(name = "user_session")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

    /** Assigned in Java (not generated) so the id can go into the tokens before the insert. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "school_id", updatable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_scope", nullable = false, length = 20, updatable = false)
    private AccessScope accountScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.ACTIVE;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_reason", length = 50)
    private String endReason;

    @Column(name = "ended_by")
    private UUID endedBy;

    public UserSession(UUID userId, UUID schoolId, AccessScope accountScope, ClientInfoView client, Instant expiresAt) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.schoolId = schoolId;
        this.accountScope = accountScope;
        this.status = SessionStatus.ACTIVE;
        this.ipAddress = client.ipAddress();
        this.userAgent = client.userAgent();
        this.deviceType = client.deviceType();
        this.browser = client.browser();
        this.operatingSystem = client.operatingSystem();
        this.createdAt = now;
        this.lastSeenAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return status == SessionStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void end(SessionStatus newStatus, String reason, UUID endedBy) {
        if (this.status != SessionStatus.ACTIVE) {
            return;
        }
        this.status = newStatus;
        this.endReason = reason;
        this.endedBy = endedBy;
        this.endedAt = Instant.now();
    }

    /** Minimal view of the client so the entity does not depend on the service layer. */
    public interface ClientInfoView {
        String ipAddress();
        String userAgent();
        String deviceType();
        String browser();
        String operatingSystem();
    }
}
