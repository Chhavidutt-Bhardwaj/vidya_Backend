package com.vidya.master.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps roles to permissions via universal section_code and action_code integers.
 * E.g., sectionCode: 4 (Students), 11 (Teachers), 20 (Community)
 * E.g., actionCode: -9 (View), -8 (Edit), -7 (Delete), -6 (Approve)
 */
@Entity
@Table(name = "community_role_permission",
       uniqueConstraints = @UniqueConstraint(name = "uq_role_section_action", columnNames = {"role_definition_id", "section_code", "action_code"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_definition_id", nullable = false)
    private CommunityRoleDefinition roleDefinition;

    @Column(name = "section_code", nullable = false)
    private int sectionCode;

    @Column(name = "action_code", nullable = false)
    private int actionCode;

    @Column(name = "granted", nullable = false)
    private boolean granted = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CommunityRolePermission(CommunityRoleDefinition roleDefinition, int sectionCode, int actionCode, boolean granted) {
        this.roleDefinition = roleDefinition;
        this.sectionCode = sectionCode;
        this.actionCode = actionCode;
        this.granted = granted;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isGranted() {
        return granted;
    }
}
