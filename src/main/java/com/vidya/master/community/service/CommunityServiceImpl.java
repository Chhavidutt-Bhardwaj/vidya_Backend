package com.vidya.master.community.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.community.dto.AddCommunityMemberRequest;
import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityResponse;
import com.vidya.master.community.dto.CreateCommunityRequest;
import com.vidya.master.community.dto.UpdateCommunityMemberRoleRequest;
import com.vidya.master.community.dto.UpdateCommunityRequest;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.community.mapper.CommunityMapper;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.community.repository.CommunityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of {@link CommunityService} for managing field agent communities
 * and member assignments in the Master Database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "masterTransactionManager")
public class CommunityServiceImpl implements CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityMapper communityMapper;

    @Override
    public CommunityResponse createCommunity(CreateCommunityRequest request) {
        log.info("Creating community with name: [{}]", request.name());

        if (communityRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("Community", "name", request.name());
        }

        String communityCode = generateUniqueCommunityCode();

        Community community = new Community(
                communityCode,
                request.name().trim(),
                request.description(),
                request.type(),
                CommunityStatus.ACTIVE,
                request.contactEmail(),
                request.contactPhone(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.state(),
                request.pincode(),
                request.country()
        );

        Community saved = communityRepository.save(community);
        log.info("Community created successfully with ID: [{}] and code: [{}]", saved.getId(), saved.getCommunityCode());
        return communityMapper.toResponse(saved, 0L);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public CommunityResponse getCommunityById(UUID id) {
        Community community = findCommunityOrThrow(id);
        long memberCount = communityMemberRepository.countByCommunityId(id);
        return communityMapper.toResponse(community, memberCount);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public CommunityResponse getCommunityByCode(String code) {
        Community community = communityRepository.findByCommunityCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Community", code));
        long memberCount = communityMemberRepository.countByCommunityId(community.getId());
        return communityMapper.toResponse(community, memberCount);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Page<CommunityResponse> getAllCommunities(Pageable pageable, CommunityStatus status, CommunityType type, String search) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<Community> page = communityRepository.findWithFilters(status, type, cleanSearch, pageable);
        return page.map(c -> {
            long count = communityMemberRepository.countByCommunityId(c.getId());
            return communityMapper.toResponse(c, count);
        });
    }

    @Override
    public CommunityResponse updateCommunity(UUID id, UpdateCommunityRequest request) {
        log.info("Updating community with ID: [{}]", id);
        Community community = findCommunityOrThrow(id);

        if (communityRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw new DuplicateResourceException("Community", "name", request.name());
        }

        community.setName(request.name().trim());
        community.setDescription(request.description());
        community.setType(request.type());
        community.setContactEmail(request.contactEmail());
        community.setContactPhone(request.contactPhone());
        community.setAddressLine1(request.addressLine1());
        community.setAddressLine2(request.addressLine2());
        community.setCity(request.city());
        community.setState(request.state());
        community.setPincode(request.pincode());
        if (request.country() != null && !request.country().isBlank()) {
            community.setCountry(request.country());
        }

        Community updated = communityRepository.save(community);
        long memberCount = communityMemberRepository.countByCommunityId(id);
        return communityMapper.toResponse(updated, memberCount);
    }

    @Override
    public CommunityResponse changeCommunityStatus(UUID id, CommunityStatus newStatus) {
        log.info("Changing community [{}] status to [{}]", id, newStatus);
        Community community = findCommunityOrThrow(id);
        community.setStatus(newStatus);
        Community updated = communityRepository.save(community);
        long memberCount = communityMemberRepository.countByCommunityId(id);
        return communityMapper.toResponse(updated, memberCount);
    }

    @Override
    public CommunityMemberResponse addMember(UUID communityId, AddCommunityMemberRequest request) {
        log.info("Adding member [{}] to community [{}] with role [{}]",
                request.platformUserId(), communityId, request.role());

        Community community = findCommunityOrThrow(communityId);
        if (community.getStatus() != CommunityStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Community", community.getStatus().name(), "ADD_MEMBER");
        }

        if (communityMemberRepository.existsByCommunityIdAndPlatformUserId(communityId, request.platformUserId())) {
            throw new DuplicateResourceException("CommunityMember", "platformUserId", request.platformUserId());
        }

        String memberCode = generateUniqueMemberCode();
        CommunityMember member = new CommunityMember(
                community,
                request.platformUserId(),
                memberCode,
                request.role()
        );

        CommunityMember saved = communityMemberRepository.save(member);
        log.info("Member added with ID: [{}] and code: [{}]", saved.getId(), saved.getMemberCode());
        return communityMapper.toMemberResponse(saved);
    }

    @Override
    public CommunityMemberResponse updateMemberRole(UUID communityId, UUID memberId, UpdateCommunityMemberRoleRequest request) {
        log.info("Updating member [{}] in community [{}] to role [{}]", memberId, communityId, request.role());
        findCommunityOrThrow(communityId);

        CommunityMember member = communityMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityMember", memberId));

        if (!member.getCommunity().getId().equals(communityId)) {
            throw new ResourceNotFoundException("CommunityMember", memberId);
        }

        member.setRole(request.role());
        CommunityMember updated = communityMemberRepository.save(member);
        return communityMapper.toMemberResponse(updated);
    }

    @Override
    public void removeMember(UUID communityId, UUID memberId) {
        log.info("Removing member [{}] from community [{}]", memberId, communityId);
        findCommunityOrThrow(communityId);

        CommunityMember member = communityMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityMember", memberId));

        if (!member.getCommunity().getId().equals(communityId)) {
            throw new ResourceNotFoundException("CommunityMember", memberId);
        }

        communityMemberRepository.delete(member);
        log.info("Member [{}] removed successfully", memberId);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Page<CommunityMemberResponse> getCommunityMembers(UUID communityId, Pageable pageable) {
        findCommunityOrThrow(communityId);
        Page<CommunityMember> members = communityMemberRepository.findByCommunityId(communityId, pageable);
        return members.map(communityMapper::toMemberResponse);
    }

    private Community findCommunityOrThrow(UUID id) {
        return communityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Community", id));
    }

    private String generateUniqueCommunityCode() {
        String code;
        int attempts = 0;
        do {
            code = CodeGenerator.communityCode();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate unique community code after 10 attempts");
            }
        } while (communityRepository.existsByCommunityCode(code));
        return code;
    }

    private String generateUniqueMemberCode() {
        String code;
        int attempts = 0;
        do {
            code = CodeGenerator.memberCode();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate unique member code after 10 attempts");
            }
        } while (communityMemberRepository.findByMemberCode(code).isPresent());
        return code;
    }
}
