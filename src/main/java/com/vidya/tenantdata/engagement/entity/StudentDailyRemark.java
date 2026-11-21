package com.vidya.tenantdata.engagement.entity;

import com.vidya.tenantdata.engagement.enums.RemarkCategory;
import com.vidya.tenantdata.engagement.enums.RemarkSentiment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A teacher's remark about one student on one day. Staff-only remarks never reach families. */
@Entity
@Table(name = "student_daily_remark")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentDailyRemark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "remark_date", nullable = false)
    private LocalDate remarkDate;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RemarkCategory category = RemarkCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RemarkSentiment sentiment = RemarkSentiment.NEUTRAL;

    @Column(nullable = false, length = 1000)
    private String remark;

    @Column(name = "visible_to_guardians", nullable = false)
    private boolean visibleToGuardians = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version = 0L;

    public StudentDailyRemark(UUID studentId, LocalDate remarkDate) {
        this.studentId = studentId;
        this.remarkDate = remarkDate;
    }
}
