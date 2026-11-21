package com.vidya.security.access.entity;

import com.vidya.security.access.AccessScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A role. {@code schoolId == null} means a global role (system roles that back
 * {@code platform_user.platform_role}, or platform-defined templates); otherwise it is a custom
 * role owned by that school.
 */
@Entity
@Table(name = "access_role")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "role_code", nullable = false, length = 50, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20, updatable = false)
    private AccessScope scope;

    @Column(name = "school_id", updatable = false)
    private UUID schoolId;

    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    @Column(name = "grants_all_permissions", nullable = false)
    private boolean grantsAllPermissions;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "access_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<AccessPermission> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public AccessRole(String code, String name, String description, AccessScope scope, UUID schoolId, UUID createdBy) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.scope = scope;
        this.schoolId = schoolId;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.system = false;
        this.grantsAllPermissions = false;
        this.active = true;
    }

    public boolean isGlobal() {
        return schoolId == null;
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
