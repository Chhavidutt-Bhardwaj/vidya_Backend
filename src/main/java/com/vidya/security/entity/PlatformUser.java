package com.vidya.security.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a platform-level user (Super Admin, Community Admin, Field Agent, School Owner/Admin).
 */
@Entity
@Table(name = "platform_user")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_code", nullable = false, unique = true, length = 50, updatable = false)
    private String userCode;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 50)
    private PlatformRole platformRole;

    @Column(name = "school_id")
    private UUID schoolId;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by")
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public PlatformUser(String userCode,
                        String email,
                        String passwordHash,
                        String firstName,
                        String lastName,
                        String phone,
                        PlatformRole platformRole) {
        this(userCode, email, passwordHash, firstName, lastName, phone, platformRole, null);
    }

    public PlatformUser(String userCode,
                        String email,
                        String passwordHash,
                        String firstName,
                        String lastName,
                        String phone,
                        PlatformRole platformRole,
                        UUID schoolId) {
        this.userCode = userCode;
        this.email = email != null ? email.trim().toLowerCase() : null;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.platformRole = platformRole;
        this.schoolId = schoolId;
        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
    }

    public String getFullName() {
        return firstName + (lastName != null && !lastName.isBlank() ? " " + lastName : "");
    }

    public boolean isAccountLocked() {
        if (status == UserStatus.SUSPENDED) {
            return true;
        }
        if (lockedUntil != null) {
            return lockedUntil.isAfter(Instant.now());
        }
        return false;
    }

    public void recordFailedLogin(int maxAttempts, long lockoutMinutes) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(Duration.ofMinutes(lockoutMinutes));
        }
    }

    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public void recordLogin() {
        recordSuccessfulLogin();
    }
}
