-- =====================================================================================
-- V34: Cache of holidays fetched from an external calendar provider (Calendarific / iCal feed).
--
-- One row per provider, country and calendar year, shared by every school; state filtering
-- happens in the application. Refreshed after vidya.holidays.cache-days, and kept (stale) when
-- the provider is down.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS holiday_catalog_cache (
    provider      VARCHAR(30)  NOT NULL,
    country_code  VARCHAR(2)   NOT NULL,
    year          INT          NOT NULL,
    holidays      JSONB        NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_holiday_catalog_cache PRIMARY KEY (provider, country_code, year),
    CONSTRAINT chk_holiday_catalog_year CHECK (year BETWEEN 2000 AND 2100)
);

COMMENT ON TABLE holiday_catalog_cache IS 'External holiday provider results per (provider, country, year); see docs: ACADEMIC_SETUP.md §5.2';
