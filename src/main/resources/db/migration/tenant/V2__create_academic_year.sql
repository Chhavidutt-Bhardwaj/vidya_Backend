-- ============================================================
-- TENANT V2: academic_year
-- Placeholder table for the tenant operational schema
-- Full implementation in Phase 02 (Academic module)
-- ============================================================

CREATE TABLE academic_year (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    label       VARCHAR(20)  NOT NULL,          -- e.g. "2026-27"
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    is_current  BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_academic_year PRIMARY KEY (id),
    CONSTRAINT uq_academic_year_label UNIQUE (label),
    CONSTRAINT chk_academic_year_dates CHECK (end_date > start_date),
    CONSTRAINT chk_academic_year_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_academic_year_current ON academic_year(is_current);
CREATE INDEX idx_academic_year_status  ON academic_year(status);

COMMENT ON TABLE  academic_year IS 'Placeholder: Phase 02 will add class, section, subject tables under this table';
COMMENT ON COLUMN academic_year.label      IS 'Human-readable year label, e.g. 2026-27';
COMMENT ON COLUMN academic_year.is_current IS 'Only one row should have is_current = TRUE at a time';
