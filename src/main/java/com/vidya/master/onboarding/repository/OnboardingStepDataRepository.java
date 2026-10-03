package com.vidya.master.onboarding.repository;

import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.entity.OnboardingStepData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link OnboardingStepData} entity.
 */
@Repository
public interface OnboardingStepDataRepository extends JpaRepository<OnboardingStepData, UUID> {

    Optional<OnboardingStepData> findByOnboardingIdAndStepName(UUID onboardingId, OnboardingStep stepName);

    List<OnboardingStepData> findByOnboardingId(UUID onboardingId);
}
