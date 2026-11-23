package com.vidya.master.school.dto;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in school and its campuses (branches), for the dashboard header and campus switcher.
 * Student and staff numbers are not split by branch in the school database; take the totals from {@code GET /dashboard/kpis}.
 */
public record SchoolCampusesResponse(
        UUID schoolId,
        String schoolCode,
        String schoolName,
        String board,
        String status,
        List<SchoolBranchResponse> campuses
) {}
