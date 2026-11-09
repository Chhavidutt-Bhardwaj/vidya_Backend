package com.vidya.master.holiday;

import java.time.LocalDate;
import java.util.Set;

/**
 * A holiday as published by an external calendar provider.
 *
 * @param regions ISO 3166-2 subdivision suffixes and/or state names it applies to (e.g. "MH",
 *                "Maharashtra"); empty = the whole country
 */
public record ProviderHoliday(String name, LocalDate startDate, LocalDate endDate, Kind kind, String description, Set<String> regions) {

    public enum Kind {
        /** National / gazetted / state public holiday: schools normally close. */
        PUBLIC,
        /** Restricted / optional holiday: school decides. */
        OPTIONAL,
        /** Observance, season, clock change: not a day off. */
        OBSERVANCE
    }

    public boolean nationwide() {
        return regions == null || regions.isEmpty();
    }
}
