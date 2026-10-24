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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a platform user to a community with a specific community-defined role.
 *
 * <p>Members are now assigned a {@link CommunityRoleDefinition} instead of the
 * deprecated hardcoded {@link CommunityMemberRole} enum. The legacy {@code role}
 * field is retained as nullable to allow zero-downtime migration from existing data.
 *
 * <p>The effective permissions for a member are resolved at runtime from
 * the {@code customRole} relationship and its associated {@link CommunityRolePermission} entries.
 */
@Entity
@Table(name = "community_member")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityMember extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(name = "platform_user_id", nullable = false)
    private UUID platformUserId;

    @Column(name = "member_code", nullable = false, unique = true, length = 50, updatable = false)
    private String memberCode;

    /**
     * The custom role assigned to this member — defined and managed via the admin panel.
     * Replaces the deprecated {@code role} enum.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_role_id")
    private CommunityRoleDefinition customRole;

    /**
     * Legacy hardcoded role — kept for backward compatibility.
     * New assignments use {@code customRole}; this field is nullable.
     *
     * @deprecated Use {@link #customRole} instead.
     */
    @Deprecated
    @Enumerated(EnumType.STRING)
    @Column(name = "legacy_role", length = 30)
    private CommunityMemberRole legacyRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CommunityMemberStatus status = CommunityMemberStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    /** Timestamp of the member's most recent successful login. */
    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    public CommunityMember(Community community,
                           UUID platformUserId,
                           String memberCode,
                           CommunityRoleDefinition customRole) {
        this.community = community;
        this.platformUserId = platformUserId;
        this.memberCode = memberCode;
        this.customRole = customRole;
        this.status = CommunityMemberStatus.ACTIVE;
        this.joinedAt = Instant.now();
    }

    public CommunityMember(Community community,
                           UUID platformUserId,
                           String memberCode,
                           CommunityMemberRole legacyRole) {
        this.community = community;
        this.platformUserId = platformUserId;
        this.memberCode = memberCode;
        this.legacyRole = legacyRole;
        this.status = CommunityMemberStatus.ACTIVE;
        this.joinedAt = Instant.now();
    }

    public CommunityMemberRole getRole() {
        return legacyRole;
    }

    public void setRole(CommunityMemberRole role) {
        this.legacyRole = role;
    }

    /** Returns the effective role name for display — custom role name or legacy enum. */
    public String getEffectiveRoleName() {
        if (customRole != null) {
            return customRole.getRoleName();
        }
        return legacyRole != null ? legacyRole.name() : "UNASSIGNED";
    }
}
