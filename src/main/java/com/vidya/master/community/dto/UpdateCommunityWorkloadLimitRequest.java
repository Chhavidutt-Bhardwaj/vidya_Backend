package com.vidya.master.community.dto;

import jakarta.validation.constraints.Min;

public record UpdateCommunityWorkloadLimitRequest(
        @Min(value = 1, message = "Max active query limit must be at least 1")
        Integer maxActiveQueryLimit,

        @Min(value = 1, message = "Max assigned schools limit must be at least 1")
        Integer maxAssignedSchoolsLimit
) {}
