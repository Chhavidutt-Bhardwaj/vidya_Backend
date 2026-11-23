package com.vidya.tenantdata.fee.entity;

import com.vidya.tenantdata.fee.enums.DueStatus;
import com.vidya.tenantdata.fee.enums.LineSource;
import com.vidya.tenantdata.fee.enums.LineStatus;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One amount owed: a fee head for one instalment, or a one-off charge. Change it only while holding the ledger's lock
 * ({@code FeeLedgerRepository.lock}); the database also checks concession + paid + pending ≤ amount.
 */
@Entity
@Table(name = "fee_ledger_line")
@Getter
@Setter
@NoArgsConstructor
public class FeeLedgerLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ledger_id", nullable = false, updatable = false)
    private UUID ledgerId;

    @Column(name = "fee_head_id", nullable = false, updatable = false)
    private UUID feeHeadId;

    @Column(name = "installment_id")
    private UUID installmentId;

    @Column(name = "installment_no", updatable = false)
    private Integer installmentNo;

    @Column(name = "installment_name", length = 100)
    private String installmentName;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private LineSource source;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "concession_paise", nullable = false)
    private long concessionPaise;

    @Column(name = "paid_paise", nullable = false)
    private long paidPaise;

    @Column(name = "pending_paise", nullable = false)
    private long pendingPaise;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineStatus status = LineStatus.ACTIVE;

    @Column(length = 500)
    private String reason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private Long version;

    /** Still to pay: amount − concession − paid − cheques waiting to clear. */
    public long duePaise() {
        return status == LineStatus.ACTIVE ? amountPaise - concessionPaise - paidPaise - pendingPaise : 0;
    }

    public DueStatus dueStatus(LocalDate today) {
        if (duePaise() <= 0) {
            return DueStatus.PAID;
        }
        if (dueDate.isBefore(today)) {
            return DueStatus.OVERDUE;
        }
        return paidPaise > 0 ? DueStatus.PARTIAL : DueStatus.UPCOMING;
    }
}
