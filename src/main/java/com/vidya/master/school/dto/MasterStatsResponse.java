package com.vidya.master.school.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Platform Master Data Statistics DTO for platform administrators.
 */
@Schema(description = "Platform-wide master data and tenant infrastructure statistics")
public record MasterStatsResponse(
        @Schema(description = "Total registered schools", example = "42")
        long totalSchools,

        @Schema(description = "Active operational schools", example = "38")
        long activeSchools,

        @Schema(description = "Schools in draft onboarding state", example = "3")
        long draftSchools,

        @Schema(description = "Suspended schools", example = "1")
        long suspendedSchools,

        @Schema(description = "Total campuses and branches across all schools", example = "56")
        long totalBranches,

        @Schema(description = "Total provisioned tenant databases", example = "38")
        long totalTenants,

        @Schema(description = "Active tenant databases", example = "38")
        long activeTenants,

        @Schema(description = "Total onboarding pipeline applications", example = "45")
        long totalOnboardings
) {}
