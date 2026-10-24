package com.vidya.master.hub.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.repository.CommunityRepository;
import com.vidya.master.hub.dto.AssignHubManagerRequest;
import com.vidya.master.hub.dto.CreateHubRequest;
import com.vidya.master.hub.dto.HubResponse;
import com.vidya.master.hub.dto.UpdateHubRequest;
import com.vidya.master.hub.entity.Hub;
import com.vidya.master.hub.entity.HubStatus;
import com.vidya.master.hub.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HubServiceImpl implements HubService {

    private final HubRepository hubRepository;
    private final CommunityRepository communityRepository;

    @Override
    @Transactional("masterTransactionManager")
    public HubResponse createHub(CreateHubRequest request) {
        log.info("Creating regional hub: [{}] for state: [{}]", request.name(), request.state());

        Community leadCommunity = null;
        if (request.leadCommunityId() != null) {
            leadCommunity = communityRepository.findById(request.leadCommunityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lead Community", request.leadCommunityId()));
        }

        String hubCode = CodeGenerator.hubCode();
        Hub hub = new Hub(
                hubCode,
                request.name(),
                request.state(),
                request.region(),
                request.description(),
                leadCommunity,
                request.hubManagerUserId(),
                request.hubManagerName(),
                request.hubSubManagerUserId(),
                request.hubSubManagerName(),
                request.contactEmail(),
                request.contactPhone()
        );

        hub = hubRepository.save(hub);

        if (leadCommunity != null) {
            leadCommunity.setHub(hub);
            leadCommunity.setHubLead(true);
            communityRepository.save(leadCommunity);
        }

        log.info("Created hub [{}] with ID: [{}]", hub.getHubCode(), hub.getId());
        return toResponse(hub);
    }

    @Override
    @Transactional("masterTransactionManager")
    public HubResponse updateHub(UUID id, UpdateHubRequest request) {
        Hub hub = hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hub", id));

        if (request.name() != null && !request.name().isBlank()) {
            hub.setName(request.name());
        }
        if (request.state() != null && !request.state().isBlank()) {
            hub.setState(request.state());
        }
        if (request.region() != null) {
            hub.setRegion(request.region());
        }
        if (request.description() != null) {
            hub.setDescription(request.description());
        }
        if (request.status() != null) {
            hub.setStatus(request.status());
        }
        if (request.contactEmail() != null) {
            hub.setContactEmail(request.contactEmail());
        }
        if (request.contactPhone() != null) {
            hub.setContactPhone(request.contactPhone());
        }
        if (request.hubManagerUserId() != null) {
            hub.setHubManagerUserId(request.hubManagerUserId());
            hub.setHubManagerName(request.hubManagerName());
        }
        if (request.hubSubManagerUserId() != null) {
            hub.setHubSubManagerUserId(request.hubSubManagerUserId());
            hub.setHubSubManagerName(request.hubSubManagerName());
        }
        if (request.leadCommunityId() != null) {
            Community lead = communityRepository.findById(request.leadCommunityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Community", request.leadCommunityId()));
            hub.setLeadCommunity(lead);
            lead.setHub(hub);
            lead.setHubLead(true);
            communityRepository.save(lead);
        }

        hub = hubRepository.save(hub);
        return toResponse(hub);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public HubResponse getHubById(UUID id) {
        Hub hub = hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hub", id));
        return toResponse(hub);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public HubResponse getHubByCode(String hubCode) {
        Hub hub = hubRepository.findByHubCode(hubCode)
                .orElseThrow(() -> new ResourceNotFoundException("Hub", hubCode));
        return toResponse(hub);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public Page<HubResponse> listHubs(String state, HubStatus status, Pageable pageable) {
        return hubRepository.findFiltered(state, status, pageable).map(this::toResponse);
    }

    @Override
    @Transactional("masterTransactionManager")
    public HubResponse assignHubManagers(UUID hubId, AssignHubManagerRequest request) {
        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new ResourceNotFoundException("Hub", hubId));

        hub.setHubManagerUserId(request.hubManagerUserId());
        hub.setHubManagerName(request.hubManagerName());
        hub.setHubSubManagerUserId(request.hubSubManagerUserId());
        hub.setHubSubManagerName(request.hubSubManagerName());

        hub = hubRepository.save(hub);
        log.info("Assigned Hub Manager [{}] and Sub-Manager [{}] to Hub [{}]",
                request.hubManagerName(), request.hubSubManagerName(), hub.getHubCode());
        return toResponse(hub);
    }

    @Override
    @Transactional("masterTransactionManager")
    public HubResponse assignLeadCommunity(UUID hubId, UUID communityId) {
        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new ResourceNotFoundException("Hub", hubId));
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        // Reset previous lead if any
        if (hub.getLeadCommunity() != null) {
            hub.getLeadCommunity().setHubLead(false);
            communityRepository.save(hub.getLeadCommunity());
        }

        hub.setLeadCommunity(community);
        community.setHub(hub);
        community.setHubLead(true);
        communityRepository.save(community);

        hub = hubRepository.save(hub);
        log.info("Designated Community [{}] as Lead for Hub [{}]", community.getName(), hub.getHubCode());
        return toResponse(hub);
    }

    @Override
    @Transactional("masterTransactionManager")
    public void assignCommunityToHub(UUID hubId, UUID communityId) {
        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new ResourceNotFoundException("Hub", hubId));
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        community.setHub(hub);
        communityRepository.save(community);
        log.info("Assigned Community [{}] to Hub [{}]", community.getName(), hub.getName());
    }

    @Override
    @Transactional("masterTransactionManager")
    public void removeCommunityFromHub(UUID hubId, UUID communityId) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        if (community.getHub() != null && community.getHub().getId().equals(hubId)) {
            community.setHub(null);
            community.setHubLead(false);
            communityRepository.save(community);
            log.info("Removed Community [{}] from Hub [{}]", community.getName(), hubId);
        }
    }

    private HubResponse toResponse(Hub hub) {
        long communityCount = hubRepository.countCommunitiesInHub(hub.getId());
        long schoolCount = hubRepository.countSchoolsInHub(hub.getId());

        UUID leadId = hub.getLeadCommunity() != null ? hub.getLeadCommunity().getId() : null;
        String leadName = hub.getLeadCommunity() != null ? hub.getLeadCommunity().getName() : null;

        return new HubResponse(
                hub.getId(),
                hub.getHubCode(),
                hub.getName(),
                hub.getState(),
                hub.getRegion(),
                hub.getDescription(),
                hub.getStatus(),
                leadId,
                leadName,
                hub.getHubManagerUserId(),
                hub.getHubManagerName(),
                hub.getHubSubManagerUserId(),
                hub.getHubSubManagerName(),
                hub.getContactEmail(),
                hub.getContactPhone(),
                communityCount,
                schoolCount,
                hub.getCreatedAt(),
                hub.getUpdatedAt()
        );
    }
}
