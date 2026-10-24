package com.vidya.master.community.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload for updating the ADDRESS_DETAILS section.
 */
public record UpdateCommunityAddressDetailRequest(
        @Size(max = 255) String billingAddressLine1,
        @Size(max = 255) String billingAddressLine2,
        @Size(max = 100) String billingCity,
        @Size(max = 100) String billingState,
        @Size(max = 20)  String billingPincode,
        @Size(max = 100) String billingCountry,

        boolean sameAsBilling,

        @Size(max = 255) String operationalAddressLine1,
        @Size(max = 255) String operationalAddressLine2,
        @Size(max = 100) String operationalCity,
        @Size(max = 100) String operationalState,
        @Size(max = 20)  String operationalPincode,
        @Size(max = 100) String operationalCountry
) {}
