package com.vidya.master.hub.entity;

import com.vidya.common.audit.AuditableEntity;
import com.vidya.master.community.entity.Community;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Regional or state-level operational hub coordinating communities.
 *
 * <p>Hierarchy:
 * {@code Platform Users} &rarr; {@code Hub Manager / Sub-Manager} &rarr; {@code Community (School RM)} &rarr; {@code Schools}
 *
 * <p>Each Hub is assigned a lead Community organization, a designated Hub Manager,
 * and a Hub Sub-Manager (deputy) who holds equivalent authority in the absence of the primary manager.
 */
@Entity
@Table(name = "hub")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hub extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "hub_code", nullable = false, unique = true, length = 50, updatable = false)
    private String hubCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private HubStatus status = HubStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_community_id")
    private Community leadCommunity;

    @Column(name = "hub_manager_user_id")
    private UUID hubManagerUserId;

    @Column(name = "hub_manager_name", length = 200)
    private String hubManagerName;

    @Column(name = "hub_sub_manager_user_id")
    private UUID hubSubManagerUserId;

    @Column(name = "hub_sub_manager_name", length = 200)
    private String hubSubManagerName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @OneToMany(mappedBy = "hub")
    private Set<Community> communities = new HashSet<>();

    public Hub(String hubCode,
               String name,
               String state,
               String region,
               String description,
               Community leadCommunity,
               UUID hubManagerUserId,
               String hubManagerName,
               UUID hubSubManagerUserId,
               String hubSubManagerName,
               String contactEmail,
               String contactPhone) {
        this.hubCode = hubCode;
        this.name = name;
        this.state = state;
        this.region = region;
        this.description = description;
        this.leadCommunity = leadCommunity;
        this.hubManagerUserId = hubManagerUserId;
        this.hubManagerName = hubManagerName;
        this.hubSubManagerUserId = hubSubManagerUserId;
        this.hubSubManagerName = hubSubManagerName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.status = HubStatus.ACTIVE;
    }
}
