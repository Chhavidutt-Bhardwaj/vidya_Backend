package com.vidya.tenantdata.user.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant operational user entity in the tenant's database.
 *
 * <p>Represents the operational identity of school users (admin, staff, teacher, student, parent).
 * Centralized authentication is handled in the Master DB; this entity holds school operational
 * identity and references the optional {@code masterUserId}.
 */
@Entity
@Table(name = "tenant_user")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_code", nullable = false, unique = true, length = 50, updatable = false)
    private String userCode;

    @Column(name = "master_user_id")
    private UUID masterUserId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 50)
    private TenantUserType userType = TenantUserType.STAFF;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantUserStatus status = TenantUserStatus.ACTIVE;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private TenantUserProfile profile;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public TenantUser(String userCode, String email, String phone, TenantUserType userType) {
        this(userCode, null, email, phone, userType);
    }

    public TenantUser(String userCode, UUID masterUserId, String email, String phone, TenantUserType userType) {
        this.userCode = userCode;
        this.masterUserId = masterUserId;
        this.email = email != null ? email.trim().toLowerCase() : null;
        this.phone = phone != null ? phone.trim() : null;
        this.userType = userType != null ? userType : TenantUserType.STAFF;
        this.status = TenantUserStatus.ACTIVE;
    }

    public void setProfile(TenantUserProfile profile) {
        this.profile = profile;
        if (profile != null && profile.getUser() != this) {
            profile.setUser(this);
        }
    }

    public void updateStatus(TenantUserStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == TenantUserStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == TenantUserStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(TenantUserStatus.DELETED);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
