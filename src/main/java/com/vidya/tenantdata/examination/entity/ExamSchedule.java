package com.vidya.tenantdata.examination.entity;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.examination.enums.ExamScheduleStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "exam_schedule")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "schedule_code", nullable = false, unique = true, length = 50, updatable = false)
    private String scheduleCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_term_id", nullable = false)
    private ExamTerm examTerm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_class_id", nullable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grading_scale_id")
    private GradingScale gradingScale;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExamScheduleStatus status = ExamScheduleStatus.DRAFT;

    @OneToMany(mappedBy = "examSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamSubjectConfig> subjectConfigs = new ArrayList<>();

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

    public ExamSchedule(String scheduleCode, ExamTerm examTerm, AcademicClass academicClass,
                        GradingScale gradingScale, String name) {
        this.scheduleCode = scheduleCode;
        this.examTerm = examTerm;
        this.academicClass = academicClass;
        this.gradingScale = gradingScale;
        this.name = name;
        this.status = ExamScheduleStatus.DRAFT;
    }

    public void addSubjectConfig(ExamSubjectConfig config) {
        subjectConfigs.add(config);
        config.setExamSchedule(this);
    }

    public void removeSubjectConfig(ExamSubjectConfig config) {
        subjectConfigs.remove(config);
        config.setExamSchedule(null);
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
