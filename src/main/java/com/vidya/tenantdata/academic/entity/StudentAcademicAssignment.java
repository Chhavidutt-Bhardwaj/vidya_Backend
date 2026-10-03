package com.vidya.tenantdata.academic.entity;

import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
import com.vidya.tenantdata.student.entity.Student;
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
 * Historical and active placement record of a student in a class & section for an academic year.
 */
@Entity
@Table(name = "student_academic_assignment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAcademicAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

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
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(name = "roll_number", length = 50)
    private String rollNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StudentAssignmentStatus status = StudentAssignmentStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

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

    public StudentAcademicAssignment(Student student,
                                     AcademicYear academicYear,
                                     AcademicClass academicClass,
                                     Section section,
                                     String rollNumber,
                                     LocalDate startDate) {
        this.student = student;
        this.academicYear = academicYear;
        this.academicClass = academicClass;
        this.section = section;
        this.rollNumber = rollNumber != null ? rollNumber.trim() : null;
        this.startDate = startDate != null ? startDate : LocalDate.now();
        this.status = StudentAssignmentStatus.ACTIVE;
    }

    public void updateStatus(StudentAssignmentStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == StudentAssignmentStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public void transfer(LocalDate transferDate) {
        updateStatus(StudentAssignmentStatus.TRANSFERRED);
        this.endDate = transferDate != null ? transferDate : LocalDate.now();
    }

    public void complete(LocalDate completionDate) {
        updateStatus(StudentAssignmentStatus.COMPLETED);
        this.endDate = completionDate != null ? completionDate : LocalDate.now();
    }

    public boolean isDeleted() {
        return deletedAt != null || status == StudentAssignmentStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(StudentAssignmentStatus.DELETED);
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
