package com.vidya.master.hub;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.community.repository.CommunityRepository;
import com.vidya.master.hub.dto.AssignHubManagerRequest;
import com.vidya.master.hub.dto.CreateHubRequest;
import com.vidya.master.hub.dto.HubResponse;
import com.vidya.master.hub.dto.UpdateHubRequest;
import com.vidya.master.hub.entity.Hub;
import com.vidya.master.hub.entity.HubStatus;
import com.vidya.master.hub.repository.HubRepository;
import com.vidya.master.hub.service.HubServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HubService — Regional Hub Hierarchy Tests")
class HubServiceTest {

    @Mock
    private HubRepository hubRepository;

    @Mock
    private CommunityRepository communityRepository;

    @InjectMocks
    private HubServiceImpl hubService;

    private UUID hubId;
    private UUID leadCommunityId;
    private UUID managerId;
    private UUID subManagerId;
    private Hub hub;
    private Community community;

    @BeforeEach
    void setUp() {
        hubId = UUID.randomUUID();
        leadCommunityId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        subManagerId = UUID.randomUUID();

        community = new Community(
                "COM-DEL001",
                "Delhi Central NGO",
                "Lead field agency in Delhi",
                CommunityType.NGO,
                CommunityStatus.ACTIVE
        );

        hub = new Hub(
                "HUB-DEL-01",
                "Delhi State Central Hub",
                "Delhi",
                "North India",
                "Delhi operations hub",
                community,
                managerId,
                "Delhi Hub Lead Manager",
                subManagerId,
                "Delhi Hub Sub-Manager",
                "delhi.hub@vidya.io",
                "+91-11-23456789"
        );
    }

    @Test
    @DisplayName("createHub: successfully creates regional hub with lead community and managers")
    void createHub_Success() {
        CreateHubRequest request = new CreateHubRequest(
                "Delhi State Central Hub",
                "Delhi",
                "North India",
                "Delhi operations hub",
                leadCommunityId,
                managerId,
                "Delhi Hub Lead Manager",
                subManagerId,
                "Delhi Hub Sub-Manager",
                "delhi.hub@vidya.io",
                "+91-11-23456789"
        );

        when(communityRepository.findById(leadCommunityId)).thenReturn(Optional.of(community));
        when(hubRepository.save(any(Hub.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hubRepository.countCommunitiesInHub(any())).thenReturn(1L);
        when(hubRepository.countSchoolsInHub(any())).thenReturn(5L);

        HubResponse response = hubService.createHub(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Delhi State Central Hub");
        assertThat(response.state()).isEqualTo("Delhi");
        assertThat(response.hubManagerName()).isEqualTo("Delhi Hub Lead Manager");
        assertThat(response.hubSubManagerName()).isEqualTo("Delhi Hub Sub-Manager");
        assertThat(community.isHubLead()).isTrue();
    }

    @Test
    @DisplayName("assignHubManagers: assigns primary Hub Manager and deputy Sub-Manager")
    void assignHubManagers_Success() {
        AssignHubManagerRequest request = new AssignHubManagerRequest(
                managerId,
                "Updated Lead Manager",
                subManagerId,
                "Updated Sub Manager"
        );

        when(hubRepository.findById(hubId)).thenReturn(Optional.of(hub));
        when(hubRepository.save(any(Hub.class))).thenReturn(hub);
        when(hubRepository.countCommunitiesInHub(any())).thenReturn(2L);
        when(hubRepository.countSchoolsInHub(any())).thenReturn(10L);

        HubResponse response = hubService.assignHubManagers(hubId, request);

        assertThat(response).isNotNull();
        assertThat(hub.getHubManagerName()).isEqualTo("Updated Lead Manager");
        assertThat(hub.getHubSubManagerName()).isEqualTo("Updated Sub Manager");
        verify(hubRepository).save(hub);
    }

    @Test
    @DisplayName("assignCommunityToHub: links community to regional hub")
    void assignCommunityToHub_Success() {
        UUID newCommunityId = UUID.randomUUID();
        Community newCommunity = new Community("COM-DEL002", "South Delhi Field Partner", "Partner", CommunityType.PARTNER, CommunityStatus.ACTIVE);

        when(hubRepository.findById(hubId)).thenReturn(Optional.of(hub));
        when(communityRepository.findById(newCommunityId)).thenReturn(Optional.of(newCommunity));

        hubService.assignCommunityToHub(hubId, newCommunityId);

        assertThat(newCommunity.getHub()).isEqualTo(hub);
        verify(communityRepository).save(newCommunity);
    }

    @Test
    @DisplayName("getHubById: throws ResourceNotFoundException when hub does not exist")
    void getHubById_NotFound() {
        when(hubRepository.findById(hubId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hubService.getHubById(hubId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
