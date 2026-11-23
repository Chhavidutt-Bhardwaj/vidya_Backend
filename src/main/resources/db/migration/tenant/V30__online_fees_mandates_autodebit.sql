-- ============================================================================================================
-- V30: Online fee collection through the school's own Cashfree / Razorpay merchant account.
--   * fee_gateway_account     the school's API keys (secrets encrypted by the application, never returned)
--   * fee_autodebit_settings  one row: debit day (default the 10th), notices, sibling gap, retries, NACH bounce + GST
--   * fee_mandate(+_student)  eNACH / UPI Autopay mandates of a payer, covering one or more of their children
--   * fee_debit(+_run)        one auto-debit per mandate, child, month and attempt; higher class first, later classes after a gap
--   * fee_checkout(+_student) payment links and checkout orders for one or more children (part payments: higher class first)
--   * fee_gateway_payment     every gateway payment applied once (idempotency); fee_gateway_event the raw webhooks
-- A debit sent to the bank is held on the lines as pending (like a cheque) until the gateway reports success or failure.
-- See tenantdata/fee/online/ONLINE_FEES.md.
-- ============================================================================================================

ALTER TABLE fee_payment DROP CONSTRAINT chk_fee_payment_mode;
ALTER TABLE fee_payment ADD CONSTRAINT chk_fee_payment_mode
    CHECK (mode IN ('CASH', 'UPI', 'CARD', 'CHEQUE', 'BANK_TRANSFER', 'DD', 'NACH', 'ONLINE'));

CREATE TABLE fee_gateway_account (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    provider            VARCHAR(20)   NOT NULL,
    environment         VARCHAR(20)   NOT NULL DEFAULT 'SANDBOX',
    key_id              VARCHAR(200)  NOT NULL,
    secret_enc          TEXT          NOT NULL,
    webhook_secret_enc  TEXT          NULL,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    updated_by          UUID          NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_gateway_account PRIMARY KEY (id),
    CONSTRAINT uq_fee_gateway_account_provider UNIQUE (provider),
    CONSTRAINT chk_fee_gateway_account_provider CHECK (provider IN ('CASHFREE', 'RAZORPAY')),
    CONSTRAINT chk_fee_gateway_account_env CHECK (environment IN ('SANDBOX', 'PRODUCTION'))
);
COMMENT ON COLUMN fee_gateway_account.secret_enc IS 'AES-GCM, key from vidya.fees.online.secret-key; Cashfree client secret / Razorpay key secret';
COMMENT ON COLUMN fee_gateway_account.webhook_secret_enc IS 'Razorpay webhook secret (Cashfree signs webhooks with the client secret)';

CREATE TABLE fee_autodebit_settings (
    id                        SMALLINT      NOT NULL DEFAULT 1,
    enabled                   BOOLEAN       NOT NULL DEFAULT FALSE,
    mandate_provider          VARCHAR(20)   NULL,
    payment_provider          VARCHAR(20)   NULL,
    debit_day                 SMALLINT      NOT NULL DEFAULT 10,
    notice_days               VARCHAR(50)   NOT NULL DEFAULT '3,1',
    invite_days_before        SMALLINT      NOT NULL DEFAULT 7,
    mandate_setup_url         VARCHAR(500)  NULL,
    sibling_gap_days          SMALLINT      NOT NULL DEFAULT 1,
    max_attempts              SMALLINT      NOT NULL DEFAULT 2,
    retry_after_days          SMALLINT      NOT NULL DEFAULT 3,
    bounce_charge_paise       BIGINT        NOT NULL DEFAULT 50000,
    bounce_gst_percent        NUMERIC(5,2)  NOT NULL DEFAULT 18.00,
    mandate_max_amount_paise  BIGINT        NOT NULL DEFAULT 10000000,
    mandate_validity_years    SMALLINT      NOT NULL DEFAULT 10,
    link_expiry_days          SMALLINT      NOT NULL DEFAULT 7,
    return_url                VARCHAR(500)  NULL,
    updated_by                UUID          NULL,
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                   BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_autodebit_settings PRIMARY KEY (id),
    CONSTRAINT chk_fee_autodebit_singleton CHECK (id = 1),
    CONSTRAINT chk_fee_autodebit_mandate_provider CHECK (mandate_provider IN ('CASHFREE', 'RAZORPAY')),
    CONSTRAINT chk_fee_autodebit_payment_provider CHECK (payment_provider IN ('CASHFREE', 'RAZORPAY')),
    CONSTRAINT chk_fee_autodebit_day CHECK (debit_day BETWEEN 1 AND 28),
    CONSTRAINT chk_fee_autodebit_invite CHECK (invite_days_before BETWEEN 0 AND 27),
    CONSTRAINT chk_fee_autodebit_gap CHECK (sibling_gap_days BETWEEN 0 AND 7),
    CONSTRAINT chk_fee_autodebit_attempts CHECK (max_attempts BETWEEN 1 AND 4),
    CONSTRAINT chk_fee_autodebit_retry CHECK (retry_after_days BETWEEN 1 AND 15),
    CONSTRAINT chk_fee_autodebit_bounce CHECK (bounce_charge_paise >= 0),
    CONSTRAINT chk_fee_autodebit_gst CHECK (bounce_gst_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_fee_autodebit_max CHECK (mandate_max_amount_paise > 0),
    CONSTRAINT chk_fee_autodebit_validity CHECK (mandate_validity_years BETWEEN 1 AND 30),
    CONSTRAINT chk_fee_autodebit_link_expiry CHECK (link_expiry_days BETWEEN 1 AND 60)
);
COMMENT ON COLUMN fee_autodebit_settings.notice_days IS 'Pre-debit notices: days before each debit, comma separated (RBI: at least 24 hours before)';
COMMENT ON COLUMN fee_autodebit_settings.sibling_gap_days IS 'Children of one mandate are debited higher class first; each next child this many days later';

CREATE TABLE fee_mandate (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    reference            VARCHAR(60)   NOT NULL,
    provider             VARCHAR(20)   NOT NULL,
    method               VARCHAR(10)   NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    payer_name           VARCHAR(200)  NOT NULL,
    payer_email          VARCHAR(255)  NULL,
    payer_phone          VARCHAR(20)   NOT NULL,
    guardian_user_id     UUID          NULL REFERENCES tenant_user(id) ON DELETE SET NULL,
    max_amount_paise     BIGINT        NOT NULL,
    expires_on           DATE          NOT NULL,
    gateway_mandate_id   VARCHAR(100)  NULL,
    gateway_customer_id  VARCHAR(100)  NULL,
    gateway_auth_id      VARCHAR(500)  NULL,
    auth_url             VARCHAR(1000) NULL,
    activated_at         TIMESTAMPTZ   NULL,
    ended_at             TIMESTAMPTZ   NULL,
    ended_by             UUID          NULL,
    end_reason           VARCHAR(500)  NULL,
    last_synced_at       TIMESTAMPTZ   NULL,
    created_by           UUID          NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version              BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_mandate PRIMARY KEY (id),
    CONSTRAINT uq_fee_mandate_reference UNIQUE (reference),
    CONSTRAINT chk_fee_mandate_provider CHECK (provider IN ('CASHFREE', 'RAZORPAY')),
    CONSTRAINT chk_fee_mandate_method CHECK (method IN ('ENACH', 'UPI')),
    CONSTRAINT chk_fee_mandate_status CHECK (status IN ('PENDING', 'ACTIVE', 'PAUSED', 'CANCELLED', 'FAILED', 'EXPIRED'))
);
CREATE INDEX idx_fee_mandate_status ON fee_mandate(status);
CREATE INDEX idx_fee_mandate_gateway ON fee_mandate(provider, gateway_mandate_id);
CREATE INDEX idx_fee_mandate_customer ON fee_mandate(provider, gateway_customer_id);
CREATE INDEX idx_fee_mandate_auth ON fee_mandate(provider, gateway_auth_id);

CREATE TABLE fee_mandate_student (
    mandate_id   UUID     NOT NULL REFERENCES fee_mandate(id) ON DELETE CASCADE,
    student_id   UUID     NOT NULL REFERENCES student(id),
    guardian_id  UUID     NULL,
    live         BOOLEAN  NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_fee_mandate_student PRIMARY KEY (mandate_id, student_id)
);
-- A child is debited through at most one pending / active mandate
CREATE UNIQUE INDEX uq_fee_mandate_student_live ON fee_mandate_student(student_id) WHERE live;

CREATE TABLE fee_debit (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    reference           VARCHAR(60)   NOT NULL,
    mandate_id          UUID          NOT NULL REFERENCES fee_mandate(id),
    student_id          UUID          NOT NULL REFERENCES student(id),
    ledger_id           UUID          NOT NULL REFERENCES fee_ledger(id),
    cycle_month         DATE          NOT NULL,
    attempt_no          SMALLINT      NOT NULL DEFAULT 1,
    sibling_rank        SMALLINT      NOT NULL DEFAULT 0,
    scheduled_on        DATE          NOT NULL,
    amount_paise        BIGINT        NOT NULL DEFAULT 0,
    status              VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',
    gateway_payment_id  VARCHAR(100)  NULL,
    gateway_order_id    VARCHAR(100)  NULL,
    fee_payment_id      UUID          NULL REFERENCES fee_payment(id),
    bounce_charge_paise BIGINT        NOT NULL DEFAULT 0,
    bounce_gst_paise    BIGINT        NOT NULL DEFAULT 0,
    failure_reason      VARCHAR(500)  NULL,
    submitted_at        TIMESTAMPTZ   NULL,
    settled_at          TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_debit PRIMARY KEY (id),
    CONSTRAINT uq_fee_debit_reference UNIQUE (reference),
    CONSTRAINT uq_fee_debit_attempt UNIQUE (mandate_id, student_id, cycle_month, attempt_no),
    CONSTRAINT chk_fee_debit_status CHECK (status IN ('SCHEDULED', 'SUBMITTING', 'SUBMITTED', 'SUCCESS', 'FAILED', 'SKIPPED',
                                                      'CANCELLED', 'ERROR')),
    CONSTRAINT chk_fee_debit_amount CHECK (amount_paise >= 0)
);
CREATE INDEX idx_fee_debit_due ON fee_debit(status, scheduled_on);
CREATE INDEX idx_fee_debit_cycle ON fee_debit(cycle_month);
CREATE INDEX idx_fee_debit_gateway ON fee_debit(gateway_payment_id);
CREATE INDEX idx_fee_debit_order ON fee_debit(gateway_order_id);
CREATE INDEX idx_fee_debit_student ON fee_debit(student_id);

CREATE TABLE fee_debit_run (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    run_date      DATE          NOT NULL,
    run_trigger   VARCHAR(20)   NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'RUNNING',
    planned       INT           NOT NULL DEFAULT 0,
    invites       INT           NOT NULL DEFAULT 0,
    notices       INT           NOT NULL DEFAULT 0,
    submitted     INT           NOT NULL DEFAULT 0,
    skipped       INT           NOT NULL DEFAULT 0,
    errors        INT           NOT NULL DEFAULT 0,
    detail        VARCHAR(1000) NULL,
    triggered_by  UUID          NULL,
    started_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    finished_at   TIMESTAMPTZ   NULL,

    CONSTRAINT pk_fee_debit_run PRIMARY KEY (id),
    CONSTRAINT chk_fee_debit_run_trigger CHECK (run_trigger IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT chk_fee_debit_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);
CREATE INDEX idx_fee_debit_run_date ON fee_debit_run(run_date DESC);

CREATE TABLE fee_checkout (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    reference          VARCHAR(60)   NOT NULL,
    kind               VARCHAR(10)   NOT NULL,
    provider           VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    amount_paise       BIGINT        NOT NULL,
    paid_paise         BIGINT        NOT NULL DEFAULT 0,
    unallocated_paise  BIGINT        NOT NULL DEFAULT 0,
    allow_partial      BOOLEAN       NOT NULL DEFAULT FALSE,
    payer_name         VARCHAR(200)  NOT NULL,
    payer_email        VARCHAR(255)  NULL,
    payer_phone        VARCHAR(20)   NOT NULL,
    purpose            VARCHAR(200)  NOT NULL,
    gateway_id         VARCHAR(120)  NULL,
    url                VARCHAR(1000) NULL,
    session_id         VARCHAR(1000) NULL,
    expires_at         TIMESTAMPTZ   NULL,
    created_by         UUID          NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_checkout PRIMARY KEY (id),
    CONSTRAINT uq_fee_checkout_reference UNIQUE (reference),
    CONSTRAINT chk_fee_checkout_kind CHECK (kind IN ('LINK', 'ORDER')),
    CONSTRAINT chk_fee_checkout_provider CHECK (provider IN ('CASHFREE', 'RAZORPAY')),
    CONSTRAINT chk_fee_checkout_status CHECK (status IN ('CREATED', 'PARTIALLY_PAID', 'PAID', 'EXPIRED', 'CANCELLED', 'FAILED')),
    CONSTRAINT chk_fee_checkout_amount CHECK (amount_paise > 0 AND paid_paise >= 0 AND unallocated_paise >= 0)
);
CREATE INDEX idx_fee_checkout_gateway ON fee_checkout(provider, gateway_id);

CREATE TABLE fee_checkout_student (
    checkout_id   UUID      NOT NULL REFERENCES fee_checkout(id) ON DELETE CASCADE,
    student_id    UUID      NOT NULL REFERENCES student(id),
    ledger_id     UUID      NOT NULL REFERENCES fee_ledger(id),
    sibling_rank  SMALLINT  NOT NULL,
    due_paise     BIGINT    NOT NULL,
    paid_paise    BIGINT    NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_checkout_student PRIMARY KEY (checkout_id, student_id)
);
CREATE INDEX idx_fee_checkout_student ON fee_checkout_student(student_id);

CREATE TABLE fee_gateway_payment (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    provider            VARCHAR(20)   NOT NULL,
    gateway_payment_id  VARCHAR(150)  NOT NULL,
    checkout_id         UUID          NULL REFERENCES fee_checkout(id),
    debit_id            UUID          NULL REFERENCES fee_debit(id),
    amount_paise        BIGINT        NOT NULL,
    allocated_paise     BIGINT        NOT NULL DEFAULT 0,
    method              VARCHAR(40)   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_fee_gateway_payment PRIMARY KEY (id),
    CONSTRAINT uq_fee_gateway_payment UNIQUE (provider, gateway_payment_id)
);

CREATE TABLE fee_gateway_event (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    provider     VARCHAR(20)   NOT NULL,
    event_id     VARCHAR(150)  NULL,
    event_type   VARCHAR(80)   NULL,
    payload      TEXT          NOT NULL,
    outcome      VARCHAR(30)   NOT NULL,
    detail       VARCHAR(1000) NULL,
    received_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_fee_gateway_event PRIMARY KEY (id)
);
CREATE INDEX idx_fee_gateway_event_received ON fee_gateway_event(received_at DESC);
