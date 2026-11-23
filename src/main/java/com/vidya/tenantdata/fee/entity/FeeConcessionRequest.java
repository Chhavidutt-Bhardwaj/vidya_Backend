package com.vidya.tenantdata.fee.entity;

import com.vidya.tenantdata.fee.enums.ConcessionRequestStatus;
import com.vidya.tenantdata.operations.enums.ConcessionKind;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * A concession for one student and year. PERCENT takes that share off each matching line; FIXED is an amount for the year,
 * taken off the latest dues first. {@code feeHeadId} null = every head billed from the fee structure.
 */
@Entity
@Table(name = "fee_concession_request")
@Getter
@Setter
@NoArgsConstructor
public class FeeConcessionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_number", nullable = false, updatable = false, length = 40)
    private String requestNumber;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "concession_type_id", nullable = false, updatable = false)
    private UUID concessionTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ConcessionKind kind;

    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal percent;

    @Column(name = "amount_paise", updatable = false)
    private Long amountPaise;

    @Column(name = "fee_head_id", updatable = false)
    private UUID feeHeadId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConcessionRequestStatus status = ConcessionRequestStatus.PENDING;

    @Column(name = "applied_paise", nullable = false)
    private long appliedPaise;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_by_name", updatable = false, length = 200)
    private String requestedByName;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_by_name", length = 200)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_remarks", length = 500)
    private String reviewRemarks;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
