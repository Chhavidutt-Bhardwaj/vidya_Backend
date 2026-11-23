package com.vidya.tenantdata.fee.online.entity;

import com.vidya.tenantdata.fee.online.enums.CheckoutKind;
import com.vidya.tenantdata.fee.online.enums.CheckoutStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
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
import java.util.UUID;

/** A payment link or checkout order for one or more children of one payer; fee_checkout_student lists them, higher class first. */
@Entity
@Table(name = "fee_checkout")
@Getter
@Setter
@NoArgsConstructor
public class FeeCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CheckoutKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private GatewayProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutStatus status = CheckoutStatus.CREATED;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "paid_paise", nullable = false)
    private long paidPaise;

    @Column(name = "unallocated_paise", nullable = false)
    private long unallocatedPaise;

    @Column(name = "allow_partial", nullable = false, updatable = false)
    private boolean allowPartial;

    @Column(name = "payer_name", nullable = false)
    private String payerName;

    @Column(name = "payer_email")
    private String payerEmail;

    @Column(name = "payer_phone", nullable = false)
    private String payerPhone;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "gateway_id")
    private String gatewayId;

    @Column
    private String url;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

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
