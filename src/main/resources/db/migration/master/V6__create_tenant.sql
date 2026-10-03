-- ============================================================
-- V6: tenant
-- Technical isolation boundary for a school's operational data
--
-- Key design decisions:
--   - school_id is UNIQUE: one school → one tenant (Phase 01)
--   - database_name is the actual PG database name
--   - database_username_ref/password_ref are VAULT/ENV references,
--     NEVER plain-text credentials
--   - circular: school.tenant_id → tenant.id (set after provisioning)
-- ============================================================

CREATE TABLE tenant (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_code             VARCHAR(50)  NOT NULL,
    school_id               UUID         NOT NULL,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'CREATED',
    database_identifier     VARCHAR(100) NOT NULL,
    database_host           VARCHAR(255) NOT NULL,
    database_port           INTEGER      NOT NULL DEFAULT 5432,
    database_name           VARCHAR(100) NOT NULL,
    database_username_ref   VARCHAR(500) NOT NULL,
    database_password_ref   VARCHAR(500) NOT NULL,
    schema_version          VARCHAR(50),
    provisioning_status     VARCHAR(50),
    provisioned_at          TIMESTAMPTZ,
    suspended_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_code        UNIQUE (tenant_code),
    CONSTRAINT uq_tenant_school      UNIQUE (school_id),
    CONSTRAINT uq_tenant_db_name     UNIQUE (database_name),
    CONSTRAINT uq_tenant_db_id       UNIQUE (database_identifier),
    CONSTRAINT fk_tenant_school
        FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT chk_tenant_status CHECK (status IN (
        'CREATED', 'PROVISIONING', 'PROVISIONED', 'ACTIVE',
        'SUSPENDED', 'DEACTIVATED', 'ARCHIVED', 'PROVISIONING_FAILED'
    ))
);

CREATE INDEX idx_tenant_status      ON tenant(status);
CREATE INDEX idx_tenant_school      ON tenant(school_id);

-- Add FK from school to tenant AFTER tenant table exists
-- This completes the bidirectional relationship
ALTER TABLE school
    ADD CONSTRAINT fk_school_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);

COMMENT ON TABLE  tenant IS 'Technical isolation boundary for a school; each tenant gets its own PostgreSQL database';
COMMENT ON COLUMN tenant.tenant_code           IS 'Short code used in database naming: vidya_tenant_<tenant_code>';
COMMENT ON COLUMN tenant.database_identifier   IS 'Fully qualified identifier, e.g. vidya_tenant_abc12345';
COMMENT ON COLUMN tenant.database_username_ref IS 'Reference key to secret manager or environment variable — NEVER the actual username';
COMMENT ON COLUMN tenant.database_password_ref IS 'Reference key to secret manager or environment variable — NEVER the actual password';
COMMENT ON COLUMN tenant.schema_version        IS 'Last known Flyway migration version applied to this tenant DB';
