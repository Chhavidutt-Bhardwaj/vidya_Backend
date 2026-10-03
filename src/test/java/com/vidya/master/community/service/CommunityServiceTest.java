package com.vidya.master.community.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.community.dto.AddCommunityMemberRequest;
import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityResponse;
import com.vidya.master.community.dto.CreateCommunityRequest;
import com.vidya.master.community.dto.UpdateCommunityMemberRoleRequest;
import com.vidya.master.community.dto.UpdateCommunityRequest;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityMemberRole;
import com.vidya.master.community.entity.CommunityMemberStatus;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.community.mapper.CommunityMapper;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.community.repository.CommunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private CommunityMemberRepository communityMemberRepository;

    @Spy
    private CommunityMapper communityMapper = new CommunityMapper();

    @InjectMocks
    private CommunityServiceImpl communityService;

    private Community sampleCommunity;
    private UUID communityId;

    @BeforeEach
    void setUp() {
        communityId = UUID.randomUUID();
        sampleCommunity = new Community(
                "COM-12345678",
                "EduPartners Agency",
                "Agency description",
                CommunityType.FIELD_AGENCY,
                CommunityStatus.ACTIVE,
                "contact@edupartners.org",
                "+919876543210",
                "123 Street",
                "Near Tower",
                "New Delhi",
                "Delhi",
                "110001",
                "India"
        );
        sampleCommunity.setId(communityId);
    }

    @Test
    @DisplayName("createCommunity should save and return CommunityResponse")
    void createCommunity_Success() {
        CreateCommunityRequest request = new CreateCommunityRequest(
                "New Field Agency",
                "Description",
                CommunityType.FIELD_AGENCY,
                "agency@vidya.io",
                "+919999999999",
                "Address 1",
                "Address 2",
                "Mumbai",
                "Maharashtra",
                "400001",
                "India"
        );

        when(communityRepository.existsByNameIgnoreCase(request.name())).thenReturn(false);
        when(communityRepository.existsByCommunityCode(any())).thenReturn(false);
        when(communityRepository.save(any(Community.class))).thenAnswer(inv -> {
            Community c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CommunityResponse response = communityService.createCommunity(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("New Field Agency");
        assertThat(response.type()).isEqualTo(CommunityType.FIELD_AGENCY);
        assertThat(response.status()).isEqualTo(CommunityStatus.ACTIVE);
        verify(communityRepository).save(any(Community.class));
    }

    @Test
    @DisplayName("createCommunity with duplicate name should throw DuplicateResourceException")
    void createCommunity_DuplicateName_ThrowsException() {
        CreateCommunityRequest request = new CreateCommunityRequest(
                "EduPartners Agency",
                null,
                CommunityType.PARTNER,
                null, null, null, null, null, null, null, null
        );

        when(communityRepository.existsByNameIgnoreCase(request.name())).thenReturn(true);

        assertThatThrownBy(() -> communityService.createCommunity(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("EduPartners Agency");
    }

    @Test
    @DisplayName("getCommunityById should return community when found")
    void getCommunityById_Success() {
        when(communityRepository.findById(communityId)).thenReturn(Optional.of(sampleCommunity));
        when(communityMemberRepository.countByCommunityId(communityId)).thenReturn(2L);

        CommunityResponse response = communityService.getCommunityById(communityId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(communityId);
        assertThat(response.communityCode()).isEqualTo("COM-12345678");
        assertThat(response.memberCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getCommunityById should throw ResourceNotFoundException when not found")
    void getCommunityById_NotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(communityRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> communityService.getCommunityById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getCommunityByCode should return community when found")
    void getCommunityByCode_Success() {
        when(communityRepository.findByCommunityCode("COM-12345678")).thenReturn(Optional.of(sampleCommunity));
        when(communityMemberRepository.countByCommunityId(communityId)).thenReturn(1L);

        CommunityResponse response = communityService.getCommunityByCode("COM-12345678");

        assertThat(response).isNotNull();
        assertThat(response.communityCode()).isEqualTo("COM-12345678");
    }

    @Test
    @DisplayName("getAllCommunities should return paginated list")
    void getAllCommunities_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Community> page = new PageImpl<>(List.of(sampleCommunity), pageable, 1);

        when(communityRepository.findWithFilters(eq(CommunityStatus.ACTIVE), eq(CommunityType.FIELD_AGENCY), eq("Edu"), eq(pageable)))
                .thenReturn(page);
        when(communityMemberRepository.countByCommunityId(communityId)).thenReturn(1L);

        Page<CommunityResponse> result = communityService.getAllCommunities(pageable, CommunityStatus.ACTIVE, CommunityType.FIELD_AGENCY, "Edu");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("EduPartners Agency");
    }

    @Test
    @DisplayName("updateCommunity should update allowed fields")
    void updateCommunity_Success() {
        UpdateCommunityRequest request = new UpdateCommunityRequest(
                "Updated Agency Name",
                "New description",
                CommunityType.NGO,
                "new@email.com",
                "+919876543219",
                "New Address",
                null,
                "Pune",
                "Maharashtra",
                "411001",
                "India"
        );

        when(communityRepository.findById(communityId)).thenReturn(Optional.of(sampleCommunity));
        when(communityRepository.existsByNameIgnoreCaseAndIdNot("Updated Agency Name", communityId)).thenReturn(false);
        when(communityRepository.save(any(Community.class))).thenReturn(sampleCommunity);
        when(communityMemberRepository.countByCommunityId(communityId)).thenReturn(0L);

        CommunityResponse response = communityService.updateCommunity(communityId, request);

        assertThat(response).isNotNull();
        assertThat(sampleCommunity.getName()).isEqualTo("Updated Agency Name");
        assertThat(sampleCommunity.getType()).isEqualTo(CommunityType.NGO);
    }

    @Test
    @DisplayName("changeCommunityStatus should change status and save")
    void changeCommunityStatus_Success() {
        when(communityRepository.findById(communityId)).thenReturn(Optional.of(sampleCommunity));
        when(communityRepository.save(any(Community.class))).thenReturn(sampleCommunity);
        when(communityMemberRepository.countByCommunityId(communityId)).thenReturn(0L);

        CommunityResponse response = communityService.changeCommunityStatus(communityId, CommunityStatus.SUSPENDED);

        assertThat(response).isNotNull();
        assertThat(sampleCommunity.getStatus()).isEqualTo(CommunityStatus.SUSPENDED);
    }

    @Test
    @DisplayName("addMember should add new member to active community")
    void addMember_Success() {
        UUID platformUserId = UUID.randomUUID();
        AddCommunityMemberRequest request = new AddCommunityMemberRequest(platformUserId, CommunityMemberRole.FIELD_AGENT);

        when(communityRepository.findById(communityId)).thenReturn(Optional.of(sampleCommunity));
        when(communityMemberRepository.existsByCommunityIdAndPlatformUserId(communityId, platformUserId)).thenReturn(false);
        when(communityMemberRepository.findByMemberCode(any())).thenReturn(Optional.empty());
        when(communityMemberRepository.save(any(CommunityMember.class))).thenAnswer(inv -> {
            CommunityMember m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        CommunityMemberResponse response = communityService.addMember(communityId, request);

        assertThat(response).isNotNull();
        assertThat(response.platformUserId()).isEqualTo(platformUserId);
        assertThat(response.role()).isEqualTo(CommunityMemberRole.FIELD_AGENT);
        assertThat(response.status()).isEqualTo(CommunityMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("addMember to inactive community should throw InvalidStateTransitionException")
    void addMember_InactiveCommunity_ThrowsException() {
        sampleCommunity.setStatus(CommunityStatus.INACTIVE);
        when(communityRepository.findById(communityId)).thenReturn(Optional.of(sampleCommunity));

        AddCommunityMemberRequest request = new AddCommunityMemberRequest(UUID.randomUUID(), CommunityMemberRole.FIELD_AGENT);

        assertThatThrownBy(() -> communityService.addMember(communityId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("addMember with already existing user should throw DuplicateResourceException")
    void addMember_DuplicateUser_ThrowsException() {
        UUID platformUserId = UUID.randomUUID();
        AddCommunityMemberRequest request = new AddCommunityMemberRequest(platformUserId, CommunityMemberRole.FIELD_AGENT);

        when(communityRepository.findById(communityId)).thenReturn(Optional.of(sampleCommunity));
        when(communityMemberRepository.existsByCommunityIdAndPlatformUserId(communityId, platformUserId)).thenReturn(true);

        assertThatThrownBy(() -> communityService.addMember(communityId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("updateMemberRole should update member role")
    void updateMemberRole_Success() {
        UUID memberId = UUID.randomUUID();
        CommunityMember member = new CommunityMember(sampleCommunity, UUID.randomUUID(), "MEM-12345678", CommunityMemberRole.FIELD_AGENT);
        member.setId(memberId);

        when(communityRepository.findById(communityId)).thenReturn(Optional.of(sampleCommunity));
        when(communityMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(communityMemberRepository.save(any(CommunityMember.class))).thenReturn(member);

        CommunityMemberResponse response = communityService.updateMemberRole(
                communityId, memberId, new UpdateCommunityMemberRoleRequest(CommunityMemberRole.COMMUNITY_ADMIN));

        assertThat(response).isNotNull();
        assertThat(member.getRole()).isEqualTo(CommunityMemberRole.COMMUNITY_ADMIN);
    }

    @Test
    @DisplayName("removeMember should delete member entity")
    void removeMember_Success() {
        UUID memberId = UUID.randomUUID();
        CommunityMember member = new CommunityMember(sampleCommunity, UUID.randomUUID(), "MEM-12345678", CommunityMemberRole.FIELD_AGENT);
        member.setId(memberId);

        when(communityRepository.findById(communityId)).thenReturn(Optional.of(sampleCommunity));
        when(communityMemberRepository.findById(memberId)).thenReturn(Optional.of(member));

        communityService.removeMember(communityId, memberId);

        verify(communityMemberRepository).delete(member);
    }
}
