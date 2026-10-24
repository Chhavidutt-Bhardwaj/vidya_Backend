package com.vidya.master.community.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for the ADDRESS_DETAILS section of a community.
 */
public record CommunityAddressDetailResponse(
        UUID id,
        UUID communityId,

        // Billing address
        String billingAddressLine1,
        String billingAddressLine2,
        String billingCity,
        String billingState,
        String billingPincode,
        String billingCountry,

        // Operational address
        String operationalAddressLine1,
        String operationalAddressLine2,
        String operationalCity,
        String operationalState,
        String operationalPincode,
        String operationalCountry,

        boolean sameAsBilling,
        Instant updatedAt
) {}
