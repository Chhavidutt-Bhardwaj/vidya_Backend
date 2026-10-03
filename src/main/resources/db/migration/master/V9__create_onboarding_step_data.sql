-- ============================================================
-- V9: onboarding_step_data
-- Stores the structured data submitted for each onboarding step
-- Uses JSONB for flexible step-specific payloads
-- ============================================================

CREATE TABLE onboarding_step_data (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    onboarding_id   UUID        NOT NULL,
    step_name       VARCHAR(50) NOT NULL,
    step_data       JSONB       NOT NULL DEFAULT '{}',
    is_completed    BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_onboarding_step_data PRIMARY KEY (id),
    CONSTRAINT uq_onboarding_step UNIQUE (onboarding_id, step_name),
    CONSTRAINT fk_onboarding_step_onboarding
        FOREIGN KEY (onboarding_id) REFERENCES school_onboarding(id)
            ON DELETE CASCADE,
    CONSTRAINT chk_onboarding_step_name CHECK (step_name IN (
        'BASIC_INFORMATION', 'CONTACT_INFORMATION', 'ADDRESS',
        'BOARD_CONFIGURATION', 'SCHOOL_DETAILS', 'DOCUMENTS',
        'PLAN_SELECTION', 'PAYMENT', 'VERIFICATION', 'APPROVAL',
        'TENANT_PROVISIONING', 'ADMIN_CREATION', 'ACTIVATION'
    ))
);

CREATE INDEX idx_onboarding_step_onboarding ON onboarding_step_data(onboarding_id);
CREATE INDEX idx_onboarding_step_name       ON onboarding_step_data(step_name);
CREATE INDEX idx_onboarding_step_completed  ON onboarding_step_data(is_completed);

-- GIN index on JSONB for future querying within step data
CREATE INDEX idx_onboarding_step_data_gin ON onboarding_step_data USING GIN (step_data);

COMMENT ON TABLE  onboarding_step_data IS 'One row per onboarding step per onboarding process; stores step-specific JSONB payload';
COMMENT ON COLUMN onboarding_step_data.step_data IS 'Flexible JSONB payload for each step; schema depends on step_name';
