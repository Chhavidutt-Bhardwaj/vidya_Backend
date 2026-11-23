package com.vidya.security.access.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A permission in the DB-driven catalog. {@link #code} is the authority string checked by
 * {@code @PreAuthorize("hasAuthority('CODE')")}.
 */
@Entity
@Table(name = "access_permission")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessPermission {

    /** Which roles may carry this permission. */
    public enum PermissionScope { PLATFORM, SCHOOL, COMMUNITY }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "permission_code", nullable = false, unique = true, length = 100, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "module_code", nullable = false, length = 50)
    private String moduleCode = "GENERAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private PermissionScope scope = PermissionScope.SCHOOL;

    /** Subscription feature (or module) a school must have enabled for school users to hold this permission. */
    @Column(name = "feature_code", length = 50)
    private String featureCode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /** Frontend permissionsMatrix cells this permission lights up (may be empty). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "access_permission_matrix_cell", joinColumns = @JoinColumn(name = "permission_id"))
    @BatchSize(size = 100)
    private Set<MatrixCell> matrixCells = new HashSet<>();

    public AccessPermission(String code, String name, String description, String moduleCode,
                            PermissionScope scope, String featureCode) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.moduleCode = moduleCode != null ? moduleCode : "GENERAL";
        this.scope = scope != null ? scope : PermissionScope.SCHOOL;
        this.featureCode = featureCode;
        this.active = true;
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
