package com.vidya.tenantdata.subject.entity;

import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Curriculum operational domain entity in the tenant database.
 *
 * <p>Represents an academic structure/configuration for a school tied to an {@link AcademicYear}.
 * The {@code curriculumCode} is the stable Vidya business identifier (e.g. {@code CUR-A1B2C3D4}).
 */
@Entity
@Table(name = "curriculum")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curriculum {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "curriculum_code", nullable = false, unique = true, length = 50, updatable = false)
    private String curriculumCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CurriculumStatus status = CurriculumStatus.DRAFT;

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

    public Curriculum(String curriculumCode, AcademicYear academicYear, String name, String description) {
        this.curriculumCode = curriculumCode;
        this.academicYear = academicYear;
        this.name = name != null ? name.trim() : null;
        this.description = description != null ? description.trim() : null;
        this.status = CurriculumStatus.DRAFT;
    }

    public void updateStatus(CurriculumStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == CurriculumStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == CurriculumStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(CurriculumStatus.DELETED);
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
