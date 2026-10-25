package com.vidya.master.subscription.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Versioned subscription plan offered on the Vidya SaaS platform.
 *
 * <p>Key invariant: A plan version with active subscribers is NEVER mutated.
 * Upgrades or pricing changes create a new version row with the same {@code planCode}
 * and an incremented {@code version}.
 */
@Entity
@Table(name = "subscription_plan")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "plan_code", nullable = false, length = 50, updatable = false)
    private String planCode;

    @Column(name = "version", nullable = false, updatable = false)
    private Integer version = 1;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 30)
    private BillingCycle billingCycle;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "trial_days", nullable = false)
    private int trialDays = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 30)
    private PlanTier tier = PlanTier.CUSTOM;

    @Column(name = "is_custom", nullable = false)
    private boolean isCustom = false;

    @Column(name = "target_school_id")
    private UUID targetSchoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version_lock", nullable = false)
    private Long versionLock = 0L;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SubscriptionPlanFeature> features = new HashSet<>();

    public SubscriptionPlan(String planCode,
                            Integer version,
                            String name,
                            String description,
                            BillingCycle billingCycle,
                            BigDecimal basePrice,
                            String currency,
                            int trialDays,
                            PlanStatus status,
                            LocalDate effectiveFrom) {
        this(planCode, version, name, description, billingCycle, basePrice, currency, trialDays,
             PlanTier.CUSTOM, false, null, status, effectiveFrom);
    }

    public SubscriptionPlan(String planCode,
                            Integer version,
                            String name,
                            String description,
                            BillingCycle billingCycle,
                            BigDecimal basePrice,
                            String currency,
                            int trialDays,
                            PlanTier tier,
                            boolean isCustom,
                            UUID targetSchoolId,
                            PlanStatus status,
                            LocalDate effectiveFrom) {
        this.planCode = planCode;
        this.version = version != null ? version : 1;
        this.name = name;
        this.description = description;
        this.billingCycle = billingCycle;
        this.basePrice = basePrice;
        this.currency = currency != null ? currency : "INR";
        this.trialDays = trialDays;
        this.tier = tier != null ? tier : PlanTier.CUSTOM;
        this.isCustom = isCustom;
        this.targetSchoolId = targetSchoolId;
        this.status = status != null ? status : PlanStatus.DRAFT;
        this.effectiveFrom = effectiveFrom != null ? effectiveFrom : LocalDate.now();
    }

    public void addFeature(SubscriptionPlanFeature planFeature) {
        features.add(planFeature);
        planFeature.setPlan(this);
    }

    public void removeFeature(SubscriptionPlanFeature planFeature) {
        features.remove(planFeature);
        planFeature.setPlan(null);
    }
}
