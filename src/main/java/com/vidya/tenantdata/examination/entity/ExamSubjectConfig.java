package com.vidya.tenantdata.examination.entity;

import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.timetable.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "exam_subject_config")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamSubjectConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "config_code", nullable = false, unique = true, length = 50, updatable = false)
    private String configCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_schedule_id", nullable = false)
    private ExamSchedule examSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "pass_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal passMarks;

    @Column(name = "theory_max_marks", precision = 6, scale = 2)
    private BigDecimal theoryMaxMarks;

    @Column(name = "practical_max_marks", precision = 6, scale = 2)
    private BigDecimal practicalMaxMarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

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

    public ExamSubjectConfig(String configCode, ExamSchedule examSchedule, Subject subject,
                             LocalDate examDate, LocalTime startTime, LocalTime endTime,
                             BigDecimal maxMarks, BigDecimal passMarks,
                             BigDecimal theoryMaxMarks, BigDecimal practicalMaxMarks, Room room) {
        this.configCode = configCode;
        this.examSchedule = examSchedule;
        this.subject = subject;
        this.examDate = examDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxMarks = maxMarks;
        this.passMarks = passMarks;
        this.theoryMaxMarks = theoryMaxMarks;
        this.practicalMaxMarks = practicalMaxMarks;
        this.room = room;
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
