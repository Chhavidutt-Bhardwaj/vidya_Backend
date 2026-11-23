package com.vidya.master.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <a href="https://calendarific.com">Calendarific</a> holidays API. One call returns the whole
 * country for a year, including state-specific holidays (filtered on our side), so the free quota
 * is enough when results are cached per year.
 */
public class CalendarificHolidayProvider implements HolidayProvider {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    public CalendarificHolidayProvider(String baseUrl, String apiKey, Duration timeout) {
        this(baseUrl, apiKey, timeout, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    CalendarificHolidayProvider(String baseUrl, String apiKey, Duration timeout, HttpClient http) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.http = http;
    }

    @Override
    public String id() {
        return "calendarific";
    }

    @Override
    public String displayName() {
        return "Calendarific";
    }

    @Override
    public List<ProviderHoliday> fetch(String countryCode, int year) {
        URI uri = URI.create(baseUrl + "/holidays?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&country=" + URLEncoder.encode(countryCode, StandardCharsets.UTF_8) + "&year=" + year);
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // Never include the URI: it carries the API key.
            throw new HolidayProviderException("Calendarific unreachable: " + e.getClass().getSimpleName(), e);
        }
        if (response.statusCode() != 200) {
            throw new HolidayProviderException("Calendarific answered HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    List<ProviderHoliday> parse(String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new HolidayProviderException("Calendarific returned invalid JSON", e);
        }
        int code = root.path("meta").path("code").asInt(200);
        if (code != 200) {
            throw new HolidayProviderException("Calendarific error " + code + ": " + root.path("meta").path("error_detail").asText(""));
        }
        List<ProviderHoliday> out = new ArrayList<>();
        for (JsonNode h : root.path("response").path("holidays")) {
            String iso = h.path("date").path("iso").asText("");
            if (iso.length() < 10) {
                continue;
            }
            LocalDate date = LocalDate.parse(iso.substring(0, 10));
            Set<String> regions = new LinkedHashSet<>();
            JsonNode states = h.path("states");
            if (states.isArray()) {
                for (JsonNode s : states) {
                    addIfText(regions, s.path("abbrev"));
                    addIfText(regions, s.path("name"));
                    addIfText(regions, s.path("iso"));
                }
            } else {
                String locations = h.path("locations").asText("All");
                if (!"all".equalsIgnoreCase(locations.trim())) {
                    for (String l : locations.split(",")) {
                        if (!l.isBlank()) {
                            regions.add(l.trim());
                        }
                    }
                }
            }
            List<String> types = new ArrayList<>();
            h.path("type").forEach(t -> types.add(t.asText()));
            out.add(new ProviderHoliday(h.path("name").asText().trim(), date, date, kind(types),
                    h.path("description").asText(null), regions));
        }
        return out;
    }

    /** Calendarific type labels → our kinds. Unknown labels count as optional (shown, unticked). */
    static ProviderHoliday.Kind kind(List<String> types) {
        String t = String.join("|", types).toLowerCase(Locale.ROOT);
        if (t.contains("national") || t.contains("gazetted") || t.contains("public") || t.contains("local holiday")
                || t.contains("bank holiday")) {
            return ProviderHoliday.Kind.PUBLIC;
        }
        if (t.contains("restricted") || t.contains("optional")) {
            return ProviderHoliday.Kind.OPTIONAL;
        }
        if (t.contains("observance") || t.contains("season") || t.contains("clock change") || t.contains("united nations")
                || t.contains("worldwide")) {
            return ProviderHoliday.Kind.OBSERVANCE;
        }
        return ProviderHoliday.Kind.OPTIONAL;
    }

    private static void addIfText(Set<String> set, JsonNode n) {
        if (n.isTextual() && !n.asText().isBlank()) {
            set.add(n.asText().trim());
        }
    }
}
