package com.vidya.tenantdata.timetable.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.timetable.enums.SubstitutionStatus;
import com.vidya.tenantdata.timetable.enums.SubstitutionType;
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
 * Daily schedule substitution for a specific date, period, and class/section in an active timetable.
 */
@Entity
@Table(name = "timetable_substitution")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableSubstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "substitution_code", nullable = false, unique = true, length = 50, updatable = false)
    private String substitutionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @Column(name = "substitution_date", nullable = false)
    private LocalDate substitutionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private BellSchedulePeriod period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Enumerated(EnumType.STRING)
    @Column(name = "substitution_type", nullable = false, length = 30)
    private SubstitutionType substitutionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_teacher_id")
    private Staff originalTeacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_subject_id")
    private Subject originalSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "substitute_teacher_id")
    private Staff substituteTeacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "substitute_room_id")
    private Room substituteRoom;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubstitutionStatus status = SubstitutionStatus.ACTIVE;

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

    public TimetableSubstitution(
            String substitutionCode,
            Timetable timetable,
            LocalDate substitutionDate,
            BellSchedulePeriod period,
            AcademicClass academicClass,
            Section section,
            SubstitutionType substitutionType,
            Staff originalTeacher,
            Subject originalSubject,
            Staff substituteTeacher,
            Room substituteRoom,
            String reason) {
        this.substitutionCode = substitutionCode;
        this.timetable = timetable;
        this.substitutionDate = substitutionDate;
        this.period = period;
        this.academicClass = academicClass;
        this.section = section;
        this.substitutionType = substitutionType;
        this.originalTeacher = originalTeacher;
        this.originalSubject = originalSubject;
        this.substituteTeacher = substituteTeacher;
        this.substituteRoom = substituteRoom;
        this.reason = reason;
        this.status = SubstitutionStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.status = SubstitutionStatus.DELETED;
    }

    public void cancel() {
        this.status = SubstitutionStatus.CANCELLED;
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
