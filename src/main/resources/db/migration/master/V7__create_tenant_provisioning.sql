-- ============================================================
-- V7: tenant_provisioning
-- Per-step state machine record for tenant database provisioning
--
-- Design intent:
--   - Each provisioning attempt gets ONE record
--   - If it fails, the record is updated with failure info
--   - Retry increments attempt_count and resets to last_completed_step
--   - Completed steps are NEVER re-executed (idempotency)
-- ============================================================

CREATE TABLE tenant_provisioning (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    provisioning_code    VARCHAR(50)  NOT NULL,
    status               VARCHAR(50)  NOT NULL DEFAULT 'REQUESTED',
    current_step         VARCHAR(50),
    last_completed_step  VARCHAR(50),
    failure_reason       TEXT,
    attempt_count        INTEGER      NOT NULL DEFAULT 1,
    started_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_tenant_provisioning PRIMARY KEY (id),
    CONSTRAINT uq_tenant_provisioning_code UNIQUE (provisioning_code),
    CONSTRAINT fk_tenant_provisioning_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT chk_tenant_provisioning_status CHECK (status IN (
        'REQUESTED',
        'DATABASE_CREATING',
        'DATABASE_CREATED',
        'MIGRATION_RUNNING',
        'MIGRATION_COMPLETED',
        'INITIALIZATION_RUNNING',
        'INITIALIZATION_COMPLETED',
        'COMPLETED',
        'FAILED'
    ))
);

CREATE INDEX idx_tenant_provisioning_tenant ON tenant_provisioning(tenant_id);
CREATE INDEX idx_tenant_provisioning_status ON tenant_provisioning(status);

COMMENT ON TABLE  tenant_provisioning IS 'State machine record for tenant database provisioning; resumable and retryable';
COMMENT ON COLUMN tenant_provisioning.last_completed_step IS 'The last step that completed successfully; retry resumes from next step';
COMMENT ON COLUMN tenant_provisioning.attempt_count      IS 'Incremented on each manual retry; first attempt = 1';
