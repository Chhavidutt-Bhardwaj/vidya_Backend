package com.vidya.tenantdata.subject.entity;

import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
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
 * Subject operational domain entity in the tenant database.
 *
 * <p>Represents the reusable academic subject definition within a school/tenant.
 * The {@code subjectCode} is the stable Vidya business identifier (e.g. {@code SUB-A1B2C3D4}).
 */
@Entity
@Table(name = "subject")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject_code", nullable = false, unique = true, length = 50, updatable = false)
    private String subjectCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 30)
    private SubjectType subjectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubjectStatus status = SubjectStatus.ACTIVE;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

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

    public Subject(String subjectCode, String name, String shortName, String description,
                   SubjectType subjectType, Integer displayOrder) {
        this.subjectCode = subjectCode;
        this.name = name != null ? name.trim() : null;
        this.shortName = shortName != null ? shortName.trim() : null;
        this.description = description != null ? description.trim() : null;
        this.subjectType = subjectType;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.status = SubjectStatus.ACTIVE;
    }

    public void updateStatus(SubjectStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == SubjectStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == SubjectStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(SubjectStatus.DELETED);
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
        if (this.displayOrder == null) {
            this.displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
