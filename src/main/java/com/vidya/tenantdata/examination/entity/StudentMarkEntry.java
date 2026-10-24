package com.vidya.tenantdata.examination.entity;

import com.vidya.tenantdata.examination.enums.MarkEntryStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_mark_entry")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentMarkEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entry_code", nullable = false, unique = true, length = 50, updatable = false)
    private String entryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_subject_config_id", nullable = false)
    private ExamSubjectConfig examSubjectConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "marks_obtained", precision = 6, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "theory_marks_obtained", precision = 6, scale = 2)
    private BigDecimal theoryMarksObtained;

    @Column(name = "practical_marks_obtained", precision = 6, scale = 2)
    private BigDecimal practicalMarksObtained;

    @Column(name = "grade", length = 10)
    private String grade;

    @Column(name = "grade_point", precision = 4, scale = 2)
    private BigDecimal gradePoint;

    @Column(name = "is_absent", nullable = false)
    private boolean isAbsent = false;

    @Column(name = "is_exempt", nullable = false)
    private boolean isExempt = false;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_by_staff_id")
    private Staff evaluatedByStaff;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MarkEntryStatus status = MarkEntryStatus.DRAFT;

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

    public StudentMarkEntry(String entryCode, ExamSubjectConfig examSubjectConfig, Student student) {
        this.entryCode = entryCode;
        this.examSubjectConfig = examSubjectConfig;
        this.student = student;
        this.status = MarkEntryStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
