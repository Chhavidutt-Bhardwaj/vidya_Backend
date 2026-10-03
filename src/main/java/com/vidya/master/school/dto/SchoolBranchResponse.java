package com.vidya.master.school.dto;

import com.vidya.master.school.entity.BranchStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a school branch.
 */
public record SchoolBranchResponse(
        UUID id,
        UUID schoolId,
        String schoolCode,
        String branchCode,
        String branchName,
        boolean isMainBranch,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String country,
        String contactEmail,
        String contactPhone,
        BranchStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
