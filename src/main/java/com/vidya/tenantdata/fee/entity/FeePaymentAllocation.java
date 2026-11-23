package com.vidya.tenantdata.fee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** How much of a payment went to one ledger line. */
@Entity
@Table(name = "fee_payment_allocation")
@Getter
@Setter
@NoArgsConstructor
public class FeePaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "ledger_line_id", nullable = false, updatable = false)
    private UUID ledgerLineId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    public FeePaymentAllocation(UUID paymentId, UUID ledgerLineId, long amountPaise) {
        this.paymentId = paymentId;
        this.ledgerLineId = ledgerLineId;
        this.amountPaise = amountPaise;
    }
}
