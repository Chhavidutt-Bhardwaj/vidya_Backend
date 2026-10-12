-- ============================================================
-- TENANT V15: Academic Calendar & School Holidays
-- 1. Table: school_holiday
-- 2. Table: working_day_config
-- 3. Check Constraints and Partial Unique Indexes
-- ============================================================

CREATE TABLE IF NOT EXISTS school_holiday (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_code VARCHAR(50) NOT NULL UNIQUE,
    academic_year_id UUID NOT NULL REFERENCES academic_year(id),
    name VARCHAR(100) NOT NULL,
    holiday_type VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    updated_by UUID NULL,
    deleted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_holiday_type CHECK (holiday_type IN ('PUBLIC_HOLIDAY', 'SCHOOL_HOLIDAY', 'VACATION', 'EXAM_BREAK')),
    CONSTRAINT chk_holiday_dates CHECK (start_date <= end_date)
);

CREATE UNIQUE INDEX idx_school_holiday_unique ON school_holiday (
    academic_year_id, lower(name), start_date
) WHERE deleted_at IS NULL;

CREATE INDEX idx_school_holiday_ay ON school_holiday (academic_year_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_school_holiday_dates ON school_holiday (academic_year_id, start_date, end_date) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS working_day_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    academic_year_id UUID NOT NULL REFERENCES academic_year(id),
    monday BOOLEAN NOT NULL DEFAULT TRUE,
    tuesday BOOLEAN NOT NULL DEFAULT TRUE,
    wednesday BOOLEAN NOT NULL DEFAULT TRUE,
    thursday BOOLEAN NOT NULL DEFAULT TRUE,
    friday BOOLEAN NOT NULL DEFAULT TRUE,
    saturday BOOLEAN NOT NULL DEFAULT TRUE,
    sunday BOOLEAN NOT NULL DEFAULT FALSE,
    saturday_pattern VARCHAR(30) NOT NULL DEFAULT 'ALL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    updated_by UUID NULL,
    deleted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_saturday_pattern CHECK (saturday_pattern IN ('ALL', 'FIRST_THIRD_FIFTH', 'SECOND_FOURTH'))
);

CREATE UNIQUE INDEX idx_working_day_config_ay ON working_day_config (
    academic_year_id
) WHERE deleted_at IS NULL;
