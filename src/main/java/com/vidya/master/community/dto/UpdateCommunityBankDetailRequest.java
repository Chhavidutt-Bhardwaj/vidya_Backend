package com.vidya.master.community.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload for updating the BANK_DETAILS section.
 */
public record UpdateCommunityBankDetailRequest(
        @Size(max = 200) String accountHolderName,
        @Size(max = 100) String bankName,
        @Size(max = 100) String branchName,
        @Size(max = 30)  String accountNumber,
        @Size(max = 15)  String ifscCode,
        @Size(max = 15)  String micrCode,
        @Size(max = 100) String upiId
) {}
