-- ============================================================
-- V15: payment_transaction
-- Immutable record of every payment initiated on the platform
--
-- Key design:
--   - idempotency_key unique index prevents duplicate processing
--   - provider_transaction_id unique (partial) index prevents
--     duplicate callback processing
--   - raw_callback stores provider response verbatim for audit/dispute
--   - NEVER delete or update completed payment records
-- ============================================================

CREATE TABLE payment_transaction (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    transaction_code        VARCHAR(50)    NOT NULL,
    school_id               UUID           NOT NULL,
    subscription_id         UUID,
    onboarding_id           UUID,
    amount                  NUMERIC(12, 2) NOT NULL,
    currency                VARCHAR(10)    NOT NULL DEFAULT 'INR',
    status                  VARCHAR(30)    NOT NULL DEFAULT 'INITIATED',
    payment_provider        VARCHAR(30)    NOT NULL,
    provider_transaction_id VARCHAR(255),
    provider_order_id       VARCHAR(255),
    idempotency_key         VARCHAR(255)   NOT NULL,
    payment_method          VARCHAR(30),
    failure_reason          TEXT,
    initiated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    raw_callback            JSONB,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version                 BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_payment_transaction PRIMARY KEY (id),
    CONSTRAINT uq_payment_transaction_code     UNIQUE (transaction_code),
    CONSTRAINT uq_payment_idempotency_key      UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_transaction_school
        FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_payment_transaction_subscription
        FOREIGN KEY (subscription_id) REFERENCES school_subscription(id),
    CONSTRAINT fk_payment_transaction_onboarding
        FOREIGN KEY (onboarding_id) REFERENCES school_onboarding(id),
    CONSTRAINT chk_payment_status CHECK (status IN (
        'INITIATED', 'PENDING', 'SUCCESS', 'FAILED', 'REFUNDED', 'CANCELLED'
    )),
    CONSTRAINT chk_payment_provider CHECK (payment_provider IN (
        'CASHFREE', 'PAYU', 'RAZORPAY', 'MANUAL', 'OTHER'
    )),
    CONSTRAINT chk_payment_method CHECK (payment_method IN (
        'UPI', 'CARD', 'NETBANKING', 'WALLET', 'NEFT', 'RTGS', 'CHEQUE', 'OTHER', NULL
    )),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
);

-- Partial unique index: only enforce uniqueness when provider_transaction_id is set
-- This allows multiple INITIATED rows before provider assigns an ID
CREATE UNIQUE INDEX uq_payment_provider_txn
    ON payment_transaction(provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

CREATE INDEX idx_payment_school       ON payment_transaction(school_id);
CREATE INDEX idx_payment_status       ON payment_transaction(status);
CREATE INDEX idx_payment_provider     ON payment_transaction(payment_provider);
CREATE INDEX idx_payment_onboarding   ON payment_transaction(onboarding_id);
CREATE INDEX idx_payment_subscription ON payment_transaction(subscription_id);
CREATE INDEX idx_payment_initiated_at ON payment_transaction(initiated_at);

COMMENT ON TABLE  payment_transaction IS 'Immutable payment record; NEVER delete or update completed rows for audit integrity';
COMMENT ON COLUMN payment_transaction.idempotency_key         IS 'Client-generated key preventing duplicate payment processing on callback retries';
COMMENT ON COLUMN payment_transaction.provider_transaction_id IS 'ID assigned by payment gateway; set after payment initiation';
COMMENT ON COLUMN payment_transaction.raw_callback            IS 'Verbatim provider callback payload stored for audit/dispute resolution';
