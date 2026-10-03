package com.vidya.tenantdata.academic.entity;

import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import jakarta.persistence.Column;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Reusable grade / class level representation within the school tenant.
 */
@Entity
@Table(name = "academic_class")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademicClass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AcademicClassStatus status = AcademicClassStatus.ACTIVE;

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

    public AcademicClass(String code, String name, String displayName, int sortOrder) {
        this.code = code != null ? code.trim() : null;
        this.name = name != null ? name.trim() : null;
        this.displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : this.name;
        this.sortOrder = sortOrder;
        this.status = AcademicClassStatus.ACTIVE;
    }

    public void updateStatus(AcademicClassStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == AcademicClassStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == AcademicClassStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(AcademicClassStatus.DELETED);
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
        if (this.displayName == null || this.displayName.isBlank()) {
            this.displayName = this.name;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        if (this.displayName == null || this.displayName.isBlank()) {
            this.displayName = this.name;
        }
    }
}
