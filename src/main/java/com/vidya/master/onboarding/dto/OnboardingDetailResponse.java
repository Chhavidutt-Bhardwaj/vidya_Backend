package com.vidya.master.onboarding.dto;

import java.util.List;

/**
 * Detailed response containing onboarding status, step data payloads, and audit history.
 */
public record OnboardingDetailResponse(
        OnboardingResponse summary,
        List<OnboardingStepDataResponse> steps,
        List<OnboardingActivityResponse> activities
) {}
