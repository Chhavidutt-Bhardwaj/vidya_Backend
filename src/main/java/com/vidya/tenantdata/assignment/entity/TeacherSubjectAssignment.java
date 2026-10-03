package com.vidya.tenantdata.assignment.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import com.vidya.tenantdata.staff.entity.Staff;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Teacher Subject Assignment domain entity in the tenant database.
 *
 * <p>Represents the academic teaching assignment of a {@link Staff} member (teacher)
 * to a {@link Subject} for a given {@link AcademicClass}, optional {@link Section},
 * within an {@link AcademicYear} and optional {@link Curriculum}.
 */
@Entity
@Table(name = "teacher_subject_assignment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherSubjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "assignment_code", nullable = false, unique = true, length = 50, updatable = false)
    private String assignmentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Staff teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

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
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = true;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TeacherAssignmentStatus status = TeacherAssignmentStatus.ACTIVE;

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

    public TeacherSubjectAssignment(String assignmentCode,
                                    Staff teacher,
                                    Subject subject,
                                    AcademicYear academicYear,
                                    AcademicClass academicClass,
                                    Section section,
                                    Curriculum curriculum,
                                    boolean isPrimary,
                                    LocalDate startDate,
                                    LocalDate endDate) {
        this.assignmentCode = assignmentCode;
        this.teacher = teacher;
        this.subject = subject;
        this.academicYear = academicYear;
        this.academicClass = academicClass;
        this.section = section;
        this.curriculum = curriculum;
        this.isPrimary = isPrimary;
        this.startDate = startDate != null ? startDate : LocalDate.now();
        this.endDate = endDate;
        this.status = TeacherAssignmentStatus.ACTIVE;
    }

    public void updateStatus(TeacherAssignmentStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == TeacherAssignmentStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public void complete(LocalDate completionDate) {
        updateStatus(TeacherAssignmentStatus.COMPLETED);
        this.endDate = completionDate != null ? completionDate : LocalDate.now();
    }

    public void cancel() {
        updateStatus(TeacherAssignmentStatus.CANCELLED);
        if (this.endDate == null) {
            this.endDate = LocalDate.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == TeacherAssignmentStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(TeacherAssignmentStatus.DELETED);
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
