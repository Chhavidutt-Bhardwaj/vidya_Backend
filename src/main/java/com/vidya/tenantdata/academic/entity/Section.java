package com.vidya.tenantdata.academic.entity;

import com.vidya.tenantdata.academic.enums.SectionStatus;
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
 * Section of a class instantiated for a particular academic year.
 */
@Entity
@Table(name = "section")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SectionStatus status = SectionStatus.ACTIVE;

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

    public Section(AcademicYear academicYear, AcademicClass academicClass, String code, String name, Integer capacity) {
        this.academicYear = academicYear;
        this.academicClass = academicClass;
        this.code = code != null ? code.trim() : null;
        this.name = name != null ? name.trim() : null;
        this.capacity = capacity;
        this.status = SectionStatus.ACTIVE;
    }

    public void updateStatus(SectionStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == SectionStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == SectionStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(SectionStatus.DELETED);
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
