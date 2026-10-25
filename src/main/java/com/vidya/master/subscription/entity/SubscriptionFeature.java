package com.vidya.master.subscription.entity;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform feature catalog item available for packaging into plans.
 */
@Entity
@Table(name = "subscription_feature")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "feature_code", nullable = false, unique = true, length = 50, updatable = false)
    private String featureCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private FeatureCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FeatureStatus status = FeatureStatus.ACTIVE;

    @Column(name = "parent_module_code", length = 50)
    private String parentModuleCode;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal basePrice = java.math.BigDecimal.ZERO;

    @Column(name = "is_addon", nullable = false)
    private boolean addon = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public SubscriptionFeature(String featureCode, String name, String description, FeatureCategory category) {
        this(featureCode, name, description, category, null, null, java.math.BigDecimal.ZERO, false, 0);
    }

    public SubscriptionFeature(String featureCode, String name, String description, FeatureCategory category,
                               String parentModuleCode, String subCategory, java.math.BigDecimal basePrice,
                               boolean addon, int displayOrder) {
        this.featureCode = featureCode;
        this.name = name;
        this.description = description;
        this.category = category;
        this.parentModuleCode = parentModuleCode;
        this.subCategory = subCategory;
        this.basePrice = basePrice != null ? basePrice : java.math.BigDecimal.ZERO;
        this.addon = addon;
        this.displayOrder = displayOrder;
        this.status = FeatureStatus.ACTIVE;
    }
}
