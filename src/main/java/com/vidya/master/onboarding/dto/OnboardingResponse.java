package com.vidya.master.onboarding.dto;

import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Standard summary response payload for school onboarding.
 */
public record OnboardingResponse(
        UUID id,
        String onboardingCode,
        UUID schoolId,
        String schoolCode,
        String schoolName,
        OnboardingSource source,
        UUID communityId,
        UUID communityMemberId,
        OnboardingStep currentStep,
        List<String> completedSteps,
        OnboardingStatus status,
        String rejectionReason,
        Instant startedAt,
        Instant submittedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {}
