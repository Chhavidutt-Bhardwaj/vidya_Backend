package com.vidya.master.onboarding.service;

import com.vidya.master.onboarding.dto.InitiateOnboardingRequest;
import com.vidya.master.onboarding.dto.OnboardingDetailResponse;
import com.vidya.master.onboarding.dto.OnboardingResponse;
import com.vidya.master.onboarding.dto.RejectOnboardingRequest;
import com.vidya.master.onboarding.dto.SaveStepDataRequest;
import com.vidya.master.onboarding.dto.VerifyOnboardingRequest;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service for the multi-step school onboarding wizard state machine.
 */
public interface OnboardingService {

    OnboardingResponse initiateOnboarding(InitiateOnboardingRequest request);

    OnboardingResponse getOnboardingById(UUID id);

    OnboardingResponse getOnboardingByCode(String code);

    OnboardingDetailResponse getOnboardingDetail(UUID id);

    OnboardingResponse saveStepData(UUID id, SaveStepDataRequest request);

    OnboardingResponse submitOnboarding(UUID id);

    OnboardingResponse verifyOnboarding(UUID id, VerifyOnboardingRequest request);

    OnboardingResponse approveOnboarding(UUID id, String notes);

    OnboardingResponse rejectOnboarding(UUID id, RejectOnboardingRequest request);

    Page<OnboardingResponse> getAllOnboardings(Pageable pageable, OnboardingStatus status, OnboardingSource source, UUID communityId);
}
