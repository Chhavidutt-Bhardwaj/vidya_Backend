package com.vidya.master.school.dto;

import com.vidya.master.school.entity.RmAssignmentType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignSchoolRmRequest(
        @NotNull(message = "Community ID is required")
        UUID communityId,

        UUID onboardingCommunityId,
        UUID rmMemberId,
        String rmMemberName,
        RmAssignmentType assignmentType
) {}
