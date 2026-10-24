package com.vidya.master.support.dto;

import com.vidya.master.support.entity.EscalationLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EscalateQueryRequest(
        @NotNull(message = "Target escalation level is required")
        EscalationLevel toLevel,

        @NotBlank(message = "Reason for escalation is required")
        String reason,

        String notes,

        UUID assignedTargetUserId
) {}
