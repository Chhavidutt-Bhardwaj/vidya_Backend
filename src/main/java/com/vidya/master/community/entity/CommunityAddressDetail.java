package com.vidya.master.community.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Captures billing and operational address information for a community.
 *
 * <p>Separated from the core {@link Community} entity to allow independent
 * section-level access control.
 */
@Entity
@Table(name = "community_address_detail")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityAddressDetail extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false, unique = true)
    private Community community;

    // ── Registered / Billing Address ─────────────────────────────────────────

    @Column(name = "billing_address_line1")
    private String billingAddressLine1;

    @Column(name = "billing_address_line2")
    private String billingAddressLine2;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

    @Column(name = "billing_state", length = 100)
    private String billingState;

    @Column(name = "billing_pincode", length = 20)
    private String billingPincode;

    @Column(name = "billing_country", length = 100)
    private String billingCountry = "India";

    // ── Operational / Correspondence Address ─────────────────────────────────

    @Column(name = "operational_address_line1")
    private String operationalAddressLine1;

    @Column(name = "operational_address_line2")
    private String operationalAddressLine2;

    @Column(name = "operational_city", length = 100)
    private String operationalCity;

    @Column(name = "operational_state", length = 100)
    private String operationalState;

    @Column(name = "operational_pincode", length = 20)
    private String operationalPincode;

    @Column(name = "operational_country", length = 100)
    private String operationalCountry = "India";

    /** When true, operational address is same as billing address. */
    @Column(name = "same_as_billing", nullable = false)
    private boolean sameAsBilling = false;

    public CommunityAddressDetail(Community community) {
        this.community = community;
    }
}
