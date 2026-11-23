package com.vidya.tenantdata.fee.online.entity;

import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A payment the gateway reported, applied once: the unique (provider, gateway_payment_id) makes webhook repeats harmless. */
@Entity
@Table(name = "fee_gateway_payment")
@Getter
@Setter
@NoArgsConstructor
public class FeeGatewayPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private GatewayProvider provider;

    @Column(name = "gateway_payment_id", nullable = false, updatable = false, length = 150)
    private String gatewayPaymentId;

    @Column(name = "checkout_id", updatable = false)
    private UUID checkoutId;

    @Column(name = "debit_id", updatable = false)
    private UUID debitId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "allocated_paise", nullable = false)
    private long allocatedPaise;

    @Column(length = 40)
    private String method;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
