package com.vidya.master.support.dto;

import com.vidya.master.support.entity.EscalationLevel;

import java.time.Instant;
import java.util.UUID;

public record QueryEscalationLogResponse(
        UUID id,
        EscalationLevel fromLevel,
        EscalationLevel toLevel,
        UUID escalatedByUserId,
        String escalatedByName,
        String escalatedByRole,
        UUID escalatedToUserId,
        String reason,
        String notes,
        Instant occurredAt
) {}
