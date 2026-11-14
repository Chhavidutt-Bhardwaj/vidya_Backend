package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.ModerationMethod;
import com.vidya.tenantdata.assessment.enums.ModerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One moderation decision applied to every eligible student of an exam paper. */
@Entity
@Table(name = "moderation_batch")
@Getter
@Setter
@NoArgsConstructor
public class ModerationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_code", nullable = false, updatable = false)
    private String batchCode;

    @Column(name = "exam_subject_config_id", nullable = false)
    private UUID examSubjectConfigId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private ModerationMethod method;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "only_below")
    private BigDecimal onlyBelow;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModerationStatus status = ModerationStatus.APPLIED;

    @Column(name = "students_affected")
    private int studentsAffected;

    @Column(name = "applied_by")
    private UUID appliedBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "reverted_by")
    private UUID revertedBy;

    @Column(name = "reverted_at")
    private Instant revertedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
