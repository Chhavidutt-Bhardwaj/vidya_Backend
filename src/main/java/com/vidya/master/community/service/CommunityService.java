package com.vidya.master.community.service;

import com.vidya.master.community.dto.AddCommunityMemberRequest;
import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityResponse;
import com.vidya.master.community.dto.CreateCommunityRequest;
import com.vidya.master.community.dto.UpdateCommunityMemberRoleRequest;
import com.vidya.master.community.dto.UpdateCommunityRequest;
import com.vidya.master.community.entity.CommunityMemberRole;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for Community management operations.
 */
public interface CommunityService {

    CommunityResponse createCommunity(CreateCommunityRequest request);

    CommunityResponse getCommunityById(UUID id);

    CommunityResponse getCommunityByCode(String code);

    Page<CommunityResponse> getAllCommunities(Pageable pageable, CommunityStatus status, CommunityType type, String search);

    CommunityResponse updateCommunity(UUID id, UpdateCommunityRequest request);

    CommunityResponse changeCommunityStatus(UUID id, CommunityStatus newStatus);

    CommunityMemberResponse addMember(UUID communityId, AddCommunityMemberRequest request);

    CommunityMemberResponse updateMemberRole(UUID communityId, UUID memberId, UpdateCommunityMemberRoleRequest request);

    void removeMember(UUID communityId, UUID memberId);

    Page<CommunityMemberResponse> getCommunityMembers(UUID communityId, Pageable pageable);
}
