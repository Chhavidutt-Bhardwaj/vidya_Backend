package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.AdjustmentStatus;
import com.vidya.tenantdata.assessment.enums.AdjustmentType;
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

/** A change to an exam paper or assessment mark after entry; raw marks are never overwritten. */
@Entity
@Table(name = "mark_adjustment")
@Getter
@Setter
@NoArgsConstructor
public class MarkAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exam_subject_config_id")
    private UUID examSubjectConfigId;

    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false)
    private AdjustmentType adjustmentType;

    @Column(name = "delta", nullable = false)
    private BigDecimal delta;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "moderation_batch_id")
    private UUID moderationBatchId;

    @Column(name = "reevaluation_request_id")
    private UUID reevaluationRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AdjustmentStatus status = AdjustmentStatus.PROPOSED;

    @Column(name = "proposed_by")
    private UUID proposedBy;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
