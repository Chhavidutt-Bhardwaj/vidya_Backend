package com.vidya.tenantdata.timetable.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

/**
 * Scheduled slot in a {@link Timetable}.
 */
@Entity
@Table(name = "timetable_entry")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entry_code", nullable = false, unique = true, length = 50, updatable = false)
    private String entryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private BellSchedulePeriod period;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_assignment_id")
    private TeacherSubjectAssignment teacherAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Staff teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_subject_assignment_id")
    private ClassSubjectAssignment classSubjectAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private TimetableEntryType entryType = TimetableEntryType.REGULAR;

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

    public TimetableEntry(String entryCode,
                          Timetable timetable,
                          AcademicYear academicYear,
                          BellSchedulePeriod period,
                          DayOfWeek dayOfWeek,
                          AcademicClass academicClass,
                          Section section,
                          Subject subject,
                          TeacherSubjectAssignment teacherAssignment,
                          Staff teacher,
                          ClassSubjectAssignment classSubjectAssignment,
                          Room room,
                          TimetableEntryType entryType) {
        this.entryCode = entryCode;
        this.timetable = timetable;
        this.academicYear = academicYear;
        this.period = period;
        this.dayOfWeek = dayOfWeek;
        this.academicClass = academicClass;
        this.section = section;
        this.subject = subject;
        this.teacherAssignment = teacherAssignment;
        this.teacher = teacher;
        this.classSubjectAssignment = classSubjectAssignment;
        this.room = room;
        this.entryType = entryType != null ? entryType : TimetableEntryType.REGULAR;
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
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
