package com.vidya.master.support.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.support.dto.AddQueryCommentRequest;
import com.vidya.master.support.dto.CreateSupportQueryRequest;
import com.vidya.master.support.dto.EscalateQueryRequest;
import com.vidya.master.support.dto.QueryCommentResponse;
import com.vidya.master.support.dto.ResolveQueryRequest;
import com.vidya.master.support.dto.SupportQueryResponse;
import com.vidya.master.support.entity.EscalationLevel;
import com.vidya.master.support.entity.QueryCategory;
import com.vidya.master.support.entity.QueryPriority;
import com.vidya.master.support.entity.QueryStatus;
import com.vidya.master.support.service.SchoolSupportQueryService;
import com.vidya.security.user.PlatformUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for School Support & Problem Queries.
 * Implements the 3-Tier Escalation chain:
 * Level 1: Community RM &rarr; Level 2: Hub Manager / Sub-Manager &rarr; Level 3: Platform Admin.
 */
@RestController
@RequestMapping("/api/v1/support/queries")
@RequiredArgsConstructor
public class SchoolSupportQueryController {

    private final SchoolSupportQueryService queryService;

    @PostMapping
    public ResponseEntity<ApiResponse<SupportQueryResponse>> createQuery(
            @Valid @RequestBody CreateSupportQueryRequest request,
            @AuthenticationPrincipal PlatformUserDetails userDetails) {
        UUID userId = userDetails != null ? userDetails.getUserId() : null;
        String name = userDetails != null ? userDetails.getUsername() : "School Representative";
        String role = userDetails != null && userDetails.getPlatformRole() != null
                ? userDetails.getPlatformRole().name() : "SCHOOL_USER";

        SupportQueryResponse response = queryService.createQuery(request, userId, name, role);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.QUERY_CREATED,
                        "Support ticket created successfully and routed to your Community Relationship Manager",
                        response
                ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupportQueryResponse>> getQueryById(@PathVariable UUID id) {
        SupportQueryResponse response = queryService.getQueryById(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.QUERY_FOUND,
                "Support query retrieved successfully",
                response
        ));
    }

    @GetMapping("/ticket/{ticketNumber}")
    public ResponseEntity<ApiResponse<SupportQueryResponse>> getQueryByTicketNumber(@PathVariable String ticketNumber) {
        SupportQueryResponse response = queryService.getQueryByTicketNumber(ticketNumber);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.QUERY_FOUND,
                "Support query retrieved successfully",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<PagedApiResponse<SupportQueryResponse>> listQueries(
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID communityId,
            @RequestParam(required = false) UUID hubId,
            @RequestParam(required = false) QueryStatus status,
            @RequestParam(required = false) QueryCategory category,
            @RequestParam(required = false) QueryPriority priority,
            @RequestParam(required = false) EscalationLevel currentLevel,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SupportQueryResponse> page = queryService.listQueries(
                schoolId, communityId, hubId, status, category, priority, currentLevel, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.QUERIES_FOUND,
                "Support queries retrieved successfully",
                page
        ));
    }

    @PostMapping("/{id}/escalate")
    public ResponseEntity<ApiResponse<SupportQueryResponse>> escalateQuery(
            @PathVariable UUID id,
            @Valid @RequestBody EscalateQueryRequest request,
            @AuthenticationPrincipal PlatformUserDetails userDetails) {
        UUID actorUserId = userDetails != null ? userDetails.getUserId() : null;
        String actorName = userDetails != null ? userDetails.getUsername() : "Authorized Agent";
        String actorRole = userDetails != null && userDetails.getPlatformRole() != null
                ? userDetails.getPlatformRole().name() : "COMMUNITY_MANAGER";

        SupportQueryResponse response = queryService.escalateQuery(id, request, actorUserId, actorName, actorRole);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.QUERY_ESCALATED,
                "Support ticket successfully escalated to " + request.toLevel(),
                response
        ));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<SupportQueryResponse>> resolveQuery(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveQueryRequest request,
            @AuthenticationPrincipal PlatformUserDetails userDetails) {
        UUID actorUserId = userDetails != null ? userDetails.getUserId() : null;
        String actorName = userDetails != null ? userDetails.getUsername() : "Support Agent";
        String actorRole = userDetails != null && userDetails.getPlatformRole() != null
                ? userDetails.getPlatformRole().name() : "SUPPORT_SPECIALIST";

        SupportQueryResponse response = queryService.resolveQuery(id, request, actorUserId, actorName, actorRole);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.QUERY_RESOLVED,
                "Support query marked as resolved",
                response
        ));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<QueryCommentResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody AddQueryCommentRequest request,
            @AuthenticationPrincipal PlatformUserDetails userDetails) {
        UUID authorId = userDetails != null ? userDetails.getUserId() : null;
        String authorName = userDetails != null ? userDetails.getUsername() : "Participant";
        String authorRole = userDetails != null && userDetails.getPlatformRole() != null
                ? userDetails.getPlatformRole().name() : "AGENT";

        QueryCommentResponse response = queryService.addComment(id, request, authorId, authorName, authorRole);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.QUERY_COMMENT_ADDED,
                "Comment posted successfully",
                response
        ));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<List<QueryCommentResponse>>> getComments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean includeInternal) {
        List<QueryCommentResponse> comments = queryService.getComments(id, includeInternal);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.QUERY_FOUND,
                "Comments retrieved successfully",
                comments
        ));
    }
}
