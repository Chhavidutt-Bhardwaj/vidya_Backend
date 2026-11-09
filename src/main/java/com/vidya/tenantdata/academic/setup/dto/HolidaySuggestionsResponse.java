package com.vidya.tenantdata.academic.setup.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param region         ISO 3166-2 state code used for state holidays (e.g. "IN-MH"); null when unknown
 * @param provider       external provider used, null when none is configured
 * @param providerStatus OK | STALE (provider down, older data) | DISABLED | UNAVAILABLE
 * @param message        why provider suggestions are missing or partial, for the UI
 * @param attribution    text to show next to provider data (required by some providers)
 */
public record HolidaySuggestionsResponse(String region, String provider, String providerStatus, Instant providerFetchedAt,
                                         String message, String attribution, List<HolidaySuggestion> suggestions) {
}
