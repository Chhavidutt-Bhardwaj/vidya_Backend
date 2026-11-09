package com.vidya.tenantdata.academic.setup.service;

import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanRequest;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanRequest.Row;
import com.vidya.tenantdata.academic.setup.dto.HolidayPlanResponse;
import com.vidya.tenantdata.academic.setup.dto.HolidaySuggestion;
import com.vidya.tenantdata.academic.setup.dto.HolidaySuggestionsResponse;
import com.vidya.master.holiday.HolidayCatalogService;
import com.vidya.master.holiday.HolidayCatalogService.Catalog;
import com.vidya.master.holiday.HolidayCatalogService.Status;
import com.vidya.master.holiday.ProviderHoliday;
import com.vidya.master.holiday.ProviderHoliday.Kind;
import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
import com.vidya.tenantdata.calendar.service.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("YearHolidayService — holiday planning for a year")
class YearHolidayServiceTest {

    @Mock private AcademicYearRepository yearRepository;
    @Mock private SchoolHolidayRepository holidayRepository;
    @Mock private CalendarService calendarService;
    @Mock private HolidayCatalogService catalogService;

    private YearHolidayService service;
    private AcademicYear last;
    private AcademicYear year;

    @BeforeEach
    void setUp() {
        service = new YearHolidayService(yearRepository, holidayRepository, calendarService, catalogService);
        when(catalogService.holidays(eq("IN"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new Catalog(Status.DISABLED, null, null, null, "off", List.of()));
        when(catalogService.stateOfTenant(any())).thenReturn(Optional.empty());
        last = year("AY26", LocalDate.of(2026, 4, 1));
        year = year("AY27", LocalDate.of(2027, 4, 1));
        when(yearRepository.findAll()).thenReturn(List.of(last, year));
        when(calendarService.isWorkingDay(any())).thenReturn(true);
        when(holidayRepository.findOverlappingHolidays(eq(year.getId()), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("suggestions: national fixed dates in the year, and last year's festivals moved one year and flagged")
    void suggestions() {
        SchoolHoliday diwali = holiday(last, "Diwali", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 8));
        SchoolHoliday summer = holiday(last, "Summer Vacation", LocalDate.of(2026, 5, 15), LocalDate.of(2026, 6, 30));
        SchoolHoliday independence = holiday(last, "Independence Day", LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15));
        when(holidayRepository.findOverlappingHolidays(eq(last.getId()), any(), any(), any()))
                .thenReturn(List.of(diwali, summer, independence));

        List<HolidaySuggestion> s = service.suggestions(year.getId(), null, null).suggestions();

        assertThat(s).filteredOn(h -> h.source().equals("NATIONAL")).extracting(HolidaySuggestion::name)
                .containsExactly("Independence Day", "Gandhi Jayanti", "Republic Day");
        assertThat(s).filteredOn(h -> h.name().equals("Independence Day")).hasSize(1)
                .first().satisfies(h -> assertThat(h.startDate()).isEqualTo(LocalDate.of(2027, 8, 15)));
        HolidaySuggestion d = s.stream().filter(h -> h.name().equals("Diwali")).findFirst().orElseThrow();
        assertThat(d.startDate()).isEqualTo(LocalDate.of(2027, 11, 8));
        assertThat(d.reviewDate()).isTrue();
        assertThat(s).filteredOn(h -> h.name().equals("Summer Vacation")).first()
                .satisfies(h -> assertThat(h.endDate()).isEqualTo(LocalDate.of(2027, 6, 30)));
    }

    @Test
    @DisplayName("provider: state holidays only for the school's state, observances dropped, provider date beats last year's copy")
    void provider() {
        when(catalogService.stateOfTenant(any())).thenReturn(Optional.of("MH"));
        when(catalogService.holidays("IN", 2027)).thenReturn(new Catalog(Status.OK, "calendarific", "Calendarific", Instant.now(), null, List.of(
                ph("Diwali/Deepavali", "2027-10-29", Kind.PUBLIC, Set.of()),
                ph("Maharashtra Day", "2027-05-01", Kind.PUBLIC, Set.of("MH")),
                ph("Kerala Piravi", "2027-11-01", Kind.PUBLIC, Set.of("Kerala")),
                ph("Karva Chauth", "2027-10-18", Kind.OPTIONAL, Set.of()),
                ph("Mother's Day", "2027-05-09", Kind.OBSERVANCE, Set.of()),
                ph("Independence Day", "2027-08-15", Kind.PUBLIC, Set.of()))));
        when(catalogService.holidays("IN", 2028)).thenReturn(new Catalog(Status.OK, "calendarific", "Calendarific", Instant.now(), null, List.of()));
        when(holidayRepository.findOverlappingHolidays(eq(last.getId()), any(), any(), any()))
                .thenReturn(List.of(holiday(last, "Diwali", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 8))));

        HolidaySuggestionsResponse r = service.suggestions(year.getId(), null, null);

        assertThat(r.region()).isEqualTo("IN-MH");
        assertThat(r.providerStatus()).isEqualTo("OK");
        assertThat(r.attribution()).contains("Calendarific");
        assertThat(r.suggestions()).extracting(HolidaySuggestion::name)
                .contains("Maharashtra Day", "Diwali/Deepavali", "Karva Chauth")
                .doesNotContain("Kerala Piravi", "Mother's Day", "Diwali");
        assertThat(r.suggestions()).filteredOn(h -> h.name().equals("Independence Day")).singleElement()
                .satisfies(h -> assertThat(h.source()).isEqualTo("NATIONAL"));
        assertThat(r.suggestions()).filteredOn(h -> h.name().equals("Karva Chauth")).singleElement()
                .satisfies(h -> assertThat(h.optional()).isTrue());
    }

    @Test
    @DisplayName("provider down or off: suggestions still work and say why provider data is missing")
    void providerUnavailable() {
        when(catalogService.holidays(eq("IN"), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new Catalog(Status.UNAVAILABLE, "calendarific", "Calendarific", null, "Holiday provider is unavailable", List.of()));

        HolidaySuggestionsResponse r = service.suggestions(year.getId(), null, "Karnataka");

        assertThat(r.region()).isEqualTo("IN-KA");
        assertThat(r.providerStatus()).isEqualTo("UNAVAILABLE");
        assertThat(r.message()).contains("unavailable");
        assertThat(r.suggestions()).extracting(HolidaySuggestion::source).containsOnly("NATIONAL");
    }

    private static ProviderHoliday ph(String name, String date, Kind kind, Set<String> regions) {
        LocalDate d = LocalDate.parse(date);
        return new ProviderHoliday(name, d, d, kind, null, regions);
    }

    @Test
    @DisplayName("bulk: valid rows are saved together")
    void bulkSaves() {
        HolidayPlanResponse r = service.plan(year.getId(), new HolidayPlanRequest(List.of(
                row("Republic Day", "2028-01-26", "2028-01-26"),
                row("Winter Break", "2027-12-25", "2028-01-01")), null));

        assertThat(r.valid()).isTrue();
        assertThat(r.created()).isEqualTo(2);
        assertThat(r.totalHolidayDays()).isEqualTo(9);
        verify(holidayRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("bulk: one bad row (outside year, overlap with another row or an existing holiday) saves nothing")
    void bulkAllOrNothing() {
        SchoolHoliday existing = holiday(year, "Holi", LocalDate.of(2028, 3, 2), LocalDate.of(2028, 3, 2));
        when(holidayRepository.findOverlappingHolidays(eq(year.getId()), any(), any(), any())).thenReturn(List.of(existing));

        HolidayPlanResponse r = service.plan(year.getId(), new HolidayPlanRequest(List.of(
                row("Ok", "2027-08-15", "2027-08-15"),
                row("Too late", "2028-04-05", "2028-04-05"),
                row("Break", "2027-10-20", "2027-10-25"),
                row("Clash", "2027-10-24", "2027-10-24"),
                row("Holi again", "2028-03-02", "2028-03-02")), null));

        assertThat(r.valid()).isFalse();
        assertThat(r.created()).isZero();
        assertThat(r.holidays()).extracting(HolidayPlanResponse.Line::error)
                .satisfiesExactly(
                        e -> assertThat(e).isNull(),
                        e -> assertThat(e).contains("Outside"),
                        e -> assertThat(e).isNull(),
                        e -> assertThat(e).contains("row 3"),
                        e -> assertThat(e).contains("Holi"));
        verify(holidayRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("bulk dry run validates without saving")
    void dryRun() {
        HolidayPlanResponse r = service.plan(year.getId(), new HolidayPlanRequest(List.of(row("A", "2027-08-15", "2027-08-15")), true));

        assertThat(r.valid()).isTrue();
        assertThat(r.created()).isZero();
        verify(holidayRepository, never()).saveAll(anyList());
    }

    private static Row row(String name, String from, String to) {
        return new Row(name, HolidayType.PUBLIC_HOLIDAY, LocalDate.parse(from), LocalDate.parse(to), null);
    }

    private AcademicYear year(String code, LocalDate start) {
        AcademicYear y = new AcademicYear(code, code, start, start.plusYears(1).minusDays(1), false, AcademicYearStatus.ACTIVE);
        y.setId(UUID.randomUUID());
        when(yearRepository.findByIdAndNotDeleted(y.getId())).thenReturn(Optional.of(y));
        return y;
    }

    private static SchoolHoliday holiday(AcademicYear y, String name, LocalDate s, LocalDate e) {
        return new SchoolHoliday("HOL-" + name, y, name, HolidayType.SCHOOL_HOLIDAY, s, e, null);
    }
}
