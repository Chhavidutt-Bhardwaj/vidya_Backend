package com.vidya.tenantdata.attendance.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import com.vidya.tenantdata.staff.entity.Staff;
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
 * Daily student attendance record in the tenant database.
 *
 * <p>Captures the attendance status of a student for a given date within a specific
 * academic year, class, and section. Exactly one active record may exist per
 * {@code (student, academicYear, attendanceDate)} combination — enforced by a
 * PostgreSQL partial unique index (V12 migration).
 *
 * <p>Attendance correction is supported by updating the existing record (status field),
 * preserving the full audit trail via {@code updatedBy} / {@code updatedAt}.
 */
@Entity
@Table(name = "student_attendance")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Stable human-readable business identifier (e.g. {@code ATT-A1B2C3D4}). Immutable after creation.
     */
    @Column(name = "attendance_code", nullable = false, unique = true, length = 50, updatable = false)
    private String attendanceCode;

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

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AttendanceStatus status;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /** Staff member who marked / last updated this attendance record. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by_staff_id")
    private Staff markedByStaff;

    @Column(name = "marked_at")
    private Instant markedAt;

    @Column(name = "non_working_day_override", nullable = false)
    private boolean nonWorkingDayOverride = false;

    @Column(name = "override_reason", length = 255)
    private String overrideReason;

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

    public StudentAttendance(String attendanceCode,
                             Student student,
                             AcademicYear academicYear,
                             AcademicClass academicClass,
                             Section section,
                             LocalDate attendanceDate,
                             AttendanceStatus status,
                             String remarks,
                             Staff markedByStaff) {
        this(attendanceCode, student, academicYear, academicClass, section, attendanceDate, status, remarks, markedByStaff, false, null);
    }

    public StudentAttendance(String attendanceCode,
                             Student student,
                             AcademicYear academicYear,
                             AcademicClass academicClass,
                             Section section,
                             LocalDate attendanceDate,
                             AttendanceStatus status,
                             String remarks,
                             Staff markedByStaff,
                             boolean nonWorkingDayOverride,
                             String overrideReason) {
        this.attendanceCode = attendanceCode;
        this.student = student;
        this.academicYear = academicYear;
        this.academicClass = academicClass;
        this.section = section;
        this.attendanceDate = attendanceDate;
        this.status = status;
        this.remarks = remarks != null ? remarks.trim() : null;
        this.markedByStaff = markedByStaff;
        this.markedAt = Instant.now();
        this.nonWorkingDayOverride = nonWorkingDayOverride;
        this.overrideReason = overrideReason != null ? overrideReason.trim() : null;
    }

    /**
     * Update the attendance status (attendance correction).
     * Validates state-machine transition before mutating.
     */
    public void updateStatus(AttendanceStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == AttendanceStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    /**
     * Correct this record: update status + optional remarks + who corrected it.
     */
    public void correct(AttendanceStatus newStatus, String remarks, Staff correctedBy) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (remarks != null && !remarks.isBlank()) {
            this.remarks = remarks.trim();
        }
        if (correctedBy != null) {
            this.markedByStaff = correctedBy;
        }
        this.markedAt = Instant.now();
    }

    /**
     * Correct this record with override details.
     */
    public void correct(AttendanceStatus newStatus, String remarks, Staff correctedBy,
                        boolean nonWorkingDayOverride, String overrideReason) {
        correct(newStatus, remarks, correctedBy);
        this.nonWorkingDayOverride = nonWorkingDayOverride;
        this.overrideReason = overrideReason != null ? overrideReason.trim() : null;
    }

    public boolean isDeleted() {
        return deletedAt != null || status == AttendanceStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(AttendanceStatus.DELETED);
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
