-- ============================================================
-- V10: onboarding_activity
-- Immutable audit trail for every onboarding state change and action
-- This table NEVER has rows updated or deleted — append-only
-- ============================================================

CREATE TABLE onboarding_activity (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    onboarding_id       UUID         NOT NULL,
    action              VARCHAR(100) NOT NULL,
    previous_status     VARCHAR(30),
    new_status          VARCHAR(30),
    performed_by        UUID,
    performed_by_type   VARCHAR(30)  NOT NULL DEFAULT 'SYSTEM',
    metadata            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_onboarding_activity PRIMARY KEY (id),
    CONSTRAINT fk_onboarding_activity_onboarding
        FOREIGN KEY (onboarding_id) REFERENCES school_onboarding(id),
    CONSTRAINT chk_onboarding_activity_performer_type CHECK (performed_by_type IN (
        'PLATFORM_USER', 'COMMUNITY_MEMBER', 'SYSTEM'
    ))
);

CREATE INDEX idx_onboarding_activity_onboarding  ON onboarding_activity(onboarding_id);
CREATE INDEX idx_onboarding_activity_action      ON onboarding_activity(action);
CREATE INDEX idx_onboarding_activity_created     ON onboarding_activity(created_at);

COMMENT ON TABLE  onboarding_activity IS 'Append-only audit trail for all onboarding events; never updated or deleted';
COMMENT ON COLUMN onboarding_activity.action           IS 'e.g. STEP_COMPLETED, STATUS_CHANGED, DOCUMENT_UPLOADED, PLAN_SELECTED';
COMMENT ON COLUMN onboarding_activity.performed_by_type IS 'Who made the action: PLATFORM_USER, COMMUNITY_MEMBER, or SYSTEM (automated)';
COMMENT ON COLUMN onboarding_activity.metadata         IS 'Additional context, e.g. {stepName: "DOCUMENTS"} or {planCode: "STANDARD"}';
