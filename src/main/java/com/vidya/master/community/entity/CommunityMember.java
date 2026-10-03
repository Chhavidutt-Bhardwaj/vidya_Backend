package com.vidya.master.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Links a platform user to a community with a specific role.
 */
@Entity
@Table(name = "community_member")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityMember {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private CommunityMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CommunityMemberStatus status = CommunityMemberStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public CommunityMember(Community community,
                           UUID platformUserId,
                           String memberCode,
                           CommunityMemberRole role) {
        this.community = community;
        this.platformUserId = platformUserId;
        this.memberCode = memberCode;
        this.role = role != null ? role : CommunityMemberRole.FIELD_AGENT;
        this.status = CommunityMemberStatus.ACTIVE;
        this.joinedAt = Instant.now();
    }
}
