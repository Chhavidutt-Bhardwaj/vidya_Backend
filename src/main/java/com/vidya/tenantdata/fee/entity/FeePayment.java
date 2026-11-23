package com.vidya.tenantdata.fee.entity;

import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.enums.PaymentStatus;
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

/** Money received for one ledger; the receipt is this row. Never deleted: wrong payments are REVERSED. */
@Entity
@Table(name = "fee_payment")
@Getter
@Setter
@NoArgsConstructor
public class FeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, updatable = false, length = 40)
    private String receiptNumber;

    @Column(name = "ledger_id", nullable = false, updatable = false)
    private UUID ledgerId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PaymentMode mode;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(name = "realized_on")
    private LocalDate realizedOn;

    @Column(length = 500)
    private String remarks;

    @Column(name = "collected_by", updatable = false)
    private UUID collectedBy;

    @Column(name = "collected_by_name", updatable = false, length = 200)
    private String collectedByName;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

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
