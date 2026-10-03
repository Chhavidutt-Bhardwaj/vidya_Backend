package com.vidya.master.onboarding.orchestration;

import com.vidya.master.onboarding.orchestration.dto.CompleteOnboardingRequest;
import com.vidya.master.onboarding.orchestration.dto.OnboardingActivationResponse;

import java.util.UUID;

public interface OnboardingOrchestrationService {

    /**
     * Executes the complete end-to-end pipeline:
     * 1. Validates onboarding state
     * 2. Sets up school subscription snapshot
     * 3. Handles payment transaction (settled / manual confirmation)
     * 4. Provisions isolated PostgreSQL tenant database & runs Flyway migrations
     * 5. Activates school, subscription, and onboarding records
     */
    OnboardingActivationResponse completeOnboardingAndActivate(UUID onboardingId, CompleteOnboardingRequest request);
}
