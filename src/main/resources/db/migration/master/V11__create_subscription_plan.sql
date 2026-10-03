-- ============================================================
-- V11: subscription_plan
-- Versioned subscription plans offered by the platform
--
-- Key design:
--   - (plan_code, version) is the unique key — same code can
--     have multiple versions (v1, v2, v3)
--   - A plan is NEVER mutated after it has active subscribers
--   - Updates create a new version row (plan_code same, version incremented)
-- ============================================================

CREATE TABLE subscription_plan (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    plan_code       VARCHAR(50)    NOT NULL,
    version         INTEGER        NOT NULL DEFAULT 1,
    name            VARCHAR(200)   NOT NULL,
    description     TEXT,
    billing_cycle   VARCHAR(30)    NOT NULL,
    base_price      NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(10)    NOT NULL DEFAULT 'INR',
    trial_days      INTEGER        NOT NULL DEFAULT 0,
    status          VARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
    effective_from  DATE           NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version_lock    BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_subscription_plan PRIMARY KEY (id),
    CONSTRAINT uq_plan_code_version UNIQUE (plan_code, version),
    CONSTRAINT chk_plan_billing_cycle CHECK (billing_cycle IN (
        'MONTHLY', 'QUARTERLY', 'ANNUAL', 'ONE_TIME'
    )),
    CONSTRAINT chk_plan_status CHECK (status IN (
        'DRAFT', 'ACTIVE', 'DEPRECATED', 'ARCHIVED'
    )),
    CONSTRAINT chk_plan_base_price CHECK (base_price >= 0),
    CONSTRAINT chk_plan_trial_days CHECK (trial_days >= 0)
);

CREATE INDEX idx_subscription_plan_code   ON subscription_plan(plan_code);
CREATE INDEX idx_subscription_plan_status ON subscription_plan(status);
CREATE INDEX idx_subscription_plan_dates  ON subscription_plan(effective_from, effective_to);

COMMENT ON TABLE  subscription_plan IS 'Versioned plans offered by the Vidya platform; plan updates create new versions, old versions are preserved';
COMMENT ON COLUMN subscription_plan.plan_code IS 'Human-readable code, e.g. BASIC, STANDARD, PREMIUM; shared across versions';
COMMENT ON COLUMN subscription_plan.version   IS 'Version number; (plan_code + version) uniquely identifies a plan snapshot';
COMMENT ON COLUMN subscription_plan.effective_to IS 'NULL means currently valid; set when plan is replaced by a newer version';
