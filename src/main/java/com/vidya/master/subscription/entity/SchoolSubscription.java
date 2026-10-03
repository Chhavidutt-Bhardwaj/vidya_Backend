package com.vidya.master.subscription.entity;

import com.vidya.common.audit.AuditableEntity;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * A school's active or historical subscription instance.
 *
 * <p>Key design: {@code planCode} and {@code planVersion} are denormalized snapshots
 * from {@link SubscriptionPlan} at the time of purchase to ensure billing accuracy
 * even if the underlying plan is upgraded or archived.
 */
@Entity
@Table(name = "school_subscription")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolSubscription extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subscription_code", nullable = false, unique = true, length = 50, updatable = false)
    private String subscriptionCode;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "plan_code", nullable = false, length = 50, updatable = false)
    private String planCode;

    @Column(name = "plan_version", nullable = false, updatable = false)
    private Integer planVersion;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 30)
    private BillingCycle billingCycle;

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    public SchoolSubscription(String subscriptionCode,
                              UUID schoolId,
                              SubscriptionPlan plan,
                              String planCode,
                              Integer planVersion,
                              LocalDate startDate,
                              LocalDate endDate,
                              BillingCycle billingCycle,
                              BigDecimal baseAmount,
                              BigDecimal discountAmount,
                              BigDecimal taxAmount,
                              BigDecimal finalAmount,
                              String currency,
                              SubscriptionStatus status,
                              boolean autoRenew,
                              LocalDate trialEndDate) {
        this.subscriptionCode = subscriptionCode;
        this.schoolId = schoolId;
        this.plan = plan;
        this.planCode = planCode;
        this.planVersion = planVersion;
        this.startDate = startDate;
        this.endDate = endDate;
        this.billingCycle = billingCycle;
        this.baseAmount = baseAmount;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.finalAmount = finalAmount;
        this.currency = currency != null ? currency : "INR";
        this.status = status != null ? status : SubscriptionStatus.TRIAL;
        this.autoRenew = autoRenew;
        this.trialEndDate = trialEndDate;
    }
}
