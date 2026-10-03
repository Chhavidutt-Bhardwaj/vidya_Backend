package com.vidya.master.community.entity;

import com.vidya.common.audit.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a partner organization, NGO, or field agency that facilitates
 * school onboarding and field operations.
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

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 20)
    private String pincode;

    @Column(name = "country", nullable = false, length = 100)
    private String country = "India";

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CommunityMember> members = new HashSet<>();

    public Community(String communityCode,
                     String name,
                     String description,
                     CommunityType type,
                     CommunityStatus status,
                     String contactEmail,
                     String contactPhone,
                     String addressLine1,
                     String addressLine2,
                     String city,
                     String state,
                     String pincode,
                     String country) {
        this.communityCode = communityCode;
        this.name = name;
        this.description = description;
        this.type = type != null ? type : CommunityType.OTHER;
        this.status = status != null ? status : CommunityStatus.ACTIVE;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.country = country != null ? country : "India";
    }

    public void addMember(CommunityMember member) {
        members.add(member);
        member.setCommunity(this);
    }

    public void removeMember(CommunityMember member) {
        members.remove(member);
        member.setCommunity(null);
    }
}
