-- ============================================================
-- V16: audit_log
-- Platform-wide audit trail for sensitive entity mutations
-- Append-only — no updates or deletes
-- ============================================================

CREATE TABLE audit_log (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    entity_type       VARCHAR(100) NOT NULL,
    entity_id         UUID         NOT NULL,
    action            VARCHAR(30)  NOT NULL,
    old_value         JSONB,
    new_value         JSONB,
    performed_by      UUID,
    performed_by_type VARCHAR(30)  NOT NULL DEFAULT 'SYSTEM',
    correlation_id    VARCHAR(100),
    ip_address        VARCHAR(50),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT chk_audit_action CHECK (action IN (
        'CREATE', 'UPDATE', 'DELETE', 'STATUS_CHANGE', 'LOGIN', 'LOGOUT', 'ACCESS_DENIED'
    )),
    CONSTRAINT chk_audit_performer_type CHECK (performed_by_type IN (
        'PLATFORM_USER', 'COMMUNITY_MEMBER', 'SYSTEM', 'EXTERNAL'
    ))
);

CREATE INDEX idx_audit_log_entity      ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_performed   ON audit_log(performed_by);
CREATE INDEX idx_audit_log_created     ON audit_log(created_at);
CREATE INDEX idx_audit_log_correlation ON audit_log(correlation_id);

COMMENT ON TABLE  audit_log IS 'Platform-wide append-only audit trail; sensitive changes are captured here';
COMMENT ON COLUMN audit_log.old_value      IS 'JSON snapshot before change; NULL for CREATE actions';
COMMENT ON COLUMN audit_log.new_value      IS 'JSON snapshot after change; NULL for DELETE actions';
COMMENT ON COLUMN audit_log.correlation_id IS 'HTTP request correlationId linking this audit entry to a specific request';
