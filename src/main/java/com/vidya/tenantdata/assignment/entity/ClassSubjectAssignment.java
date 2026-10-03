package com.vidya.tenantdata.assignment.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.Subject;
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
 * Class Subject Assignment domain entity in the tenant database.
 *
 * <p>Represents the actual deployed academic subject offering for an {@link AcademicClass}
 * and optional {@link Section} during a specific {@link AcademicYear}.
 */
@Entity
@Table(name = "class_subject_assignment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassSubjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "assignment_code", nullable = false, unique = true, length = 50, updatable = false)
    private String assignmentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    @Column(name = "is_compulsory", nullable = false)
    private boolean isCompulsory = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "periods_per_week")
    private Integer periodsPerWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClassSubjectAssignmentStatus status = ClassSubjectAssignmentStatus.ACTIVE;

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

    public ClassSubjectAssignment(String assignmentCode,
                                  AcademicYear academicYear,
                                  AcademicClass academicClass,
                                  Section section,
                                  Subject subject,
                                  Curriculum curriculum,
                                  boolean isCompulsory,
                                  Integer displayOrder,
                                  Integer periodsPerWeek) {
        this.assignmentCode = assignmentCode;
        this.academicYear = academicYear;
        this.academicClass = academicClass;
        this.section = section;
        this.subject = subject;
        this.curriculum = curriculum;
        this.isCompulsory = isCompulsory;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.periodsPerWeek = periodsPerWeek;
        this.status = ClassSubjectAssignmentStatus.ACTIVE;
    }

    public void updateStatus(ClassSubjectAssignmentStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == ClassSubjectAssignmentStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == ClassSubjectAssignmentStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(ClassSubjectAssignmentStatus.DELETED);
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
