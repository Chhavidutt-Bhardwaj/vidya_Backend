package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
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
 * Subject enrollment for a student within an academic enrollment.
 */
@Entity
@Table(name = "student_subject_enrollment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentSubjectEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subject_enrollment_code", nullable = false, unique = true, length = 50, updatable = false)
    private String subjectEnrollmentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private StudentAcademicEnrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_subject_assignment_id")
    private ClassSubjectAssignment classSubjectAssignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StudentSubjectEnrollmentStatus status = StudentSubjectEnrollmentStatus.ACTIVE;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

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

    public StudentSubjectEnrollment(String subjectEnrollmentCode,
                                    StudentAcademicEnrollment enrollment,
                                    Subject subject,
                                    ClassSubjectAssignment classSubjectAssignment,
                                    LocalDate effectiveFrom,
                                    String remarks) {
        this.subjectEnrollmentCode = subjectEnrollmentCode;
        this.enrollment = enrollment;
        this.subject = subject;
        this.classSubjectAssignment = classSubjectAssignment;
        this.status = StudentSubjectEnrollmentStatus.ACTIVE;
        this.effectiveFrom = effectiveFrom != null ? effectiveFrom : LocalDate.now();
        this.remarks = remarks != null ? remarks.trim() : null;
    }

    public void updateStatus(StudentSubjectEnrollmentStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == StudentSubjectEnrollmentStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public void drop(LocalDate dropDate, String remarks) {
        if (this.status != StudentSubjectEnrollmentStatus.ACTIVE) {
            throw new BusinessException("Can only drop an ACTIVE subject enrollment");
        }
        LocalDate date = dropDate != null ? dropDate : LocalDate.now();
        if (date.isBefore(this.effectiveFrom)) {
            throw new BusinessException("Drop date cannot be before effective from date");
        }
        updateStatus(StudentSubjectEnrollmentStatus.DROPPED);
        this.effectiveTo = date;
        if (remarks != null && !remarks.isBlank()) {
            this.remarks = remarks.trim();
        }
    }

    public void exempt(LocalDate exemptDate, String remarks) {
        if (this.status != StudentSubjectEnrollmentStatus.ACTIVE) {
            throw new BusinessException("Can only exempt an ACTIVE subject enrollment");
        }
        LocalDate date = exemptDate != null ? exemptDate : LocalDate.now();
        updateStatus(StudentSubjectEnrollmentStatus.EXEMPTED);
        this.effectiveTo = date;
        if (remarks != null && !remarks.isBlank()) {
            this.remarks = remarks.trim();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == StudentSubjectEnrollmentStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(StudentSubjectEnrollmentStatus.DELETED);
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
