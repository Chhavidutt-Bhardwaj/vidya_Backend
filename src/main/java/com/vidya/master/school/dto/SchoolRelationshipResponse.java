package com.vidya.master.school.dto;

import com.vidya.master.school.entity.RmAssignmentType;

import java.time.Instant;
import java.util.UUID;

public record SchoolRelationshipResponse(
        UUID schoolId,
        String schoolCode,
        String schoolName,
        String schoolEmail,
        String schoolPhone,
        String schoolCity,
        String schoolState,
        // Onboarding Community (Community A - Brought the school, receives onboarding commission)
        UUID onboardingCommunityId,
        String onboardingCommunityCode,
        String onboardingCommunityName,
        Instant onboardedAt,
        // Assigned Relationship Manager Community (Community B - Services queries, receives query commission)
        UUID assignedCommunityId,
        String assignedCommunityCode,
        String assignedCommunityName,
        String assignedCommunityType,
        int rmCommunityActiveQueries,
        int rmCommunityMaxQueryLimit,
        // Community RM Member (Account Executive)
        UUID assignedRmMemberId,
        String rmMemberName,
        // Regional Hub
        UUID hubId,
        String hubCode,
        String hubName,
        String hubState,
        UUID hubManagerUserId,
        String hubManagerName,
        UUID hubSubManagerUserId,
        String hubSubManagerName,
        // Assignment Meta
        RmAssignmentType rmAssignmentType,
        Instant rmAssignedAt
) {}
