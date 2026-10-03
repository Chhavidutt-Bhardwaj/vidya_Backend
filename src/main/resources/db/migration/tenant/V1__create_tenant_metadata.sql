-- ============================================================
-- TENANT V1: tenant_metadata
-- Bootstraps the tenant database with identification metadata
-- This is the FIRST table created in every tenant DB
-- ============================================================

CREATE TABLE tenant_metadata (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_code             VARCHAR(50)  NOT NULL,
    schema_initialized_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    app_version             VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_tenant_metadata PRIMARY KEY (id),
    CONSTRAINT uq_tenant_metadata_code UNIQUE (tenant_code)
);

COMMENT ON TABLE  tenant_metadata IS 'Bootstrap metadata for this tenant database; identifies which platform tenant owns this DB';
COMMENT ON COLUMN tenant_metadata.tenant_code IS 'Must match tenant.tenant_code in the master DB';
COMMENT ON COLUMN tenant_metadata.app_version IS 'Application version that ran this migration; useful for support and upgrade tracking';
