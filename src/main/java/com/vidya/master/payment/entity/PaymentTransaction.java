package com.vidya.master.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable ledger record of every payment transaction initiated on the platform.
 * <p>
 * NEVER delete or directly rewrite completed records for financial audit integrity.
 */
@Entity
@Table(name = "payment_transaction")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_code", nullable = false, unique = true, length = 50)
    private String transactionCode;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "onboarding_id")
    private UUID onboardingId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 30)
    private PaymentProvider paymentProvider;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(name = "provider_order_id", length = 255)
    private String providerOrderId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_callback", columnDefinition = "jsonb")
    private Map<String, Object> rawCallback = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public PaymentTransaction(String transactionCode,
                              UUID schoolId,
                              UUID subscriptionId,
                              UUID onboardingId,
                              BigDecimal amount,
                              String currency,
                              PaymentProvider paymentProvider,
                              String idempotencyKey,
                              PaymentMethod paymentMethod) {
        this.transactionCode = transactionCode;
        this.schoolId = schoolId;
        this.subscriptionId = subscriptionId;
        this.onboardingId = onboardingId;
        this.amount = amount;
        this.currency = (currency != null && !currency.isBlank()) ? currency : "INR";
        this.paymentProvider = paymentProvider;
        this.idempotencyKey = idempotencyKey;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.INITIATED;
        this.initiatedAt = Instant.now();
    }

    public void markSuccess(String providerTransactionId, PaymentMethod method, Map<String, Object> rawCallback) {
        this.status = PaymentStatus.SUCCESS;
        if (providerTransactionId != null) {
            this.providerTransactionId = providerTransactionId;
        }
        if (method != null) {
            this.paymentMethod = method;
        }
        if (rawCallback != null) {
            this.rawCallback = rawCallback;
        }
        this.completedAt = Instant.now();
        this.failureReason = null;
    }

    public void markFailed(String reason, Map<String, Object> rawCallback) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        if (rawCallback != null) {
            this.rawCallback = rawCallback;
        }
        this.completedAt = Instant.now();
    }

    public void markRefunded(Map<String, Object> rawCallback) {
        this.status = PaymentStatus.REFUNDED;
        if (rawCallback != null) {
            this.rawCallback = rawCallback;
        }
    }

    public void markCancelled(String reason) {
        this.status = PaymentStatus.CANCELLED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }
}
