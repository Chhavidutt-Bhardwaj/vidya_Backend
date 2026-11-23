package com.vidya.master.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Holiday providers — parsing, regions, selection, cache format")
class HolidayProvidersTest {

    private static final String CALENDARIFIC = """
            {"meta":{"code":200},"response":{"holidays":[
              {"name":"Republic Day","description":"Republic Day is a national holiday","date":{"iso":"2027-01-26"},
               "type":["National holiday"],"locations":"All","states":"All"},
              {"name":"Maharashtra Day","description":"","date":{"iso":"2027-05-01"},"type":["Common local holiday"],
               "locations":"MH","states":[{"id":1,"abbrev":"MH","name":"Maharashtra","exception":null,"iso":"in-mh"}]},
              {"name":"Holi","description":"","date":{"iso":"2027-03-22"},"type":["Gazetted Holiday"],"locations":"All","states":"All"},
              {"name":"Karva Chauth","description":"","date":{"iso":"2027-10-18"},"type":["Restricted Holiday"],"locations":"All","states":"All"},
              {"name":"March Equinox","description":"","date":{"iso":"2027-03-21T01:24:00+05:30"},"type":["Season"],"locations":"All","states":"All"}
            ]}}
            """;

    @Test
    @DisplayName("Calendarific: types map to public / optional / observance, states become regions")
    void calendarific() {
        List<ProviderHoliday> h = new CalendarificHolidayProvider("https://x", "k", Duration.ofSeconds(1)).parse(CALENDARIFIC);

        assertThat(h).extracting(ProviderHoliday::name)
                .containsExactly("Republic Day", "Maharashtra Day", "Holi", "Karva Chauth", "March Equinox");
        assertThat(h).extracting(ProviderHoliday::kind).containsExactly(ProviderHoliday.Kind.PUBLIC, ProviderHoliday.Kind.PUBLIC,
                ProviderHoliday.Kind.PUBLIC, ProviderHoliday.Kind.OPTIONAL, ProviderHoliday.Kind.OBSERVANCE);
        assertThat(h.get(0).nationwide()).isTrue();
        assertThat(h.get(1).regions()).contains("MH", "Maharashtra");
        assertThat(h.get(4).startDate()).isEqualTo(LocalDate.of(2027, 3, 21));
    }

    @Test
    @DisplayName("Calendarific error responses raise a provider error (never containing the key)")
    void calendarificError() {
        CalendarificHolidayProvider p = new CalendarificHolidayProvider("https://x", "secret-key", Duration.ofSeconds(1));
        assertThatThrownBy(() -> p.parse("{\"meta\":{\"code\":401,\"error_detail\":\"Invalid API key\"}}"))
                .isInstanceOf(HolidayProvider.HolidayProviderException.class)
                .hasMessageContaining("401").hasMessageNotContaining("secret-key");
    }

    @Test
    @DisplayName("iCal: folded lines, all-day DTEND is exclusive, description decides the kind")
    void ics() {
        String ics = String.join("\r\n",
                "BEGIN:VCALENDAR",
                "BEGIN:VEVENT",
                "DTSTART;VALUE=DATE:20271029",
                "DTEND;VALUE=DATE:20271030",
                "SUMMARY:Diwali/Deepa",
                " vali",
                "DESCRIPTION:Public holiday",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "DTSTART;VALUE=DATE:20271018",
                "DTEND;VALUE=DATE:20271019",
                "SUMMARY:Karva Chauth",
                "DESCRIPTION:Observance\\nTo hide observances, go to Google Calendar Settings",
                "END:VEVENT",
                "END:VCALENDAR");

        List<ProviderHoliday> h = IcsHolidayProvider.parse(ics);

        assertThat(h).hasSize(2);
        assertThat(h.get(0).name()).isEqualTo("Diwali/Deepavali");
        assertThat(h.get(0).endDate()).isEqualTo(LocalDate.of(2027, 10, 29));
        assertThat(h.get(0).kind()).isEqualTo(ProviderHoliday.Kind.PUBLIC);
        assertThat(h.get(1).kind()).isEqualTo(ProviderHoliday.Kind.OBSERVANCE);
    }

    @Test
    @DisplayName("regions: names, codes, IN- prefix and pre-2023 codes all resolve")
    void regions() {
        assertThat(IndianRegions.code("Maharashtra")).contains("MH");
        assertThat(IndianRegions.code("IN-KA")).contains("KA");
        assertThat(IndianRegions.code("in-mh")).contains("MH");
        assertThat(IndianRegions.code("Orissa")).contains("OD");
        assertThat(IndianRegions.code("OR")).contains("OD");
        assertThat(IndianRegions.code("UT")).contains("UK");
        assertThat(IndianRegions.code("Jammu & Kashmir")).contains("JK");
        assertThat(IndianRegions.code("Atlantis")).isEmpty();
        assertThat(IndianRegions.matches("TG", "TS")).isTrue();
        assertThat(IndianRegions.matches("Kerala", "MH")).isFalse();
    }

    @Test
    @DisplayName("provider selection: auto needs a key, misconfiguration disables instead of failing")
    void selection() {
        Duration t = Duration.ofSeconds(1);
        assertThat(HolidayCatalogService.select("auto", "", "https://x", "", t)).isNull();
        assertThat(HolidayCatalogService.select("auto", "k", "https://x", "", t)).isInstanceOf(CalendarificHolidayProvider.class);
        assertThat(HolidayCatalogService.select("calendarific", null, "https://x", "", t)).isNull();
        assertThat(HolidayCatalogService.select("ics", "", "https://x", "https://feed.ics", t)).isInstanceOf(IcsHolidayProvider.class);
        assertThat(HolidayCatalogService.select("none", "k", "https://x", "https://feed.ics", t)).isNull();
    }

    @Test
    @DisplayName("cache JSON round-trips")
    void cacheRoundTrip() {
        HolidayCatalogService svc = new HolidayCatalogService(null, Duration.ofDays(1), null);
        List<ProviderHoliday> in = List.of(new ProviderHoliday("Maharashtra Day", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 1),
                ProviderHoliday.Kind.PUBLIC, null, Set.of("MH")));

        assertThat(svc.read(svc.write(in))).isEqualTo(in);
    }
}
