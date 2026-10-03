package com.vidya.master.community.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.community.dto.AddCommunityMemberRequest;
import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityResponse;
import com.vidya.master.community.dto.CreateCommunityRequest;
import com.vidya.master.community.dto.UpdateCommunityMemberRoleRequest;
import com.vidya.master.community.dto.UpdateCommunityRequest;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.community.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for managing Communities and Community Memberships.
 */
@RestController
@RequestMapping("/api/v1/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityResponse>> createCommunity(
            @Valid @RequestBody CreateCommunityRequest request) {
        CommunityResponse response = communityService.createCommunity(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.COMMUNITY_CREATED,
                        "Community created successfully",
                        response
                ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CommunityResponse>> getCommunityById(@PathVariable UUID id) {
        CommunityResponse response = communityService.getCommunityById(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.COMMUNITY_FOUND,
                "Community retrieved successfully",
                response
        ));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<CommunityResponse>> getCommunityByCode(@PathVariable String code) {
        CommunityResponse response = communityService.getCommunityByCode(code);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.COMMUNITY_FOUND,
                "Community retrieved successfully",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<PagedApiResponse<CommunityResponse>> getAllCommunities(
            @RequestParam(required = false) CommunityStatus status,
            @RequestParam(required = false) CommunityType type,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {

        Page<CommunityResponse> page = communityService.getAllCommunities(pageable, status, type, search);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.COMMUNITIES_FOUND,
                "Communities retrieved successfully",
                page.getContent(),
                page
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommunityResponse>> updateCommunity(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCommunityRequest request) {
        CommunityResponse response = communityService.updateCommunity(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.COMMUNITY_UPDATED,
                "Community updated successfully",
                response
        ));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CommunityResponse>> changeCommunityStatus(
            @PathVariable UUID id,
            @RequestParam CommunityStatus status) {
        CommunityResponse response = communityService.changeCommunityStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.COMMUNITY_STATUS_UPDATED,
                "Community status updated successfully",
                response
        ));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddCommunityMemberRequest request) {
        CommunityMemberResponse response = communityService.addMember(id, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.COMMUNITY_MEMBER_ADDED,
                        "Community member added successfully",
                        response
                ));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<PagedApiResponse<CommunityMemberResponse>> getCommunityMembers(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<CommunityMemberResponse> page = communityService.getCommunityMembers(id, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.COMMUNITY_MEMBERS_FOUND,
                "Community members retrieved successfully",
                page.getContent(),
                page
        ));
    }

    @PatchMapping("/{id}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> updateMemberRole(
            @PathVariable UUID id,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateCommunityMemberRoleRequest request) {
        CommunityMemberResponse response = communityService.updateMemberRole(id, memberId, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.COMMUNITY_STATUS_UPDATED,
                "Member role updated successfully",
                response
        ));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID memberId) {
        communityService.removeMember(id, memberId);
        return ResponseEntity.noContent().build();
    }
}
