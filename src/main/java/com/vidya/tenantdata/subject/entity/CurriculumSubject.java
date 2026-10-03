package com.vidya.tenantdata.subject.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
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

/**
 * CurriculumSubject relational domain entity in the tenant database.
 *
 * <p>Maps an academic {@link Subject} to a {@link Curriculum} at a specific {@link AcademicClass} level,
 * with flags for compulsory/elective status and display ordering.
 */
@Entity
@Table(name = "curriculum_subject")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurriculumSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @Column(name = "is_compulsory", nullable = false)
    private boolean isCompulsory = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public CurriculumSubject(Curriculum curriculum, Subject subject, AcademicClass academicClass,
                             boolean isCompulsory, Integer displayOrder) {
        this.curriculum = curriculum;
        this.subject = subject;
        this.academicClass = academicClass;
        this.isCompulsory = isCompulsory;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
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
