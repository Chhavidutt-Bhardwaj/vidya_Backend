package com.vidya.master.holiday;

import java.util.List;

/** Source of public holidays and festivals for a country and calendar year. */
public interface HolidayProvider {

    /** Short id stored with cached data, e.g. "calendarific". */
    String id();

    /** Human-readable name for the UI / attribution. */
    String displayName();

    /**
     * All holidays of the country in that calendar year (nationwide and regional).
     *
     * @throws HolidayProviderException when the provider cannot be reached or answers with an error
     */
    List<ProviderHoliday> fetch(String countryCode, int year);

    class HolidayProviderException extends RuntimeException {
        public HolidayProviderException(String message) {
            super(message);
        }

        public HolidayProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
