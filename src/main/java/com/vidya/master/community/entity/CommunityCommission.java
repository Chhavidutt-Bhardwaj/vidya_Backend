package com.vidya.master.community.entity;

import com.vidya.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Commission structure for a community.
 */
@Entity
@Table(name = "community_commission")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityCommission extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false, length = 30)
    private CommissionType commissionType;

    @Column(name = "commission_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal commissionRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_basis", nullable = false, length = 30)
    private CommissionBasis commissionBasis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tier_config", columnDefinition = "JSONB")
    private Map<String, Object> tierConfig;

    @Column(name = "minimum_threshold", precision = 15, scale = 2)
    private BigDecimal minimumThreshold;

    @Column(name = "maximum_cap", precision = 15, scale = 2)
    private BigDecimal maximumCap;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public CommunityCommission(Community community,
                                CommissionType commissionType,
                                BigDecimal commissionRate,
                                CommissionBasis commissionBasis,
                                Map<String, Object> tierConfig,
                                BigDecimal minimumThreshold,
                                BigDecimal maximumCap,
                                String paymentTerms,
                                LocalDate effectiveFrom,
                                LocalDate effectiveTo) {
        this.community = community;
        this.commissionType = commissionType;
        this.commissionRate = commissionRate;
        this.commissionBasis = commissionBasis;
        this.tierConfig = tierConfig;
        this.minimumThreshold = minimumThreshold;
        this.maximumCap = maximumCap;
        this.paymentTerms = paymentTerms;
        this.isActive = true;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
