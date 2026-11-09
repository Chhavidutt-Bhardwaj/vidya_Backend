package com.vidya.master.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Holidays and festivals from an external provider, cached in the master DB per (provider,
 * country, year) so all schools share one call per year.
 *
 * <p>Provider selection ({@code vidya.holidays.provider}):
 * <ul>
 *   <li>{@code auto} (default): Calendarific when an API key is set, otherwise off</li>
 *   <li>{@code calendarific}: requires {@code vidya.holidays.calendarific.api-key}</li>
 *   <li>{@code ics}: a public iCal feed ({@code vidya.holidays.ics.url}), nationwide only</li>
 *   <li>{@code none}: off</li>
 * </ul>
 * The provider only feeds suggestions; nothing is saved to a school's calendar without the admin.
 */
@Slf4j
@Service
public class HolidayCatalogService {

    public enum Status { OK, STALE, DISABLED, UNAVAILABLE }

    /** Holidays of one calendar year plus how they were obtained. */
    public record Catalog(Status status, String provider, String providerName, Instant fetchedAt, String message,
                          List<ProviderHoliday> holidays) {
    }

    private final HolidayProvider provider;
    private final Duration cacheFor;
    private final ObjectMapper json = new ObjectMapper();

    @PersistenceContext(unitName = "master")
    private EntityManager em;

    public HolidayCatalogService(@Value("${vidya.holidays.provider:auto}") String providerName,
                                 @Value("${vidya.holidays.calendarific.api-key:}") String calendarificKey,
                                 @Value("${vidya.holidays.calendarific.base-url:https://calendarific.com/api/v2}") String calendarificUrl,
                                 @Value("${vidya.holidays.ics.url:}") String icsUrl,
                                 @Value("${vidya.holidays.timeout-seconds:8}") long timeoutSeconds,
                                 @Value("${vidya.holidays.cache-days:30}") long cacheDays) {
        this(select(providerName, calendarificKey, calendarificUrl, icsUrl, Duration.ofSeconds(timeoutSeconds)),
                Duration.ofDays(cacheDays));
    }

    HolidayCatalogService(HolidayProvider provider, Duration cacheFor) {
        this.provider = provider;
        this.cacheFor = cacheFor;
        log.info("Holiday provider: {}", provider == null ? "none" : provider.id());
    }

    static HolidayProvider select(String name, String key, String calendarificUrl, String icsUrl, Duration timeout) {
        String n = name == null ? "auto" : name.trim().toLowerCase(Locale.ROOT);
        boolean hasKey = key != null && !key.isBlank();
        return switch (n) {
            case "calendarific" -> {
                if (!hasKey) {
                    log.warn("vidya.holidays.provider=calendarific but no API key is set; holiday provider disabled");
                    yield null;
                }
                yield new CalendarificHolidayProvider(calendarificUrl, key.trim(), timeout);
            }
            case "ics" -> {
                if (icsUrl == null || icsUrl.isBlank()) {
                    log.warn("vidya.holidays.provider=ics but vidya.holidays.ics.url is empty; holiday provider disabled");
                    yield null;
                }
                yield new IcsHolidayProvider(icsUrl.trim(), timeout);
            }
            case "none", "off", "disabled" -> null;
            default -> hasKey ? new CalendarificHolidayProvider(calendarificUrl, key.trim(), timeout) : null;
        };
    }

    public boolean enabled() {
        return provider != null;
    }

    /**
     * Holidays of a calendar year. Fresh cache → cache; otherwise fetch and store. When the provider
     * fails, a stale cache is returned with status STALE, or an empty list with UNAVAILABLE.
     */
    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Catalog holidays(String countryCode, int year) {
        if (provider == null) {
            return new Catalog(Status.DISABLED, null, null, null,
                    "No holiday provider is configured (set CALENDARIFIC_API_KEY or VIDYA_HOLIDAY_PROVIDER=ics).", List.of());
        }
        String country = countryCode.toUpperCase(Locale.ROOT);
        Optional<Object[]> cached = cached(country, year);
        if (cached.isPresent()) {
            Instant fetchedAt = toInstant(cached.get()[1]);
            if (fetchedAt.plus(cacheFor).isAfter(Instant.now())) {
                return new Catalog(Status.OK, provider.id(), provider.displayName(), fetchedAt, null, read(cached.get()[0]));
            }
        }
        try {
            List<ProviderHoliday> fresh = provider.fetch(country, year);
            store(country, year, fresh);
            log.info("Fetched {} holidays for {} {} from {}", fresh.size(), country, year, provider.id());
            return new Catalog(Status.OK, provider.id(), provider.displayName(), Instant.now(), null, fresh);
        } catch (RuntimeException e) {
            log.warn("Holiday provider {} failed for {} {}: {}", provider.id(), country, year, e.getMessage());
            if (cached.isPresent()) {
                return new Catalog(Status.STALE, provider.id(), provider.displayName(), toInstant(cached.get()[1]),
                        "Provider unavailable; showing data fetched earlier.", read(cached.get()[0]));
            }
            return new Catalog(Status.UNAVAILABLE, provider.id(), provider.displayName(), null,
                    "Holiday provider is unavailable right now; try again later.", List.of());
        }
    }

    /** State of the school that owns a tenant, from its primary address (ISO code such as "MH"). */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Optional<String> stateOfTenant(String tenantCode) {
        if (tenantCode == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("""
                        SELECT a.state FROM school_address a JOIN tenant t ON t.school_id = a.school_id
                        WHERE t.tenant_code = :code
                        ORDER BY a.is_primary DESC, (a.address_type = 'CAMPUS_PRIMARY') DESC, a.created_at
                        LIMIT 1
                        """)
                .setParameter("code", tenantCode).getResultList();
        return rows.isEmpty() ? Optional.empty() : IndianRegions.code((String) rows.get(0));
    }

    /** Drops cached years so the next request refetches (e.g. after the provider corrected data). */
    @Transactional(transactionManager = "masterTransactionManager")
    public int evict(String countryCode, Integer year) {
        return em.createNativeQuery("DELETE FROM holiday_catalog_cache WHERE country_code = :c AND (CAST(:y AS integer) IS NULL OR year = :y)")
                .setParameter("c", countryCode.toUpperCase(Locale.ROOT)).setParameter("y", year).executeUpdate();
    }

    // -----------------------------------------------------------------------------------------

    private Optional<Object[]> cached(String country, int year) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT CAST(holidays AS text), fetched_at FROM holiday_catalog_cache WHERE provider = :p AND country_code = :c AND year = :y")
                .setParameter("p", provider.id()).setParameter("c", country).setParameter("y", year).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private void store(String country, int year, List<ProviderHoliday> holidays) {
        em.createNativeQuery("""
                        INSERT INTO holiday_catalog_cache (provider, country_code, year, holidays, fetched_at)
                        VALUES (:p, :c, :y, CAST(:h AS jsonb), NOW())
                        ON CONFLICT (provider, country_code, year) DO UPDATE SET holidays = EXCLUDED.holidays, fetched_at = NOW()
                        """)
                .setParameter("p", provider.id()).setParameter("c", country).setParameter("y", year)
                .setParameter("h", write(holidays)).executeUpdate();
    }

    String write(List<ProviderHoliday> holidays) {
        ArrayNode arr = json.createArrayNode();
        for (ProviderHoliday h : holidays) {
            ObjectNode o = arr.addObject();
            o.put("name", h.name());
            o.put("start", h.startDate().toString());
            o.put("end", h.endDate().toString());
            o.put("kind", h.kind().name());
            o.put("description", h.description());
            ArrayNode r = o.putArray("regions");
            h.regions().forEach(r::add);
        }
        return arr.toString();
    }

    List<ProviderHoliday> read(Object text) {
        try {
            List<ProviderHoliday> out = new ArrayList<>();
            for (JsonNode o : json.readTree(String.valueOf(text))) {
                Set<String> regions = new LinkedHashSet<>();
                o.path("regions").forEach(r -> regions.add(r.asText()));
                out.add(new ProviderHoliday(o.path("name").asText(), LocalDate.parse(o.path("start").asText()),
                        LocalDate.parse(o.path("end").asText()), ProviderHoliday.Kind.valueOf(o.path("kind").asText("OPTIONAL")),
                        o.path("description").isNull() ? null : o.path("description").asText(null), regions));
            }
            return out;
        } catch (Exception e) {
            log.warn("Unreadable holiday cache entry: {}", e.getMessage());
            return List.of();
        }
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        return Instant.parse(String.valueOf(o));
    }
}
