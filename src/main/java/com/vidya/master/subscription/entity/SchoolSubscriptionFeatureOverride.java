package com.vidya.master.subscription.entity;

import com.vidya.master.school.entity.School;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Granular school-level feature and sub-feature override configured by SuperUsers or Platform Admins.
 * Allows toggling specific features (e.g. enable Teacher Payroll only, or disable Leave)
 * independently from the base subscription plan.
 */
@Entity
@Table(name = "school_subscription_feature_override")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolSubscriptionFeatureOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private SchoolSubscription subscription;

    @Column(name = "feature_code", nullable = false, length = 50)
    private String featureCode;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "custom_usage_limit")
    private Integer customUsageLimit;

    @Column(name = "addon_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal addonPrice = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "overridden_by")
    private UUID overriddenBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SchoolSubscriptionFeatureOverride(School school,
                                           SchoolSubscription subscription,
                                           String featureCode,
                                           boolean enabled,
                                           Integer customUsageLimit,
                                           BigDecimal addonPrice,
                                           String notes,
                                           UUID overriddenBy) {
        this.school = school;
        this.subscription = subscription;
        this.featureCode = featureCode;
        this.enabled = enabled;
        this.customUsageLimit = customUsageLimit;
        this.addonPrice = addonPrice != null ? addonPrice : BigDecimal.ZERO;
        this.notes = notes;
        this.overriddenBy = overriddenBy;
    }
}
