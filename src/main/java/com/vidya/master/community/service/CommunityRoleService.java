package com.vidya.master.community.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.community.dto.AssignCommunityRoleRequest;
import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityRoleAuditResponse;
import com.vidya.master.community.dto.CommunityRoleResponse;
import com.vidya.master.community.dto.CreateCommunityRoleRequest;
import com.vidya.master.community.dto.MyPermissionsResponse;
import com.vidya.master.community.dto.RolePermissionEntry;
import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityRoleAuditLog;
import com.vidya.master.community.entity.CommunityRoleDefinition;
import com.vidya.master.community.entity.CommunityRolePermission;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.community.mapper.CommunityMapper;
import com.vidya.master.community.mapper.CommunityRoleMapper;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.community.repository.CommunityRoleAuditLogRepository;
import com.vidya.master.community.repository.CommunityRoleDefinitionRepository;
import com.vidya.master.community.repository.CommunityRolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing community custom roles and universal integer permission matrices.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityRoleService {

    private final CommunityRoleDefinitionRepository roleDefinitionRepository;
    private final CommunityRolePermissionRepository rolePermissionRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityRoleAuditLogRepository roleAuditLogRepository;
    private final CommunityRoleMapper roleMapper;
    private final CommunityMapper communityMapper;

    /**
     * Create a new custom role definition with its permission matrix.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public CommunityRoleResponse createRole(CreateCommunityRoleRequest request) {
        CommunityType communityType = request.communityType() != null ? request.communityType() : CommunityType.OTHER;
        log.info("Creating custom role: [{}] for community type: [{}]", request.roleName(), communityType);

        String roleCode = request.roleCode();
        if (roleCode == null || roleCode.isBlank()) {
            roleCode = "ROLE_" + communityType.name() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        if (roleDefinitionRepository.existsByRoleCode(roleCode)) {
            throw new DuplicateResourceException("CommunityRoleDefinition", "roleCode", roleCode);
        }

        if (roleDefinitionRepository.existsByRoleNameAndCommunityType(request.roleName(), communityType)) {
            throw new DuplicateResourceException("CommunityRoleDefinition", "roleName", request.roleName());
        }

        CommunityRoleDefinition role = new CommunityRoleDefinition(
                roleCode,
                request.roleName(),
                request.description(),
                communityType,
                false,
                request.level() != null ? request.level() : 1
        );

        if (request.permissions() != null) {
            for (RolePermissionEntry perm : request.permissions()) {
                if (perm.sectionCode() != null && perm.actionCode() != null) {
                    role.getPermissions().add(new CommunityRolePermission(
                            role, perm.sectionCode(), perm.actionCode(), perm.granted()));
                }
            }
        }

        CommunityRoleDefinition saved = roleDefinitionRepository.save(role);
        log.info("Created custom role [{}] with ID: [{}]", saved.getRoleCode(), saved.getId());
        return roleMapper.toResponse(saved);
    }

    /**
     * Get all role definitions for a specific community type.
     */
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<CommunityRoleResponse> getRolesByCommunityType(CommunityType communityType) {
        return roleDefinitionRepository.findByCommunityTypeAndIsActiveTrue(communityType)
                .stream()
                .map(roleMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a role definition by ID.
     */
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public CommunityRoleResponse getRoleById(UUID roleId) {
        CommunityRoleDefinition role = roleDefinitionRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityRoleDefinition", roleId));
        return roleMapper.toResponse(role);
    }

    /**
     * Get a role definition by role code.
     */
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public CommunityRoleResponse getRoleByCode(String roleCode) {
        CommunityRoleDefinition role = roleDefinitionRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityRoleDefinition", roleCode));
        return roleMapper.toResponse(role);
    }

    /**
     * Update an existing custom role definition and its permission matrix.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public CommunityRoleResponse updateRole(UUID roleId, CreateCommunityRoleRequest request) {
        CommunityRoleDefinition role = roleDefinitionRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityRoleDefinition", roleId));

        if (role.isSystem()) {
            throw new BusinessException("System roles cannot be modified");
        }

        role.setRoleName(request.roleName());
        role.setRoleDescription(request.description());
        if (request.level() != null) {
            role.setLevel(request.level());
        }

        if (request.permissions() != null) {
            role.getPermissions().clear();
            for (RolePermissionEntry perm : request.permissions()) {
                if (perm.sectionCode() != null && perm.actionCode() != null) {
                    role.getPermissions().add(new CommunityRolePermission(
                            role, perm.sectionCode(), perm.actionCode(), perm.granted()));
                }
            }
        }

        CommunityRoleDefinition updated = roleDefinitionRepository.save(role);
        log.info("Updated role [{}]", updated.getRoleCode());
        return roleMapper.toResponse(updated);
    }

    /**
     * Update granular permissions for a role.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public CommunityRoleResponse updateRolePermissions(UUID roleId, List<RolePermissionEntry> permissions) {
        CommunityRoleDefinition role = roleDefinitionRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityRoleDefinition", roleId));

        if (role.isSystem()) {
            throw new BusinessException("System roles cannot be modified");
        }

        role.getPermissions().clear();
        if (permissions != null) {
            for (RolePermissionEntry perm : permissions) {
                if (perm.sectionCode() != null && perm.actionCode() != null) {
                    role.getPermissions().add(new CommunityRolePermission(
                            role, perm.sectionCode(), perm.actionCode(), perm.granted()));
                }
            }
        }

        CommunityRoleDefinition updated = roleDefinitionRepository.save(role);
        return roleMapper.toResponse(updated);
    }

    /**
     * Deactivate a custom role.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public void deactivateRole(UUID roleId) {
        CommunityRoleDefinition role = roleDefinitionRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityRoleDefinition", roleId));

        if (role.isSystem()) {
            throw new BusinessException("System roles cannot be deactivated");
        }

        role.setIsActive(false);
        roleDefinitionRepository.save(role);
        log.info("Deactivated role: [{}]", role.getRoleCode());
    }

    /**
     * Delete a custom non-system role.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public void deleteRole(UUID roleId) {
        CommunityRoleDefinition role = roleDefinitionRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityRoleDefinition", roleId));

        if (role.isSystem()) {
            throw new BusinessException("System roles cannot be deleted");
        }

        roleDefinitionRepository.delete(role);
        log.info("Deleted role: [{}]", role.getRoleCode());
    }

    /**
     * Assign a custom role to a community member.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public CommunityMemberResponse assignRoleToMember(AssignCommunityRoleRequest request) {
        CommunityMember member = communityMemberRepository.findById(request.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("CommunityMember", request.memberId()));

        CommunityRoleDefinition role = roleDefinitionRepository.findById(request.roleDefinitionId())
                .orElseThrow(() -> new ResourceNotFoundException("CommunityRoleDefinition", request.roleDefinitionId()));

        member.setCustomRole(role);
        CommunityMember saved = communityMemberRepository.save(member);
        log.info("Assigned role [{}] to member [{}]", role.getRoleCode(), member.getId());
        return communityMapper.toMemberResponse(saved);
    }

    /**
     * Resolve effective permissions for the currently authenticated user in a community.
     */
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public MyPermissionsResponse getMyPermissions(UUID platformUserId) {
        List<CommunityMember> members = communityMemberRepository.findByPlatformUserId(platformUserId);
        if (members.isEmpty()) {
            return new MyPermissionsResponse(null, "NO_ROLE", Map.of());
        }

        CommunityMember primaryMember = members.get(0);
        Map<Integer, Set<Integer>> matrix = new HashMap<>();

        if (primaryMember.getCustomRole() != null && primaryMember.getCustomRole().getPermissions() != null) {
            for (CommunityRolePermission perm : primaryMember.getCustomRole().getPermissions()) {
                if (perm.isGranted()) {
                    matrix.computeIfAbsent(perm.getSectionCode(), k -> new HashSet<>())
                          .add(perm.getActionCode());
                }
            }
        }

        String roleName = primaryMember.getCustomRole() != null
                ? primaryMember.getCustomRole().getRoleName()
                : primaryMember.getRole() != null ? primaryMember.getRole().name() : "MEMBER";

        return new MyPermissionsResponse(primaryMember.getId(), roleName, matrix);
    }

    /**
     * Retrieve audit logs for role changes in a community.
     */
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Page<CommunityRoleAuditResponse> getAuditLogs(UUID communityId, Pageable pageable) {
        return roleAuditLogRepository.findByCommunityId(communityId, pageable)
                .map(roleMapper::toAuditResponse);
    }
}
