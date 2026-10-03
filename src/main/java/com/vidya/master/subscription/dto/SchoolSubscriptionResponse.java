package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload representing a school subscription record.
 */
public record SchoolSubscriptionResponse(
        UUID id,
        String subscriptionCode,
        UUID schoolId,
        UUID planId,
        String planCode,
        Integer planVersion,
        LocalDate startDate,
        LocalDate endDate,
        BillingCycle billingCycle,
        BigDecimal baseAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal finalAmount,
        String currency,
        SubscriptionStatus status,
        boolean autoRenew,
        LocalDate trialEndDate,
        Instant createdAt,
        Instant updatedAt
) {}
