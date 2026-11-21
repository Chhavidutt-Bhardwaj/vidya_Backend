package com.vidya.master.holiday.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** Master-DB access for the shared holiday cache and the school-address lookup used by holiday suggestions. */
@Repository
public class HolidayCatalogRepository {

    /** One cached (provider, country, year) entry: the holidays JSON and when it was fetched. */
    public record CachedEntry(String holidaysJson, Instant fetchedAt) {
    }

    @PersistenceContext(unitName = "master")
    private EntityManager em;

    public Optional<CachedEntry> findCached(String provider, String country, int year) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT CAST(holidays AS text), fetched_at FROM holiday_catalog_cache WHERE provider = :p AND country_code = :c AND year = :y")
                .setParameter("p", provider).setParameter("c", country).setParameter("y", year).getResultList();
        return rows.stream().findFirst().map(r -> new CachedEntry(String.valueOf(r[0]), toInstant(r[1])));
    }

    public void upsert(String provider, String country, int year, String holidaysJson) {
        em.createNativeQuery("""
                        INSERT INTO holiday_catalog_cache (provider, country_code, year, holidays, fetched_at)
                        VALUES (:p, :c, :y, CAST(:h AS jsonb), NOW())
                        ON CONFLICT (provider, country_code, year) DO UPDATE SET holidays = EXCLUDED.holidays, fetched_at = NOW()
                        """)
                .setParameter("p", provider).setParameter("c", country).setParameter("y", year)
                .setParameter("h", holidaysJson).executeUpdate();
    }

    /** Deletes cached years of a country; a null year deletes every year. */
    public int evict(String country, Integer year) {
        return em.createNativeQuery("DELETE FROM holiday_catalog_cache WHERE country_code = :c AND (CAST(:y AS integer) IS NULL OR year = :y)")
                .setParameter("c", country).setParameter("y", year).executeUpdate();
    }

    /** Raw state text of the tenant's school, preferring the primary / campus address. */
    public Optional<String> findSchoolStateByTenantCode(String tenantCode) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("""
                        SELECT a.state FROM school_address a JOIN tenant t ON t.school_id = a.school_id
                        WHERE t.tenant_code = :code
                        ORDER BY a.is_primary DESC, (a.address_type = 'CAMPUS_PRIMARY') DESC, a.created_at
                        LIMIT 1
                        """)
                .setParameter("code", tenantCode).getResultList();
        return rows.stream().findFirst().map(o -> (String) o);
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (o instanceof Timestamp ts) {
            return ts.toInstant();
        }
        return Instant.parse(String.valueOf(o));
    }
}
