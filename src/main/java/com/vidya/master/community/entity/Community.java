package com.vidya.master.community.entity;

import com.vidya.common.audit.AuditableEntity;
import com.vidya.master.hub.entity.Hub;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the core identity of a partner organization, NGO, or field agency
 * that facilitates school onboarding and field operations.
 *
 * <p>Personal details, address details, bank accounts, payroll, and commission
 * are separated into dedicated sub-tables for modularity and granular RBAC.
 */
@Entity
@Table(name = "community")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Community extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "community_code", nullable = false, unique = true, length = 50, updatable = false)
    private String communityCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private CommunityType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CommunityStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id")
    private Hub hub;

    @Column(name = "is_hub_lead", nullable = false)
    private boolean hubLead = false;

    @Column(name = "max_active_query_limit", nullable = false)
    private int maxActiveQueryLimit = 10;

    @Column(name = "max_assigned_schools_limit", nullable = false)
    private int maxAssignedSchoolsLimit = 25;

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CommunityMember> members = new HashSet<>();

    @OneToOne(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CommunityPersonalDetail personalDetail;

    @OneToOne(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CommunityProfessionalDetail professionalDetail;

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CommunityAddress> addresses = new HashSet<>();

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CommunityBankDetail> bankDetails = new HashSet<>();

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CommunityPayroll> payrolls = new HashSet<>();

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CommunityCommission> commissions = new HashSet<>();

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CommunityLoginHistory> loginHistory = new HashSet<>();

    public Community(String communityCode,
                     String name,
                     String description,
                     CommunityType type,
                     CommunityStatus status) {
        this.communityCode = communityCode;
        this.name = name;
        this.description = description;
        this.type = type != null ? type : CommunityType.OTHER;
        this.status = status != null ? status : CommunityStatus.ACTIVE;
    }

    public void addMember(CommunityMember member) {
        members.add(member);
    }

    public void removeMember(CommunityMember member) {
        members.remove(member);
    }

    public void addAddress(CommunityAddress address) {
        addresses.add(address);
    }

    public void removeAddress(CommunityAddress address) {
        addresses.remove(address);
    }

    public void addBankDetail(CommunityBankDetail bankDetail) {
        bankDetails.add(bankDetail);
    }

    public void removeBankDetail(CommunityBankDetail bankDetail) {
        bankDetails.remove(bankDetail);
    }
}
