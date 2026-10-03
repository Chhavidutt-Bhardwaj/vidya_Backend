package com.vidya.master.onboarding.orchestration.dto;

import com.vidya.master.onboarding.entity.OnboardingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingActivationResponse {
    private UUID onboardingId;
    private String onboardingCode;
    private UUID schoolId;
    private String schoolCode;
    private UUID tenantId;
    private String tenantCode;
    private String databaseName;
    private UUID subscriptionId;
    private String subscriptionCode;
    private String transactionCode;
    private OnboardingStatus status;
    private String message;
    private Instant activatedAt;
}
