-- ============================================================
-- V14: school_subscription
-- A school's active subscription to a specific plan version
--
-- Key design:
--   - plan_code and plan_version are DENORMALIZED from subscription_plan
--   - This snapshot ensures historical accuracy even if the plan
--     is updated or deprecated later
--   - plan_id still references the original plan row for relational integrity
-- ============================================================

CREATE TABLE school_subscription (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    subscription_code   VARCHAR(50)    NOT NULL,
    school_id           UUID           NOT NULL,
    plan_id             UUID           NOT NULL,
    plan_code           VARCHAR(50)    NOT NULL,           -- denormalized snapshot
    plan_version        INTEGER        NOT NULL,           -- denormalized snapshot
    start_date          DATE           NOT NULL,
    end_date            DATE,
    billing_cycle       VARCHAR(30)    NOT NULL,
    base_amount         NUMERIC(12, 2) NOT NULL,
    discount_amount     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    final_amount        NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(10)    NOT NULL DEFAULT 'INR',
    status              VARCHAR(30)    NOT NULL DEFAULT 'TRIAL',
    auto_renew          BOOLEAN        NOT NULL DEFAULT TRUE,
    trial_end_date      DATE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_school_subscription PRIMARY KEY (id),
    CONSTRAINT uq_school_subscription_code UNIQUE (subscription_code),
    CONSTRAINT fk_school_subscription_school
        FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_school_subscription_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plan(id),
    CONSTRAINT chk_subscription_status CHECK (status IN (
        'TRIAL', 'ACTIVE', 'PAYMENT_PENDING', 'PAST_DUE',
        'SUSPENDED', 'EXPIRED', 'CANCELLED'
    )),
    CONSTRAINT chk_subscription_billing_cycle CHECK (billing_cycle IN (
        'MONTHLY', 'QUARTERLY', 'ANNUAL', 'ONE_TIME'
    )),
    CONSTRAINT chk_subscription_amounts CHECK (
        base_amount >= 0 AND
        discount_amount >= 0 AND
        tax_amount >= 0 AND
        final_amount >= 0
    )
);

CREATE INDEX idx_school_subscription_school  ON school_subscription(school_id);
CREATE INDEX idx_school_subscription_plan    ON school_subscription(plan_id);
CREATE INDEX idx_school_subscription_status  ON school_subscription(status);
CREATE INDEX idx_school_subscription_dates   ON school_subscription(start_date, end_date);

COMMENT ON TABLE  school_subscription IS 'A school''s active or historical subscription; plan_code/version denormalized for historical accuracy';
COMMENT ON COLUMN school_subscription.plan_code    IS 'Snapshot of plan_code at purchase time; accurate even after plan deprecation';
COMMENT ON COLUMN school_subscription.plan_version IS 'Snapshot of plan version at purchase time; prevents silent plan version drift';
COMMENT ON COLUMN school_subscription.final_amount IS 'base_amount - discount_amount + tax_amount; stored for billing immutability';
