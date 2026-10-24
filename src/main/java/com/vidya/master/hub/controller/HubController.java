package com.vidya.master.hub.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.hub.dto.AssignHubManagerRequest;
import com.vidya.master.hub.dto.CreateHubRequest;
import com.vidya.master.hub.dto.HubResponse;
import com.vidya.master.hub.dto.UpdateHubRequest;
import com.vidya.master.hub.entity.HubStatus;
import com.vidya.master.hub.service.HubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for Managing Regional / State Operational Hubs.
 * Controls the chain: Platform Users &rarr; Hub Manager &rarr; Community &rarr; School.
 */
@RestController
@RequestMapping("/api/v1/hubs")
@RequiredArgsConstructor
public class HubController {

    private final HubService hubService;

    @PostMapping
    public ResponseEntity<ApiResponse<HubResponse>> createHub(
            @Valid @RequestBody CreateHubRequest request) {
        HubResponse response = hubService.createHub(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.HUB_CREATED,
                        "Regional hub created successfully",
                        response
                ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HubResponse>> getHubById(@PathVariable UUID id) {
        HubResponse response = hubService.getHubById(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.HUB_FOUND,
                "Hub retrieved successfully",
                response
        ));
    }

    @GetMapping("/code/{hubCode}")
    public ResponseEntity<ApiResponse<HubResponse>> getHubByCode(@PathVariable String hubCode) {
        HubResponse response = hubService.getHubByCode(hubCode);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.HUB_FOUND,
                "Hub retrieved successfully",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<PagedApiResponse<HubResponse>> listHubs(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) HubStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<HubResponse> page = hubService.listHubs(state, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.HUBS_FOUND,
                "Hubs retrieved successfully",
                page
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HubResponse>> updateHub(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHubRequest request) {
        HubResponse response = hubService.updateHub(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.HUB_UPDATED,
                "Hub updated successfully",
                response
        ));
    }

    @PostMapping("/{id}/managers")
    public ResponseEntity<ApiResponse<HubResponse>> assignHubManagers(
            @PathVariable UUID id,
            @Valid @RequestBody AssignHubManagerRequest request) {
        HubResponse response = hubService.assignHubManagers(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.HUB_MANAGER_ASSIGNED,
                "Hub Manager and Sub-Manager assigned successfully",
                response
        ));
    }

    @PostMapping("/{id}/lead-community/{communityId}")
    public ResponseEntity<ApiResponse<HubResponse>> assignLeadCommunity(
            @PathVariable UUID id,
            @PathVariable UUID communityId) {
        HubResponse response = hubService.assignLeadCommunity(id, communityId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.HUB_COMMUNITY_ASSIGNED,
                "Lead Community assigned to Hub successfully",
                response
        ));
    }

    @PostMapping("/{id}/communities/{communityId}")
    public ResponseEntity<ApiResponse<Void>> assignCommunityToHub(
            @PathVariable UUID id,
            @PathVariable UUID communityId) {
        hubService.assignCommunityToHub(id, communityId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.HUB_COMMUNITY_ASSIGNED,
                "Community successfully attached to Hub",
                null
        ));
    }

    @DeleteMapping("/{id}/communities/{communityId}")
    public ResponseEntity<ApiResponse<Void>> removeCommunityFromHub(
            @PathVariable UUID id,
            @PathVariable UUID communityId) {
        hubService.removeCommunityFromHub(id, communityId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.HUB_COMMUNITY_REMOVED,
                "Community successfully detached from Hub",
                null
        ));
    }
}
