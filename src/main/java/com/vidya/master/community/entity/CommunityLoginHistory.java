package com.vidya.master.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Login history tracking for community members.
 */
@Entity
@Table(name = "community_login_history")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityLoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(name = "platform_user_id", nullable = false)
    private UUID platformUserId;

    @Column(name = "login_time", nullable = false)
    private Instant loginTime = Instant.now();

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "location", length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_status", nullable = false, length = 30)
    private LoginStatus loginStatus;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "logout_time")
    private Instant logoutTime;

    public CommunityLoginHistory(Community community,
                                   UUID platformUserId,
                                   Instant loginTime,
                                   Instant logoutTime,
                                   String ipAddress,
                                   String userAgent,
                                   String deviceType,
                                   String browser,
                                   String location,
                                   LoginStatus loginStatus,
                                   String failureReason,
                                   String sessionId) {
        this.community = community;
        this.platformUserId = platformUserId;
        this.loginTime = loginTime != null ? loginTime : Instant.now();
        this.logoutTime = logoutTime;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceType = deviceType;
        this.browser = browser;
        this.location = location;
        this.loginStatus = loginStatus;
        this.failureReason = failureReason;
        this.sessionId = sessionId;
    }
}
