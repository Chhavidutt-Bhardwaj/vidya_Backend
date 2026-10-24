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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Professional/business details for a community.
 */
@Entity
@Table(name = "community_professional_detail")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityProfessionalDetail extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "community_id", nullable = false, unique = true)
    private Community community;

    @Column(name = "organization_type", length = 100)
    private String organizationType;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "business_registration_number", length = 50)
    private String businessRegistrationNumber;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "company_website", length = 255)
    private String companyWebsite;

    @Column(name = "linkedin_profile", length = 255)
    private String linkedinProfile;

    @Column(name = "years_in_operation")
    private Integer yearsInOperation;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "annual_revenue_range", length = 50)
    private AnnualRevenueRange annualRevenueRange;

    public CommunityProfessionalDetail(Community community,
                                        String organizationType,
                                        String industry,
                                        String businessRegistrationNumber,
                                        String gstNumber,
                                        String companyWebsite,
                                        String linkedinProfile,
                                        Integer yearsInOperation,
                                        Integer employeeCount,
                                        AnnualRevenueRange annualRevenueRange) {
        this.community = community;
        this.organizationType = organizationType;
        this.industry = industry;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.gstNumber = gstNumber;
        this.companyWebsite = companyWebsite;
        this.linkedinProfile = linkedinProfile;
        this.yearsInOperation = yearsInOperation;
        this.employeeCount = employeeCount;
        this.annualRevenueRange = annualRevenueRange;
    }
}
