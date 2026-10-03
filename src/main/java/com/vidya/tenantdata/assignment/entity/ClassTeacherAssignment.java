package com.vidya.tenantdata.assignment.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import com.vidya.tenantdata.staff.entity.Staff;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Class Teacher Assignment domain entity in the tenant database.
 *
 * <p>Represents the designation of a {@link Staff} member (teacher) as the responsible
 * class/section teacher for a given {@link Section} within an {@link AcademicYear}.
 *
 * <p>Business rule: at most one ACTIVE class teacher may exist per (academicYear, section).
 * This is enforced at both the application layer and the database layer via a partial unique index.
 */
@Entity
@Table(name = "class_teacher_assignment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassTeacherAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Stable business identifier (e.g. {@code CTA-A1B2C3D4}). Immutable after creation.
     */
    @Column(name = "assignment_code", nullable = false, unique = true, length = 50, updatable = false)
    private String assignmentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClassTeacherAssignmentStatus status = ClassTeacherAssignmentStatus.ACTIVE;

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

    public ClassTeacherAssignment(String assignmentCode,
                                  Staff staff,
                                  AcademicYear academicYear,
                                  AcademicClass academicClass,
                                  Section section,
                                  LocalDate startDate,
                                  String notes) {
        this.assignmentCode = assignmentCode;
        this.staff = staff;
        this.academicYear = academicYear;
        this.academicClass = academicClass;
        this.section = section;
        this.startDate = startDate != null ? startDate : LocalDate.now();
        this.notes = notes;
        this.status = ClassTeacherAssignmentStatus.ACTIVE;
    }

    public void updateStatus(ClassTeacherAssignmentStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == ClassTeacherAssignmentStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public void complete(LocalDate completionDate) {
        updateStatus(ClassTeacherAssignmentStatus.COMPLETED);
        this.endDate = completionDate != null ? completionDate : LocalDate.now();
    }

    public void cancel() {
        updateStatus(ClassTeacherAssignmentStatus.CANCELLED);
        if (this.endDate == null) {
            this.endDate = LocalDate.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == ClassTeacherAssignmentStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(ClassTeacherAssignmentStatus.DELETED);
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
