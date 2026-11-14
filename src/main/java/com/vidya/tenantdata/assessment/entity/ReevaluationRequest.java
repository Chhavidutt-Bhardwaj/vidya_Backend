package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.ReevaluationStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A request to re-check a student's exam paper. */
@Entity
@Table(name = "reevaluation_request")
@Getter
@Setter
@NoArgsConstructor
public class ReevaluationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_code", nullable = false, updatable = false)
    private String requestCode;

    @Column(name = "exam_subject_config_id", nullable = false)
    private UUID examSubjectConfigId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReevaluationStatus status = ReevaluationStatus.REQUESTED;

    @Column(name = "original_marks")
    private BigDecimal originalMarks;

    @Column(name = "revised_marks")
    private BigDecimal revisedMarks;

    @Column(name = "reviewer_staff_id")
    private UUID reviewerStaffId;

    @Column(name = "reviewer_note")
    private String reviewerNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
