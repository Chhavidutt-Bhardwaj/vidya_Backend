package com.vidya.master.support.dto;

import java.time.Instant;
import java.util.UUID;

public record QueryCommentResponse(
        UUID id,
        UUID authorUserId,
        String authorName,
        String authorRole,
        String message,
        boolean internalNote,
        Instant createdAt
) {}
