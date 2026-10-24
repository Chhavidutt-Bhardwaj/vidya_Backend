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
 * Custom role definitions per community type.
 * Allows each community type (PARTNER, FIELD_AGENCY, NGO, GOVT, OTHER)
 * to have its own customizable roles with granular permissions.
 */
@Entity
@Table(name = "community_role_definition")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityRoleDefinition extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "role_code", nullable = false, unique = true, length = 50, updatable = false)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "role_description", columnDefinition = "TEXT")
    private String roleDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "community_type", nullable = false, length = 30)
    private CommunityType communityType;

    @Column(name = "is_system_role", nullable = false)
    private Boolean isSystemRole = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "level", nullable = false)
    private Integer level = 1;

    @OneToMany(mappedBy = "roleDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CommunityRolePermission> permissions = new HashSet<>();

    public CommunityRoleDefinition(String roleCode,
                                    String roleName,
                                    String roleDescription,
                                    CommunityType communityType,
                                    Boolean isSystemRole,
                                    Integer level) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.roleDescription = roleDescription;
        this.communityType = communityType != null ? communityType : CommunityType.OTHER;
        this.isSystemRole = isSystemRole != null ? isSystemRole : false;
        this.isActive = true;
        this.level = level != null ? level : 1;
    }

    public CommunityRoleDefinition(Community community, String roleName, String description, boolean isSystem) {
        this(
                "ROLE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                roleName,
                description,
                community != null ? community.getType() : CommunityType.OTHER,
                isSystem,
                1
        );
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public boolean isSystem() {
        return Boolean.TRUE.equals(isSystemRole);
    }

    public String getDescription() {
        return roleDescription;
    }
}
