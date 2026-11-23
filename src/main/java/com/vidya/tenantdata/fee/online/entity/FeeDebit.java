package com.vidya.tenantdata.fee.online.entity;

import com.vidya.tenantdata.fee.online.enums.DebitStatus;
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

/** One auto-debit of one child's dues through a mandate, for one month and attempt. While SUBMITTED its fee_payment holds the lines as pending. */
@Entity
@Table(name = "fee_debit")
@Getter
@Setter
@NoArgsConstructor
public class FeeDebit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "mandate_id", nullable = false, updatable = false)
    private UUID mandateId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "ledger_id", nullable = false, updatable = false)
    private UUID ledgerId;

    @Column(name = "cycle_month", nullable = false, updatable = false)
    private LocalDate cycleMonth;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private short attemptNo;

    @Column(name = "sibling_rank", nullable = false)
    private short siblingRank;

    @Column(name = "scheduled_on", nullable = false)
    private LocalDate scheduledOn;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebitStatus status = DebitStatus.SCHEDULED;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "fee_payment_id")
    private UUID feePaymentId;

    @Column(name = "bounce_charge_paise", nullable = false)
    private long bounceChargePaise;

    @Column(name = "bounce_gst_paise", nullable = false)
    private long bounceGstPaise;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

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
