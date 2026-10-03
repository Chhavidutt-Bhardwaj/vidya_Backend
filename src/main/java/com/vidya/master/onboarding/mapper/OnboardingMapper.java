package com.vidya.master.onboarding.mapper;

import com.vidya.master.onboarding.dto.OnboardingActivityResponse;
import com.vidya.master.onboarding.dto.OnboardingDetailResponse;
import com.vidya.master.onboarding.dto.OnboardingResponse;
import com.vidya.master.onboarding.dto.OnboardingStepDataResponse;
import com.vidya.master.onboarding.entity.OnboardingActivity;
import com.vidya.master.onboarding.entity.OnboardingStepData;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Maps between Onboarding entities and DTO records.
 */
@Component
public class OnboardingMapper {

    public OnboardingResponse toResponse(SchoolOnboarding entity) {
        if (entity == null) {
            return null;
        }
        return new OnboardingResponse(
                entity.getId(),
                entity.getOnboardingCode(),
                entity.getSchool() != null ? entity.getSchool().getId() : null,
                entity.getSchool() != null ? entity.getSchool().getSchoolCode() : null,
                entity.getSchool() != null ? entity.getSchool().getSchoolName() : null,
                entity.getSource(),
                entity.getCommunityId(),
                entity.getCommunityMemberId(),
                entity.getCurrentStep(),
                entity.getCompletedSteps() != null ? entity.getCompletedSteps() : Collections.emptyList(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getStartedAt(),
                entity.getSubmittedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public OnboardingStepDataResponse toStepDataResponse(OnboardingStepData stepData) {
        if (stepData == null) {
            return null;
        }
        return new OnboardingStepDataResponse(
                stepData.getId(),
                stepData.getStepName(),
                stepData.getStepData(),
                stepData.isCompleted(),
                stepData.getCompletedAt(),
                stepData.getUpdatedAt()
        );
    }

    public OnboardingActivityResponse toActivityResponse(OnboardingActivity activity) {
        if (activity == null) {
            return null;
        }
        return new OnboardingActivityResponse(
                activity.getId(),
                activity.getAction(),
                activity.getPreviousStatus(),
                activity.getNewStatus(),
                activity.getPerformedBy(),
                activity.getPerformedByType(),
                activity.getMetadata(),
                activity.getCreatedAt()
        );
    }

    public OnboardingDetailResponse toDetailResponse(SchoolOnboarding entity) {
        if (entity == null) {
            return null;
        }
        OnboardingResponse summary = toResponse(entity);
        List<OnboardingStepDataResponse> steps = entity.getStepData() != null
                ? entity.getStepData().stream().map(this::toStepDataResponse).toList()
                : Collections.emptyList();
        List<OnboardingActivityResponse> activities = entity.getActivities() != null
                ? entity.getActivities().stream().map(this::toActivityResponse).toList()
                : Collections.emptyList();

        return new OnboardingDetailResponse(summary, steps, activities);
    }
}
