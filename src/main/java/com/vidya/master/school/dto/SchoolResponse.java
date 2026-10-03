package com.vidya.master.school.dto;

import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a school entity.
 */
public record SchoolResponse(
        UUID id,
        String schoolCode,
        UUID tenantId,
        String schoolName,
        String legalName,
        String registrationNumber,
        SchoolBoard board,
        SchoolType schoolType,
        String email,
        String phone,
        String website,
        SchoolStatus status,
        Instant createdAt,
        Instant updatedAt,
        long branchCount,
        SchoolBranchResponse mainBranch
) {}
