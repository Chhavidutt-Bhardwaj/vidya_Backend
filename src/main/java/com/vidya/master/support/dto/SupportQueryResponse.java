package com.vidya.master.support.dto;

import com.vidya.master.support.entity.EscalationLevel;
import com.vidya.master.support.entity.QueryCategory;
import com.vidya.master.support.entity.QueryPriority;
import com.vidya.master.support.entity.QueryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupportQueryResponse(
        UUID id,
        String ticketNumber,
        UUID schoolId,
        String schoolName,
        String schoolCode,
        UUID assignedCommunityId,
        String assignedCommunityName,
        UUID hubId,
        String hubName,
        String hubState,
        QueryCategory category,
        QueryPriority priority,
        QueryStatus status,
        EscalationLevel currentLevel,
        String subject,
        String description,
        UUID raisedByUserId,
        String raisedByName,
        String raisedByRole,
        UUID assignedRmMemberId,
        UUID escalatedToHubManagerId,
        UUID escalatedToPlatformUserId,
        String resolutionSummary,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        List<QueryEscalationLogResponse> escalationHistory,
        List<QueryCommentResponse> comments
) {}
