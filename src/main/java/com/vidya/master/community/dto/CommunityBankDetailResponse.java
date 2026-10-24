package com.vidya.master.community.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for the BANK_DETAILS section.
 * Account number is masked to last 4 digits for non-privileged callers.
 */
public record CommunityBankDetailResponse(
        UUID id,
        UUID communityId,
        String accountHolderName,
        String bankName,
        String branchName,
        /** Masked: "••••••1234" for VIEW, full value for APPROVE. */
        String accountNumber,
        String ifscCode,
        String micrCode,
        String upiId,
        boolean isVerified,
        Instant updatedAt
) {}
