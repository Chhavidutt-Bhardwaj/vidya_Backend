package com.vidya.tenantdata.academic.setup.dto;

import com.vidya.tenantdata.calendar.enums.HolidayType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * @param valid   no row has an error (when false nothing was saved)
 * @param created rows saved (0 for a dry run or when invalid)
 */
public record HolidayPlanResponse(boolean dryRun, boolean valid, int created, List<Line> holidays, int totalHolidayDays) {

    /** @param error why the row cannot be saved; @param note a non-blocking remark (e.g. falls on a weekend) */
    public record Line(int index, String name, HolidayType holidayType, LocalDate startDate, LocalDate endDate,
                       DayOfWeek startsOn, int days, String error, String note) {
    }
}
