package com.vidya.master.onboarding.repository;

import com.vidya.master.onboarding.entity.OnboardingActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link OnboardingActivity} entity.
 */
@Repository
public interface OnboardingActivityRepository extends JpaRepository<OnboardingActivity, UUID> {

    List<OnboardingActivity> findByOnboardingIdOrderByCreatedAtDesc(UUID onboardingId);
}
