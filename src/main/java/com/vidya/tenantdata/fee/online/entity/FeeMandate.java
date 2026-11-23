package com.vidya.tenantdata.fee.online.entity;

import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateMethod;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
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

/** An eNACH / UPI Autopay mandate of one payer, covering the children listed in fee_mandate_student. */
@Entity
@Table(name = "fee_mandate")
@Getter
@Setter
@NoArgsConstructor
public class FeeMandate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private GatewayProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MandateMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MandateStatus status = MandateStatus.PENDING;

    @Column(name = "payer_name", nullable = false)
    private String payerName;

    @Column(name = "payer_email")
    private String payerEmail;

    @Column(name = "payer_phone", nullable = false)
    private String payerPhone;

    @Column(name = "guardian_user_id")
    private UUID guardianUserId;

    @Column(name = "max_amount_paise", nullable = false)
    private long maxAmountPaise;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "gateway_mandate_id")
    private String gatewayMandateId;

    @Column(name = "gateway_customer_id")
    private String gatewayCustomerId;

    @Column(name = "gateway_auth_id")
    private String gatewayAuthId;

    @Column(name = "auth_url")
    private String authUrl;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by")
    private UUID endedBy;

    @Column(name = "end_reason")
    private String endReason;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

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
