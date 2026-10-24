package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommissionLedgerStatus;
import com.vidya.master.community.entity.CommissionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionLedgerResponse(
        UUID id,
        UUID communityId,
        String communityName,
        CommissionType commissionType,
        UUID schoolId,
        String schoolName,
        UUID supportQueryId,
        String ticketNumber,
        BigDecimal amount,
        String currency,
        CommissionLedgerStatus status,
        String notes,
        Instant earnedAt,
        Instant paidAt
) {}
