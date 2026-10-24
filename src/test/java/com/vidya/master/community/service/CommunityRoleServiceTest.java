package com.vidya.master.community.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.community.dto.AssignCommunityRoleRequest;
import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityRoleResponse;
import com.vidya.master.community.dto.CreateCommunityRoleRequest;
import com.vidya.master.community.dto.MyPermissionsResponse;
import com.vidya.master.community.dto.RolePermissionEntry;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityRoleDefinition;
import com.vidya.master.community.entity.CommunityRolePermission;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.community.mapper.CommunityMapper;
import com.vidya.master.community.mapper.CommunityRoleMapper;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.community.repository.CommunityRoleAuditLogRepository;
import com.vidya.master.community.repository.CommunityRoleDefinitionRepository;
import com.vidya.master.community.repository.CommunityRolePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityRoleService — Custom Roles and Universal Integer Permission Matrix Tests")
class CommunityRoleServiceTest {

    @Mock
    private CommunityRoleDefinitionRepository roleDefinitionRepository;

    @Mock
    private CommunityRolePermissionRepository rolePermissionRepository;

    @Mock
    private CommunityMemberRepository communityMemberRepository;

    @Mock
    private CommunityRoleAuditLogRepository roleAuditLogRepository;

    @Spy
    private CommunityRoleMapper roleMapper = new CommunityRoleMapper();

    @Spy
    private CommunityMapper communityMapper = new CommunityMapper();

    @InjectMocks
    private CommunityRoleService roleService;

    private CommunityRoleDefinition sampleRole;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        roleId = UUID.randomUUID();
        sampleRole = new CommunityRoleDefinition(
                "NGO_COORDINATOR",
                "Program Coordinator",
                "Coordinates admissions and programs",
                CommunityType.NGO,
                false,
                2
        );
        sampleRole.setId(roleId);
        sampleRole.getPermissions().add(new CommunityRolePermission(sampleRole, 4, -9, true));
        sampleRole.getPermissions().add(new CommunityRolePermission(sampleRole, 4, -8, true));
        sampleRole.getPermissions().add(new CommunityRolePermission(sampleRole, 21, -9, true));
    }

    @Test
    @DisplayName("createRole should save role and permission entries and return response")
    void createRole_Success() {
        CreateCommunityRoleRequest request = new CreateCommunityRoleRequest(
                "NGO_LEAD",
                "Lead Coordinator",
                "Manages regional operations",
                CommunityType.NGO,
                1,
                List.of(
                        new RolePermissionEntry(4, -9, true),
                        new RolePermissionEntry(4, -8, true),
                        new RolePermissionEntry(20, -9, true)
                )
        );

        when(roleDefinitionRepository.existsByRoleCode(request.roleCode())).thenReturn(false);
        when(roleDefinitionRepository.existsByRoleNameAndCommunityType(request.roleName(), request.communityType())).thenReturn(false);
        when(roleDefinitionRepository.save(any(CommunityRoleDefinition.class))).thenAnswer(inv -> {
            CommunityRoleDefinition r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        CommunityRoleResponse response = roleService.createRole(request);

        assertThat(response).isNotNull();
        assertThat(response.roleName()).isEqualTo("Lead Coordinator");
        assertThat(response.communityType()).isEqualTo(CommunityType.NGO);
        assertThat(response.permissionsMatrix()).containsKey(4);
        assertThat(response.permissionsMatrix().get(4)).contains(-9, -8);
        assertThat(response.permissionsMatrix().get(20)).contains(-9);
    }

    @Test
    @DisplayName("createRole with duplicate role code should throw DuplicateResourceException")
    void createRole_DuplicateCode_ThrowsException() {
        CreateCommunityRoleRequest request = new CreateCommunityRoleRequest(
                "NGO_COORDINATOR",
                "New Name",
                "Desc",
                CommunityType.NGO,
                1,
                List.of()
        );

        when(roleDefinitionRepository.existsByRoleCode(request.roleCode())).thenReturn(true);

        assertThatThrownBy(() -> roleService.createRole(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("getRolesByCommunityType should return active roles for community type")
    void getRolesByCommunityType_Success() {
        when(roleDefinitionRepository.findByCommunityTypeAndIsActiveTrue(CommunityType.NGO))
                .thenReturn(List.of(sampleRole));

        List<CommunityRoleResponse> result = roleService.getRolesByCommunityType(CommunityType.NGO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roleCode()).isEqualTo("NGO_COORDINATOR");
        assertThat(result.get(0).permissionsMatrix().get(4)).contains(-9, -8);
    }

    @Test
    @DisplayName("getRoleById should return role when found")
    void getRoleById_Success() {
        when(roleDefinitionRepository.findById(roleId)).thenReturn(Optional.of(sampleRole));

        CommunityRoleResponse response = roleService.getRoleById(roleId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(roleId);
        assertThat(response.roleName()).isEqualTo("Program Coordinator");
    }

    @Test
    @DisplayName("updateRolePermissions should replace permissions matrix")
    void updateRolePermissions_Success() {
        when(roleDefinitionRepository.findById(roleId)).thenReturn(Optional.of(sampleRole));
        when(roleDefinitionRepository.save(any(CommunityRoleDefinition.class))).thenReturn(sampleRole);

        List<RolePermissionEntry> newPermissions = List.of(
                new RolePermissionEntry(11, -9, true),
                new RolePermissionEntry(11, -8, true)
        );

        CommunityRoleResponse response = roleService.updateRolePermissions(roleId, newPermissions);

        assertThat(response).isNotNull();
        assertThat(sampleRole.getPermissions()).hasSize(2);
    }

    @Test
    @DisplayName("deactivateRole on system role should throw BusinessException")
    void deactivateRole_SystemRole_ThrowsException() {
        sampleRole.setIsSystemRole(true);
        when(roleDefinitionRepository.findById(roleId)).thenReturn(Optional.of(sampleRole));

        assertThatThrownBy(() -> roleService.deactivateRole(roleId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System roles cannot be deactivated");
    }

    @Test
    @DisplayName("assignRoleToMember should bind role to community member")
    void assignRoleToMember_Success() {
        UUID memberId = UUID.randomUUID();
        Community community = new Community("COM-1", "NGO 1", "Desc", CommunityType.NGO, CommunityStatus.ACTIVE);
        community.setId(UUID.randomUUID());

        CommunityMember member = new CommunityMember(community, UUID.randomUUID(), "MEM-01", (CommunityRoleDefinition) null);
        member.setId(memberId);

        when(communityMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(roleDefinitionRepository.findById(roleId)).thenReturn(Optional.of(sampleRole));
        when(communityMemberRepository.save(any(CommunityMember.class))).thenReturn(member);

        AssignCommunityRoleRequest request = new AssignCommunityRoleRequest(memberId, roleId, "Promoted to Coordinator");
        CommunityMemberResponse response = roleService.assignRoleToMember(request);

        assertThat(response).isNotNull();
        assertThat(member.getCustomRole()).isEqualTo(sampleRole);
    }

    @Test
    @DisplayName("getMyPermissions should resolve permissions matrix for member")
    void getMyPermissions_Success() {
        UUID userId = UUID.randomUUID();
        Community community = new Community("COM-1", "NGO 1", "Desc", CommunityType.NGO, CommunityStatus.ACTIVE);
        community.setId(UUID.randomUUID());

        CommunityMember member = new CommunityMember(community, userId, "MEM-01", sampleRole);
        member.setId(UUID.randomUUID());

        when(communityMemberRepository.findByPlatformUserId(userId)).thenReturn(List.of(member));

        MyPermissionsResponse response = roleService.getMyPermissions(userId);

        assertThat(response).isNotNull();
        assertThat(response.roleName()).isEqualTo("Program Coordinator");
        assertThat(response.permissionsMatrix()).containsKey(4);
        assertThat(response.permissionsMatrix().get(4)).contains(-9, -8);
    }
}
