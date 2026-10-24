package com.vidya.master.community.entity;

import com.vidya.common.audit.AuditableEntity;
import com.vidya.master.support.entity.SchoolSupportQuery;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Dual Commission Accounting Ledger Entry:
 * <ul>
 *   <li>{@link CommissionType#ONBOARDING}: Awarded to Community A (the community that brought/onboarded the school).</li>
 *   <li>{@link CommissionType#QUERY_RESOLUTION}: Awarded to Community B (the relationship manager community servicing queries).</li>
 * </ul>
 */
@Entity
@Table(name = "community_commission_ledger")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityCommissionLedgerEntry extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false, length = 50)
    private CommissionType commissionType;

    @Column(name = "school_id")
    private UUID schoolId;

    @Column(name = "school_name", length = 300)
    private String schoolName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "support_query_id")
    private SchoolSupportQuery supportQuery;

    @Column(name = "ticket_number", length = 50)
    private String ticketNumber;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CommissionLedgerStatus status = CommissionLedgerStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    public CommunityCommissionLedgerEntry(Community community,
                                         CommissionType commissionType,
                                         UUID schoolId,
                                         String schoolName,
                                         SchoolSupportQuery supportQuery,
                                         String ticketNumber,
                                         BigDecimal amount,
                                         String currency,
                                         String notes) {
        this.community = community;
        this.commissionType = commissionType;
        this.schoolId = schoolId;
        this.schoolName = schoolName;
        this.supportQuery = supportQuery;
        this.ticketNumber = ticketNumber;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.currency = (currency != null && !currency.isBlank()) ? currency : "INR";
        this.notes = notes;
        this.status = CommissionLedgerStatus.PENDING;
        this.earnedAt = Instant.now();
    }
}
