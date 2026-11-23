package com.vidya.tenantdata.academic.setup.dto;

import com.vidya.tenantdata.calendar.enums.HolidayType;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * A holiday the admin may want this year.
 *
 * @param source       NATIONAL (fixed-date national holiday), PROVIDER (external holiday calendar,
 *                     e.g. Calendarific) or PREVIOUS_YEAR (copied, moved by whole years)
 * @param optional     restricted / optional holiday: show unticked
 * @param reviewDate   true when the date should be checked (copied festivals; provider dates are
 *                     published but may still differ from the state's notified list)
 * @param alreadyAdded a holiday with the same name already exists this year
 */
public record HolidaySuggestion(String source, String name, HolidayType holidayType, LocalDate startDate, LocalDate endDate,
                                DayOfWeek startsOn, boolean optional, boolean reviewDate, boolean alreadyAdded,
                                String description) {
}
