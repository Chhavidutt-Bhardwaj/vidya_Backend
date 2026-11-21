package com.vidya.security.access.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** An extra role held by a user, on top of the account-type role in {@code platform_user.platform_role}. */
@Entity
@Table(name = "user_role_assignment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, updatable = false)
    private AccessRole role;

    /** School the assignment applies to (the user's school for school users; null for platform/community users). */
    @Column(name = "school_id", updatable = false)
    private UUID schoolId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "notes")
    private String notes;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public UserRoleAssignment(UUID userId, AccessRole role, UUID schoolId, Instant validFrom, Instant validUntil,
                              String notes, UUID assignedBy) {
        this.userId = userId;
        this.role = role;
        this.schoolId = schoolId;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.notes = notes;
        this.assignedBy = assignedBy;
        this.active = true;
    }

    /** Active, inside its validity window, and pointing at an active role. */
    public boolean isEffective(Instant now) {
        return active
                && role != null && role.isActive()
                && (validFrom == null || !validFrom.isAfter(now))
                && (validUntil == null || validUntil.isAfter(now));
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
