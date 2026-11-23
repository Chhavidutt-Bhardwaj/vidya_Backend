package com.vidya.tenantdata.academic.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanRequest;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanRequest.Row;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanResponse;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanResponse.Line;
import com.vidya.tenantdata.academic.setup.dto.HolidaySuggestion;
import com.vidya.tenantdata.academic.setup.dto.HolidaySuggestionsResponse;
import com.vidya.master.holiday.HolidayCatalogService;
import com.vidya.master.holiday.HolidayCatalogService.Catalog;
import com.vidya.master.holiday.IndianRegions;
import com.vidya.master.holiday.ProviderHoliday;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
import com.vidya.tenantdata.calendar.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Holiday planning for a whole academic year: suggestions (national fixed-date holidays and last
 * year's holidays moved to this year) and validated bulk creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YearHolidayService {

    /** National holidays with a fixed calendar date (India). Festival holidays vary by year and state. */
    static final List<Object[]> NATIONAL_FIXED = List.of(
            new Object[]{"Republic Day", MonthDay.of(1, 26)},
            new Object[]{"Independence Day", MonthDay.of(8, 15)},
            new Object[]{"Gandhi Jayanti", MonthDay.of(10, 2)});

    private final AcademicYearRepository academicYearRepository;
    private final SchoolHolidayRepository holidayRepository;
    private final CalendarService calendarService;
    private final HolidayCatalogService catalogService;

    // -----------------------------------------------------------------------------------------

    /**
     * Suggestions, in priority order (a later source never repeats a name an earlier one gave):
     * national fixed dates, the external provider for the school's state, then last year's list.
     *
     * @param region optional state override ("Maharashtra", "MH", "IN-MH"); default: the school's address
     */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public HolidaySuggestionsResponse suggestions(UUID academicYearId, UUID fromAcademicYearId, String region) {
        AcademicYear year = year(academicYearId);
        Set<String> existingNames = new HashSet<>();
        for (SchoolHoliday h : existing(year)) {
            existingNames.add(norm(h.getName()));
        }
        List<HolidaySuggestion> out = new ArrayList<>();
        List<String> taken = new ArrayList<>();

        // 1. National holidays on fixed dates
        for (Object[] n : NATIONAL_FIXED) {
            MonthDay md = (MonthDay) n[1];
            for (int y = year.getStartDate().getYear(); y <= year.getEndDate().getYear(); y++) {
                LocalDate d = md.atYear(y);
                if (!d.isBefore(year.getStartDate()) && !d.isAfter(year.getEndDate())) {
                    String name = (String) n[0];
                    taken.add(norm(name));
                    out.add(new HolidaySuggestion("NATIONAL", name, HolidayType.PUBLIC_HOLIDAY, d, d, d.getDayOfWeek(),
                            false, false, isTaken(existingNames, name), "National holiday"));
                }
            }
        }

        // 2. External provider, filtered to the school's state
        String state = region != null && !region.isBlank()
                ? IndianRegions.code(region).orElseThrow(() -> new BusinessException("Unknown Indian state or code: " + region))
                : catalogService.stateOfTenant(TenantContext.getTenantCode()).orElse(null);
        String status = "DISABLED";
        String providerName = null;
        String message = null;
        java.time.Instant fetchedAt = null;
        for (int y = year.getStartDate().getYear(); y <= year.getEndDate().getYear(); y++) {
            Catalog c = catalogService.holidays("IN", y);
            status = worse(status, c.status().name(), y == year.getStartDate().getYear());
            providerName = c.providerName() != null ? c.providerName() : providerName;
            message = c.message() != null ? c.message() : message;
            fetchedAt = c.fetchedAt() != null && (fetchedAt == null || c.fetchedAt().isBefore(fetchedAt)) ? c.fetchedAt() : fetchedAt;
            for (ProviderHoliday h : c.holidays()) {
                if (h.kind() == ProviderHoliday.Kind.OBSERVANCE || isTaken(taken, h.name())) {
                    continue;
                }
                if (!h.nationwide() && (state == null || h.regions().stream().noneMatch(r -> IndianRegions.matches(r, state)))) {
                    continue;
                }
                if (h.endDate().isBefore(year.getStartDate()) || h.startDate().isAfter(year.getEndDate())) {
                    continue;
                }
                taken.add(norm(h.name()));
                boolean optional = h.kind() == ProviderHoliday.Kind.OPTIONAL;
                out.add(new HolidaySuggestion("PROVIDER", h.name(),
                        HolidayType.PUBLIC_HOLIDAY,
                        h.startDate(), h.endDate(), h.startDate().getDayOfWeek(), optional, true,
                        isTaken(existingNames, h.name()),
                        (optional ? "Restricted / optional holiday" : h.nationwide() ? "Public holiday" : "State holiday")
                                + (h.description() != null && !h.description().isBlank() ? ". " + h.description() : "")));
            }
        }
        if (state == null && !"DISABLED".equals(status)) {
            message = "The school's state is unknown, so only nationwide holidays are shown. Add the state to the school address "
                    + "or pass ?region=.";
        }

        // 3. Last year's holidays, moved to this year
        AcademicYear from = fromAcademicYearId != null ? year(fromAcademicYearId) : previousYear(year);
        if (from != null && !from.getId().equals(year.getId())) {
            // Move by whole years so fixed-date holidays keep their date (15 Aug stays 15 Aug).
            long years = Math.max(1, year.getStartDate().getYear() - from.getStartDate().getYear());
            for (SchoolHoliday h : existing(from)) {
                if (isTaken(taken, h.getName())) {
                    continue;
                }
                LocalDate s = h.getStartDate().plusYears(years);
                LocalDate e = h.getEndDate().plusYears(years);
                if (e.isBefore(year.getStartDate()) || s.isAfter(year.getEndDate())) {
                    continue;
                }
                s = s.isBefore(year.getStartDate()) ? year.getStartDate() : s;
                e = e.isAfter(year.getEndDate()) ? year.getEndDate() : e;
                taken.add(norm(h.getName()));
                boolean fixed = isNationalFixed(h.getName()) || h.getHolidayType() == HolidayType.VACATION;
                out.add(new HolidaySuggestion("PREVIOUS_YEAR", h.getName(), h.getHolidayType(), s, e, s.getDayOfWeek(),
                        false, !fixed, isTaken(existingNames, h.getName()),
                        fixed ? h.getDescription() : "Copied from " + from.getCode() + "; festival dates change every year, check the date."));
            }
        }
        out.sort(Comparator.comparing(HolidaySuggestion::startDate));
        return new HolidaySuggestionsResponse(state != null ? "IN-" + state : null, providerName, status, fetchedAt, message,
                "Calendarific".equals(providerName) ? "Holiday data by Calendarific (calendarific.com)" : null, out);
    }

    /** Status of the whole response: the worst of the calendar years covered. */
    private static String worse(String current, String next, boolean first) {
        if (first) {
            return next;
        }
        List<String> order = List.of("OK", "STALE", "UNAVAILABLE", "DISABLED");
        return order.indexOf(next) > order.indexOf(current) ? next : current;
    }

    /** Same holiday under slightly different names ("Diwali" vs "Diwali/Deepavali")? */
    static boolean isTaken(java.util.Collection<String> normalizedNames, String name) {
        String n = norm(name).replaceAll("[^a-z]", "");
        if (n.isEmpty()) {
            return false;
        }
        for (String t : normalizedNames) {
            String x = t.replaceAll("[^a-z]", "");
            if (x.equals(n) || (Math.min(x.length(), n.length()) >= 4 && (x.startsWith(n) || n.startsWith(x)))) {
                return true;
            }
        }
        return false;
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public HolidayPlanResponse plan(UUID academicYearId, HolidayPlanRequest request) {
        AcademicYear year = year(academicYearId);
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        List<SchoolHoliday> existing = existing(year);

        List<Line> lines = new ArrayList<>();
        List<Row> rows = request.holidays();
        boolean valid = true;
        int totalDays = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            String error = null;
            if (r.endDate().isBefore(r.startDate())) {
                error = "End date is before start date";
            } else if (r.startDate().isBefore(year.getStartDate()) || r.endDate().isAfter(year.getEndDate())) {
                error = "Outside the academic year (" + year.getStartDate() + " to " + year.getEndDate() + ")";
            } else {
                for (SchoolHoliday h : existing) {
                    if (overlaps(r.startDate(), r.endDate(), h.getStartDate(), h.getEndDate())) {
                        error = "Overlaps existing holiday '" + h.getName() + "' (" + h.getStartDate() + " to " + h.getEndDate() + ")";
                        break;
                    }
                }
                for (int j = 0; error == null && j < i; j++) {
                    Row o = rows.get(j);
                    if (overlaps(r.startDate(), r.endDate(), o.startDate(), o.endDate())) {
                        error = "Overlaps row " + (j + 1) + " '" + o.name() + "'";
                    }
                }
            }
            int days = error == null && !r.endDate().isBefore(r.startDate())
                    ? (int) ChronoUnit.DAYS.between(r.startDate(), r.endDate()) + 1 : 0;
            String note = null;
            if (error == null && days == 1 && !safeIsWorkingDay(r.startDate())) {
                note = "Falls on a " + r.startDate().getDayOfWeek().name().toLowerCase(Locale.ROOT) + " that is already a non-working day";
            }
            valid &= error == null;
            totalDays += days;
            lines.add(new Line(i + 1, r.name().trim(), r.holidayType(), r.startDate(), r.endDate(), r.startDate().getDayOfWeek(),
                    days, error, note));
        }

        int created = 0;
        if (valid && !dryRun) {
            UUID user = SecurityUtils.getCurrentUserId().orElse(null);
            List<SchoolHoliday> toSave = new ArrayList<>();
            for (Row r : rows) {
                SchoolHoliday h = new SchoolHoliday("HOL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                        year, r.name().trim(), r.holidayType(), r.startDate(), r.endDate(), r.description());
                h.setCreatedBy(user);
                h.setUpdatedBy(user);
                toSave.add(h);
            }
            holidayRepository.saveAll(toSave);
            created = toSave.size();
            log.info("Created {} holidays for academic year [{}]", created, year.getCode());
        }
        return new HolidayPlanResponse(dryRun, valid, created, lines, totalDays);
    }

    // -----------------------------------------------------------------------------------------

    private List<SchoolHoliday> existing(AcademicYear y) {
        return holidayRepository.findOverlappingHolidays(y.getId(), y.getStartDate(), y.getEndDate(), null);
    }

    /** The latest year that started before this one. */
    private AcademicYear previousYear(AcademicYear year) {
        return academicYearRepository.findAll().stream()
                .filter(y -> !y.isDeleted() && y.getStartDate().isBefore(year.getStartDate()))
                .max(Comparator.comparing(AcademicYear::getStartDate))
                .orElse(null);
    }

    private boolean safeIsWorkingDay(LocalDate d) {
        try {
            return calendarService.isWorkingDay(d);
        } catch (RuntimeException e) {
            DayOfWeek w = d.getDayOfWeek();
            return w != DayOfWeek.SUNDAY;
        }
    }

    private static boolean isNationalFixed(String name) {
        String n = norm(name);
        return NATIONAL_FIXED.stream().anyMatch(o -> norm((String) o[0]).equals(n));
    }

    private static boolean overlaps(LocalDate s1, LocalDate e1, LocalDate s2, LocalDate e2) {
        return !s1.isAfter(e2) && !s2.isAfter(e1);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private AcademicYear year(UUID id) {
        return academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));
    }
}
