package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
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
 * Academic enrollment linking a student identity to an academic year, class, and section.
 *
 * <p>Separates the long-lived student identity from their academic placements across years.
 */
@Entity
@Table(name = "student_academic_enrollment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAcademicEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "enrollment_code", nullable = false, unique = true, length = 50, updatable = false)
    private String enrollmentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(name = "roll_number", length = 50)
    private String rollNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 30)
    private StudentEnrollmentStatus enrollmentStatus = StudentEnrollmentStatus.ACTIVE;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "enrollment_date", nullable = false)
    private LocalDate enrollmentDate;

    @Column(name = "withdrawal_date")
    private LocalDate withdrawalDate;

    @Column(name = "withdrawal_reason", length = 255)
    private String withdrawalReason;

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

    public StudentAcademicEnrollment(String enrollmentCode,
                                     Student student,
                                     AcademicYear academicYear,
                                     AcademicClass academicClass,
                                     Section section,
                                     String rollNumber,
                                     StudentEnrollmentStatus enrollmentStatus,
                                     LocalDate admissionDate,
                                     LocalDate enrollmentDate,
                                     String remarks) {
        this.enrollmentCode = enrollmentCode;
        this.student = student;
        this.academicYear = academicYear;
        this.academicClass = academicClass;
        this.section = section;
        this.rollNumber = rollNumber != null ? rollNumber.trim() : null;
        this.enrollmentStatus = enrollmentStatus != null ? enrollmentStatus : StudentEnrollmentStatus.ACTIVE;
        this.admissionDate = admissionDate;
        this.enrollmentDate = enrollmentDate != null ? enrollmentDate : LocalDate.now();
        this.remarks = remarks != null ? remarks.trim() : null;
    }

    public void updateStatus(StudentEnrollmentStatus newStatus) {
        this.enrollmentStatus.validateTransitionTo(newStatus);
        this.enrollmentStatus = newStatus;
        if (newStatus == StudentEnrollmentStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public void withdraw(LocalDate withdrawalDate, String withdrawalReason, String remarks) {
        if (this.enrollmentStatus != StudentEnrollmentStatus.ACTIVE && this.enrollmentStatus != StudentEnrollmentStatus.ADMITTED) {
            throw new BusinessException("Can only withdraw an ACTIVE or ADMITTED student enrollment");
        }
        LocalDate date = withdrawalDate != null ? withdrawalDate : LocalDate.now();
        if (date.isBefore(this.enrollmentDate)) {
            throw new BusinessException("Withdrawal date cannot be before enrollment date");
        }
        updateStatus(StudentEnrollmentStatus.WITHDRAWN);
        this.withdrawalDate = date;
        this.withdrawalReason = withdrawalReason != null ? withdrawalReason.trim() : null;
        if (remarks != null && !remarks.isBlank()) {
            this.remarks = remarks.trim();
        }
    }

    public void transfer(LocalDate transferDate, String remarks) {
        if (this.enrollmentStatus != StudentEnrollmentStatus.ACTIVE && this.enrollmentStatus != StudentEnrollmentStatus.ADMITTED) {
            throw new BusinessException("Can only transfer an ACTIVE or ADMITTED student enrollment");
        }
        LocalDate date = transferDate != null ? transferDate : LocalDate.now();
        if (date.isBefore(this.enrollmentDate)) {
            throw new BusinessException("Transfer date cannot be before enrollment date");
        }
        updateStatus(StudentEnrollmentStatus.TRANSFERRED);
        this.withdrawalDate = date;
        this.withdrawalReason = "Transferred to new section/class";
        if (remarks != null && !remarks.isBlank()) {
            this.remarks = remarks.trim();
        }
    }

    public void promote(LocalDate promotionDate) {
        if (this.enrollmentStatus != StudentEnrollmentStatus.ACTIVE) {
            throw new BusinessException("Can only promote an ACTIVE student enrollment");
        }
        updateStatus(StudentEnrollmentStatus.PROMOTED);
        this.withdrawalDate = promotionDate != null ? promotionDate : LocalDate.now();
    }

    public void complete(LocalDate completionDate) {
        if (this.enrollmentStatus != StudentEnrollmentStatus.ACTIVE) {
            throw new BusinessException("Can only complete an ACTIVE student enrollment");
        }
        updateStatus(StudentEnrollmentStatus.COMPLETED);
        this.withdrawalDate = completionDate != null ? completionDate : LocalDate.now();
    }

    public boolean isDeleted() {
        return deletedAt != null || enrollmentStatus == StudentEnrollmentStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(StudentEnrollmentStatus.DELETED);
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
