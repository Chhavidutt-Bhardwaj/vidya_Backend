package com.vidya.security.access.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A per-user exception: GRANT adds a permission without a role, DENY removes one even if a role grants it. */
@Entity
@Table(name = "user_permission_override")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPermissionOverride {

    public enum Effect { GRANT, DENY }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false, updatable = false)
    private AccessPermission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 10)
    private Effect effect;

    @Column(name = "reason")
    private String reason;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserPermissionOverride(UUID userId, AccessPermission permission, Effect effect, String reason,
                                  Instant validUntil, UUID createdBy) {
        this.userId = userId;
        this.permission = permission;
        this.effect = effect;
        this.reason = reason;
        this.validUntil = validUntil;
        this.createdBy = createdBy;
    }

    public boolean isEffective(Instant now) {
        return validUntil == null || validUntil.isAfter(now);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
