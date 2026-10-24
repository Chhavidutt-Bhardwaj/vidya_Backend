package com.vidya.master.hub.service;

import com.vidya.master.hub.dto.AssignHubManagerRequest;
import com.vidya.master.hub.dto.CreateHubRequest;
import com.vidya.master.hub.dto.HubResponse;
import com.vidya.master.hub.dto.UpdateHubRequest;
import com.vidya.master.hub.entity.HubStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface HubService {

    HubResponse createHub(CreateHubRequest request);

    HubResponse updateHub(UUID id, UpdateHubRequest request);

    HubResponse getHubById(UUID id);

    HubResponse getHubByCode(String hubCode);

    Page<HubResponse> listHubs(String state, HubStatus status, Pageable pageable);

    HubResponse assignHubManagers(UUID hubId, AssignHubManagerRequest request);

    HubResponse assignLeadCommunity(UUID hubId, UUID communityId);

    void assignCommunityToHub(UUID hubId, UUID communityId);

    void removeCommunityFromHub(UUID hubId, UUID communityId);
}
