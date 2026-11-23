package com.vidya.master.holiday;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Holidays from a public iCalendar (.ics) feed, e.g. Google's "Holidays in India" calendar. Feeds
 * have no state information, so every event counts as nationwide; the event description decides the
 * kind ("Public holiday", "Restricted holiday", "Observance"). Feeds usually cover only the
 * previous, current and next year.
 */
public class IcsHolidayProvider implements HolidayProvider {

    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

    private final String url;
    private final Duration timeout;
    private final HttpClient http;

    public IcsHolidayProvider(String url, Duration timeout) {
        this(url, timeout, HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    IcsHolidayProvider(String url, Duration timeout, HttpClient http) {
        this.url = url;
        this.timeout = timeout;
        this.http = http;
    }

    @Override
    public String id() {
        return "ics";
    }

    @Override
    public String displayName() {
        return "Public holiday calendar feed";
    }

    @Override
    public List<ProviderHoliday> fetch(String countryCode, int year) {
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new HolidayProviderException("Holiday feed unreachable: " + e.getClass().getSimpleName(), e);
        }
        if (response.statusCode() != 200) {
            throw new HolidayProviderException("Holiday feed answered HTTP " + response.statusCode());
        }
        return parse(response.body()).stream().filter(h -> h.startDate().getYear() == year).toList();
    }

    /** Minimal RFC 5545 reader: unfolds lines and reads DTSTART, DTEND, SUMMARY, DESCRIPTION of VEVENTs. */
    static List<ProviderHoliday> parse(String ics) {
        List<String> lines = new ArrayList<>();
        for (String raw : ics.split("\r?\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && !lines.isEmpty()) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + raw.substring(1));
            } else {
                lines.add(raw);
            }
        }
        List<ProviderHoliday> out = new ArrayList<>();
        LocalDate start = null;
        LocalDate end = null;
        String summary = null;
        String description = null;
        boolean inEvent = false;
        for (String line : lines) {
            if (line.equals("BEGIN:VEVENT")) {
                inEvent = true;
                start = end = null;
                summary = description = null;
            } else if (line.equals("END:VEVENT")) {
                if (inEvent && start != null && summary != null) {
                    // DTEND of an all-day event is exclusive.
                    LocalDate last = end != null && end.isAfter(start) ? end.minusDays(1) : start;
                    out.add(new ProviderHoliday(summary, start, last, kind(description), description, Set.of()));
                }
                inEvent = false;
            } else if (inEvent) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String name = line.substring(0, colon).toUpperCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (name.startsWith("DTSTART")) {
                    start = date(value);
                } else if (name.startsWith("DTEND")) {
                    end = date(value);
                } else if (name.equals("SUMMARY") || name.startsWith("SUMMARY;")) {
                    summary = unescape(value);
                } else if (name.equals("DESCRIPTION") || name.startsWith("DESCRIPTION;")) {
                    description = unescape(value);
                }
            }
        }
        return out;
    }

    static ProviderHoliday.Kind kind(String description) {
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (d.contains("public holiday") || d.contains("gazetted") || d.contains("national holiday")) {
            return ProviderHoliday.Kind.PUBLIC;
        }
        if (d.contains("observance") || d.contains("season")) {
            return ProviderHoliday.Kind.OBSERVANCE;
        }
        return ProviderHoliday.Kind.OPTIONAL;
    }

    private static LocalDate date(String v) {
        String d = v.length() >= 8 ? v.substring(0, 8) : v;
        try {
            return LocalDate.parse(d, BASIC);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String unescape(String v) {
        return v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim();
    }
}
