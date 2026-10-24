package com.vidya.master.school.service;

import com.vidya.master.school.dto.AssignSchoolRmRequest;
import com.vidya.master.school.dto.SchoolRelationshipResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SchoolRelationshipService {

    SchoolRelationshipResponse assignRelationshipManager(UUID schoolId, AssignSchoolRmRequest request);

    SchoolRelationshipResponse autoAssignNearestCommunity(UUID schoolId);

    SchoolRelationshipResponse getSchoolRelationshipDetails(UUID schoolId);

    Page<SchoolRelationshipResponse> listSchoolsByCommunity(UUID communityId, Pageable pageable);

    Page<SchoolRelationshipResponse> listSchoolsByHub(UUID hubId, Pageable pageable);

    void updateCommunityWorkloadLimits(UUID communityId, com.vidya.master.community.dto.UpdateCommunityWorkloadLimitRequest request);
}
