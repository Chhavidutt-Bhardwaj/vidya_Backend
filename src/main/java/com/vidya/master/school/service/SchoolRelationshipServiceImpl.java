package com.vidya.master.school.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.community.dto.UpdateCommunityWorkloadLimitRequest;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityAddress;
import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.repository.CommunityAddressRepository;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.community.repository.CommunityRepository;
import com.vidya.master.hub.entity.Hub;
import com.vidya.master.hub.entity.HubStatus;
import com.vidya.master.hub.repository.HubRepository;
import com.vidya.master.school.dto.AssignSchoolRmRequest;
import com.vidya.master.school.dto.SchoolRelationshipResponse;
import com.vidya.master.school.entity.RmAssignmentType;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBranch;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.support.entity.QueryStatus;
import com.vidya.master.support.repository.SchoolSupportQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolRelationshipServiceImpl implements SchoolRelationshipService {

    private final SchoolRepository schoolRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityAddressRepository communityAddressRepository;
    private final HubRepository hubRepository;
    private final SchoolSupportQueryRepository queryRepository;

    private static final List<QueryStatus> ACTIVE_STATUSES = List.of(
            QueryStatus.OPEN, QueryStatus.IN_PROGRESS, QueryStatus.ESCALATED_TO_HUB
    );

    @Override
    @Transactional("masterTransactionManager")
    public SchoolRelationshipResponse assignRelationshipManager(UUID schoolId, AssignSchoolRmRequest request) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        Community rmCommunity = communityRepository.findById(request.communityId())
                .orElseThrow(() -> new ResourceNotFoundException("Community", request.communityId()));

        // Workload capacity check
        long activeQueries = queryRepository.countByAssignedCommunityIdAndStatusIn(rmCommunity.getId(), ACTIVE_STATUSES);
        if (activeQueries >= rmCommunity.getMaxActiveQueryLimit()) {
            log.warn("Community [{}] has reached or exceeded max active query capacity [{}/{}]. Assigning with capacity alert.",
                    rmCommunity.getName(), activeQueries, rmCommunity.getMaxActiveQueryLimit());
        }

        // Onboarding Community Attribution (Community A)
        if (request.onboardingCommunityId() != null) {
            Community onboardingComm = communityRepository.findById(request.onboardingCommunityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Onboarding Community", request.onboardingCommunityId()));
            school.setOnboardingCommunity(onboardingComm);
        } else if (school.getOnboardingCommunity() == null) {
            // Default onboarding attribution to the assigned community if not already set
            school.setOnboardingCommunity(rmCommunity);
        }

        // Relationship Manager Community (Community B)
        school.setAssignedCommunity(rmCommunity);
        school.setRmAssignmentType(request.assignmentType() != null
                ? request.assignmentType()
                : RmAssignmentType.MANUAL_PLATFORM_OVERRIDE);
        school.setRmAssignedAt(Instant.now());

        if (rmCommunity.getHub() != null) {
            school.setHub(rmCommunity.getHub());
        }

        // Dedicated RM member
        if (request.rmMemberId() != null) {
            CommunityMember member = communityMemberRepository.findById(request.rmMemberId())
                    .orElseThrow(() -> new ResourceNotFoundException("CommunityMember", request.rmMemberId()));
            school.setAssignedRmMemberId(member.getId());
            school.setRmMemberName(request.rmMemberName() != null ? request.rmMemberName() : member.getMemberCode());
        } else if (request.rmMemberName() != null) {
            school.setRmMemberName(request.rmMemberName());
        }

        school = schoolRepository.save(school);
        log.info("Assigned School [{}] - Onboarded by: [{}], Serviced by RM Community: [{}] under Hub: [{}]",
                school.getSchoolName(),
                school.getOnboardingCommunity() != null ? school.getOnboardingCommunity().getName() : "DIRECT",
                rmCommunity.getName(),
                school.getHub() != null ? school.getHub().getName() : "NONE");

        return toResponse(school);
    }

    @Override
    @Transactional("masterTransactionManager")
    public SchoolRelationshipResponse autoAssignNearestCommunity(UUID schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        log.info("Running capacity-aware auto-assignment for school [{}] to nearest Relationship Manager Community",
                school.getSchoolName());

        String state = null;
        for (SchoolBranch branch : school.getBranches()) {
            if (branch.isMainBranch() || state == null) {
                state = branch.getState();
            }
        }

        Community targetCommunity = null;

        // 1. Try to match active Hub by state and pick community with available query capacity
        if (state != null && !state.isBlank()) {
            List<Hub> stateHubs = hubRepository.findByStateIgnoreCaseAndStatus(state, HubStatus.ACTIVE);
            if (!stateHubs.isEmpty()) {
                Hub hub = stateHubs.get(0);
                List<Community> hubCommunities = communityRepository.findByHubIdAndStatus(hub.getId(), CommunityStatus.ACTIVE);

                // Filter communities with capacity (activeQueries < maxActiveQueryLimit)
                targetCommunity = hubCommunities.stream()
                        .filter(c -> queryRepository.countByAssignedCommunityIdAndStatusIn(c.getId(), ACTIVE_STATUSES) < c.getMaxActiveQueryLimit())
                        .min(Comparator.comparingLong(c -> schoolRepository.countByAssignedCommunityId(c.getId())))
                        .orElse(null);

                // If all are at capacity, fall back to the one with the lowest total school load
                if (targetCommunity == null && !hubCommunities.isEmpty()) {
                    targetCommunity = hubCommunities.stream()
                            .min(Comparator.comparingLong(c -> schoolRepository.countByAssignedCommunityId(c.getId())))
                            .orElse(hubCommunities.get(0));
                }
            }

            // 2. Search community addresses in the state
            if (targetCommunity == null) {
                List<CommunityAddress> addresses = communityAddressRepository.findByStateIgnoreCase(state);
                if (!addresses.isEmpty()) {
                    targetCommunity = addresses.stream()
                            .map(CommunityAddress::getCommunity)
                            .filter(c -> c.getStatus() == CommunityStatus.ACTIVE)
                            .filter(c -> queryRepository.countByAssignedCommunityIdAndStatusIn(c.getId(), ACTIVE_STATUSES) < c.getMaxActiveQueryLimit())
                            .min(Comparator.comparingLong(c -> schoolRepository.countByAssignedCommunityId(c.getId())))
                            .orElse(null);
                }
            }
        }

        // 3. Fallback: global active community with capacity
        if (targetCommunity == null) {
            List<Community> allActive = communityRepository.findByStatus(CommunityStatus.ACTIVE);
            if (!allActive.isEmpty()) {
                targetCommunity = allActive.stream()
                        .filter(c -> queryRepository.countByAssignedCommunityIdAndStatusIn(c.getId(), ACTIVE_STATUSES) < c.getMaxActiveQueryLimit())
                        .min(Comparator.comparingLong(c -> schoolRepository.countByAssignedCommunityId(c.getId())))
                        .orElse(allActive.get(0));
            }
        }

        if (targetCommunity == null) {
            log.warn("No active community available for auto-assignment to school [{}]", school.getSchoolCode());
            return toResponse(school);
        }

        school.setAssignedCommunity(targetCommunity);
        school.setRmAssignmentType(RmAssignmentType.NEAREST_GEO_ALLOCATION);
        school.setRmAssignedAt(Instant.now());

        if (school.getOnboardingCommunity() == null) {
            school.setOnboardingCommunity(targetCommunity);
        }

        if (targetCommunity.getHub() != null) {
            school.setHub(targetCommunity.getHub());
        }

        school = schoolRepository.save(school);
        log.info("Auto-assigned School [{}] to nearest Community [{}] with capacity [active queries: {}/{}]",
                school.getSchoolName(), targetCommunity.getName(),
                queryRepository.countByAssignedCommunityIdAndStatusIn(targetCommunity.getId(), ACTIVE_STATUSES),
                targetCommunity.getMaxActiveQueryLimit());

        return toResponse(school);
    }

    @Override
    @Transactional("masterTransactionManager")
    public void updateCommunityWorkloadLimits(UUID communityId, UpdateCommunityWorkloadLimitRequest request) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        if (request.maxActiveQueryLimit() != null) {
            community.setMaxActiveQueryLimit(request.maxActiveQueryLimit());
        }
        if (request.maxAssignedSchoolsLimit() != null) {
            community.setMaxAssignedSchoolsLimit(request.maxAssignedSchoolsLimit());
        }

        communityRepository.save(community);
        log.info("Updated workload capacity limits for Community [{}]: maxActiveQueries={}, maxSchools={}",
                community.getName(), community.getMaxActiveQueryLimit(), community.getMaxAssignedSchoolsLimit());
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public SchoolRelationshipResponse getSchoolRelationshipDetails(UUID schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
        return toResponse(school);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public Page<SchoolRelationshipResponse> listSchoolsByCommunity(UUID communityId, Pageable pageable) {
        return schoolRepository.findByAssignedCommunityId(communityId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true, value = "masterTransactionManager")
    public Page<SchoolRelationshipResponse> listSchoolsByHub(UUID hubId, Pageable pageable) {
        return schoolRepository.findByHubId(hubId, pageable).map(this::toResponse);
    }

    private SchoolRelationshipResponse toResponse(School school) {
        String city = null;
        String state = null;
        for (SchoolBranch b : school.getBranches()) {
            if (b.isMainBranch() || city == null) {
                city = b.getCity();
                state = b.getState();
            }
        }

        Community onboardingCommunity = school.getOnboardingCommunity();
        Community rmCommunity = school.getAssignedCommunity();
        Hub hub = school.getHub();

        int activeQueries = 0;
        int maxQueryLimit = 10;
        if (rmCommunity != null) {
            activeQueries = (int) queryRepository.countByAssignedCommunityIdAndStatusIn(rmCommunity.getId(), ACTIVE_STATUSES);
            maxQueryLimit = rmCommunity.getMaxActiveQueryLimit();
        }

        return new SchoolRelationshipResponse(
                school.getId(),
                school.getSchoolCode(),
                school.getSchoolName(),
                school.getEmail(),
                school.getPhone(),
                city,
                state,
                onboardingCommunity != null ? onboardingCommunity.getId() : null,
                onboardingCommunity != null ? onboardingCommunity.getCommunityCode() : null,
                onboardingCommunity != null ? onboardingCommunity.getName() : null,
                school.getOnboardedAt(),
                rmCommunity != null ? rmCommunity.getId() : null,
                rmCommunity != null ? rmCommunity.getCommunityCode() : null,
                rmCommunity != null ? rmCommunity.getName() : null,
                rmCommunity != null ? rmCommunity.getType().name() : null,
                activeQueries,
                maxQueryLimit,
                school.getAssignedRmMemberId(),
                school.getRmMemberName(),
                hub != null ? hub.getId() : null,
                hub != null ? hub.getHubCode() : null,
                hub != null ? hub.getName() : null,
                hub != null ? hub.getState() : null,
                hub != null ? hub.getHubManagerUserId() : null,
                hub != null ? hub.getHubManagerName() : null,
                hub != null ? hub.getHubSubManagerUserId() : null,
                hub != null ? hub.getHubSubManagerName() : null,
                school.getRmAssignmentType(),
                school.getRmAssignedAt()
        );
    }
}
