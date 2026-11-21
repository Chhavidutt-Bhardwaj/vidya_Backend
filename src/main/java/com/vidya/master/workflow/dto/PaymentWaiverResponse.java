package com.vidya.master.workflow.dto;

import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of an onboarding payment waiver")
public class PaymentWaiverResponse {
    private UUID onboardingId;
    private String onboardingCode;
    private OnboardingStatus onboardingStatus;
    private OnboardingStep nextStep;
    private UUID waivedBy;
    private String reason;
    private Instant waivedAt;
}
