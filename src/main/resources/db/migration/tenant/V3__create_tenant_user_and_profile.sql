-- ============================================================
-- TENANT V3: tenant_user and tenant_user_profile
-- Operational user foundation for the school tenant database
-- ============================================================

CREATE TABLE tenant_user (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_code           VARCHAR(50)  NOT NULL,
    master_user_id      UUID         NULL,
    email               VARCHAR(255) NOT NULL,
    phone               VARCHAR(20)  NULL,
    user_type           VARCHAR(50)  NOT NULL DEFAULT 'STAFF',
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_tenant_user PRIMARY KEY (id),
    CONSTRAINT uq_tenant_user_code UNIQUE (user_code),
    CONSTRAINT uq_tenant_user_email UNIQUE (email),
    CONSTRAINT chk_tenant_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'LOCKED', 'DELETED'))
);

CREATE INDEX idx_tenant_user_code       ON tenant_user(user_code);
CREATE INDEX idx_tenant_user_email      ON tenant_user(email);
CREATE INDEX idx_tenant_user_phone      ON tenant_user(phone);
CREATE INDEX idx_tenant_user_status     ON tenant_user(status);
CREATE INDEX idx_tenant_user_master_id  ON tenant_user(master_user_id);
CREATE INDEX idx_tenant_user_deleted_at ON tenant_user(deleted_at);

CREATE TABLE tenant_user_profile (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NULL,
    display_name            VARCHAR(200) NULL,
    gender                  VARCHAR(20)  NULL,
    date_of_birth           DATE         NULL,
    address_line1           VARCHAR(255) NULL,
    address_line2           VARCHAR(255) NULL,
    city                    VARCHAR(100) NULL,
    state                   VARCHAR(100) NULL,
    postal_code             VARCHAR(20)  NULL,
    country                 VARCHAR(100) NULL DEFAULT 'India',
    emergency_contact_name  VARCHAR(100) NULL,
    emergency_contact_phone VARCHAR(20)  NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                 BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_tenant_user_profile PRIMARY KEY (id),
    CONSTRAINT uq_tenant_user_profile_user_id UNIQUE (user_id),
    CONSTRAINT fk_tenant_user_profile_user FOREIGN KEY (user_id)
        REFERENCES tenant_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_tenant_user_profile_name ON tenant_user_profile(first_name, last_name);

COMMENT ON TABLE  tenant_user                 IS 'Operational identity in this tenant DB; maps to master_user_id if linked';
COMMENT ON COLUMN tenant_user.user_code       IS 'Unique human-readable identifier (e.g. USR-XYZ12345)';
COMMENT ON COLUMN tenant_user.master_user_id  IS 'UUID of the corresponding platform_user in master DB if federated';
COMMENT ON COLUMN tenant_user.deleted_at      IS 'Soft deletion timestamp; NULL for active records';
COMMENT ON TABLE  tenant_user_profile         IS 'General profile details for a tenant user';
