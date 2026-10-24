package com.vidya.master.community.entity;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log capturing every role assignment/revocation event
 * for community members.
 *
 * <p>Every time an authorised platform user:
 * <ul>
 *   <li>Assigns a {@link CommunityRoleDefinition} to a {@link CommunityMember}</li>
 *   <li>Revokes or changes a member's role</li>
 *   <li>Creates, updates, or deletes a role definition</li>
 * </ul>
 * …a row is inserted into this table recording who performed the action,
 * what changed, and when. Rows are NEVER modified after insert.
 *
 * <p>Access to this log is governed by role management VIEW permission.
 * Only PLATFORM_SUPER_ADMIN and authorised PLATFORM_ADMIN can see cross-community logs.
 */
@Entity
@Table(name = "community_role_audit_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityRoleAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Community in which the role action occurred. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false, updatable = false)
    private Community community;

    /** The platform user who performed the action (granter/revoker). */
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    /** Human-readable name of the actor (snapshotted at event time). */
    @Column(name = "actor_name", length = 255, updatable = false)
    private String actorName;

    /** The member affected by the action (null for role definition changes). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_member_id", updatable = false)
    private CommunityMember targetMember;

    /** The role definition that was created/updated/deleted/assigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_definition_id", updatable = false)
    private CommunityRoleDefinition roleDefinition;

    /** Snapshot of the role name at event time (stable even if role is later renamed). */
    @Column(name = "role_name_snapshot", length = 100, updatable = false)
    private String roleNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50, updatable = false)
    private RoleAuditAction action;

    /** Free-text reason provided by the actor when performing the action. */
    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    /** Previous role name (for ROLE_ASSIGNED/ROLE_CHANGED events). */
    @Column(name = "previous_role_name", length = 100, updatable = false)
    private String previousRoleName;

    /** IP address of the actor at event time. */
    @Column(name = "actor_ip_address", length = 50, updatable = false)
    private String actorIpAddress;

    @CreatedDate
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public CommunityRoleAuditLog(Community community,
                                  UUID actorUserId,
                                  String actorName,
                                  CommunityMember targetMember,
                                  CommunityRoleDefinition roleDefinition,
                                  String roleNameSnapshot,
                                  RoleAuditAction action,
                                  String reason,
                                  String previousRoleName,
                                  String actorIpAddress) {
        this.community = community;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.targetMember = targetMember;
        this.roleDefinition = roleDefinition;
        this.roleNameSnapshot = roleNameSnapshot;
        this.action = action;
        this.reason = reason;
        this.previousRoleName = previousRoleName;
        this.actorIpAddress = actorIpAddress;
    }

    /** Enumeration of all role-related audit events. */
    public enum RoleAuditAction {
        /** A new role definition was created via the admin panel. */
        ROLE_CREATED,
        /** An existing role definition was renamed or its description changed. */
        ROLE_UPDATED,
        /** A role definition was deactivated (soft-delete). */
        ROLE_DEACTIVATED,
        /** A role definition was reactivated. */
        ROLE_REACTIVATED,
        /** Permissions were modified for a role definition. */
        PERMISSIONS_UPDATED,
        /** A member was assigned a role (initial assignment). */
        ROLE_ASSIGNED,
        /** A member's role was changed from one to another. */
        ROLE_CHANGED,
        /** A member's role was revoked (set to unassigned). */
        ROLE_REVOKED
    }
}
