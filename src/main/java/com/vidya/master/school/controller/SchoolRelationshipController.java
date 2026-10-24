package com.vidya.master.school.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.school.dto.AssignSchoolRmRequest;
import com.vidya.master.school.dto.SchoolRelationshipResponse;
import com.vidya.master.school.service.SchoolRelationshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for School Relationship Management (RM) & Community/Hub Allocation.
 * Handles the mapping: School &rarr; Relationship Manager Community &rarr; Regional Hub.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SchoolRelationshipController {

    private final SchoolRelationshipService relationshipService;

    @PostMapping("/schools/{schoolId}/relationship/assign-rm")
    public ResponseEntity<ApiResponse<SchoolRelationshipResponse>> assignRelationshipManager(
            @PathVariable UUID schoolId,
            @Valid @RequestBody AssignSchoolRmRequest request) {
        SchoolRelationshipResponse response = relationshipService.assignRelationshipManager(schoolId, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SCHOOL_RM_ASSIGNED,
                "School successfully assigned to Relationship Manager Community",
                response
        ));
    }

    @PostMapping("/schools/{schoolId}/relationship/auto-assign-nearest")
    public ResponseEntity<ApiResponse<SchoolRelationshipResponse>> autoAssignNearest(
            @PathVariable UUID schoolId) {
        SchoolRelationshipResponse response = relationshipService.autoAssignNearestCommunity(schoolId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SCHOOL_RM_AUTO_ASSIGNED,
                "School auto-assigned to nearest Community Relationship Manager based on state/hub proximity",
                response
        ));
    }

    @GetMapping("/schools/{schoolId}/relationship")
    public ResponseEntity<ApiResponse<SchoolRelationshipResponse>> getSchoolRelationship(
            @PathVariable UUID schoolId) {
        SchoolRelationshipResponse response = relationshipService.getSchoolRelationshipDetails(schoolId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SCHOOL_RM_FOUND,
                "School Relationship details retrieved successfully",
                response
        ));
    }

    @GetMapping("/communities/{communityId}/assigned-schools")
    public ResponseEntity<PagedApiResponse<SchoolRelationshipResponse>> listAssignedSchoolsForCommunity(
            @PathVariable UUID communityId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SchoolRelationshipResponse> page = relationshipService.listSchoolsByCommunity(communityId, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.SCHOOL_RM_LIST_FOUND,
                "Assigned schools for community retrieved successfully",
                page
        ));
    }

    @GetMapping("/hubs/{hubId}/assigned-schools")
    public ResponseEntity<PagedApiResponse<SchoolRelationshipResponse>> listAssignedSchoolsForHub(
            @PathVariable UUID hubId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SchoolRelationshipResponse> page = relationshipService.listSchoolsByHub(hubId, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.SCHOOL_RM_LIST_FOUND,
                "Assigned schools for hub retrieved successfully",
                page
        ));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/communities/{communityId}/workload-limits")
    public ResponseEntity<ApiResponse<Void>> updateWorkloadLimits(
            @PathVariable UUID communityId,
            @Valid @RequestBody com.vidya.master.community.dto.UpdateCommunityWorkloadLimitRequest request) {
        relationshipService.updateCommunityWorkloadLimits(communityId, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.COMMUNITY_UPDATED,
                "Community workload capacity limits updated successfully by Hub Manager",
                null
        ));
    }
}
