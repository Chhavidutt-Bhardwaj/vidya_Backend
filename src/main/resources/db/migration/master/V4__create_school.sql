-- ============================================================
-- V4: school
-- Business entity representing a school on the platform
-- NOTE: tenant_id is nullable — populated after provisioning
-- ============================================================

CREATE TABLE school (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    school_code         VARCHAR(50)  NOT NULL,
    tenant_id           UUID,                              -- populated after provisioning
    school_name         VARCHAR(300) NOT NULL,
    legal_name          VARCHAR(300),
    registration_number VARCHAR(100),
    board               VARCHAR(30),
    school_type         VARCHAR(30),
    email               VARCHAR(255) NOT NULL,
    phone               VARCHAR(20),
    website             VARCHAR(500),
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_school PRIMARY KEY (id),
    CONSTRAINT uq_school_code UNIQUE (school_code),
    CONSTRAINT chk_school_board CHECK (board IN (
        'CBSE', 'ICSE', 'STATE', 'IB', 'IGCSE', 'OTHER', NULL
    )),
    CONSTRAINT chk_school_type CHECK (school_type IN (
        'PRIMARY', 'SECONDARY', 'SENIOR_SECONDARY', 'ALL', 'OTHER', NULL
    )),
    CONSTRAINT chk_school_status CHECK (status IN (
        'DRAFT', 'PENDING', 'VERIFIED', 'ACTIVE',
        'SUSPENDED', 'DEACTIVATED', 'ARCHIVED'
    ))
);

CREATE INDEX idx_school_status    ON school(status);
CREATE INDEX idx_school_tenant    ON school(tenant_id);
CREATE INDEX idx_school_email     ON school(email);

COMMENT ON TABLE  school IS 'Business entity for a school on the Vidya SaaS platform';
COMMENT ON COLUMN school.school_code IS 'Human-readable platform code, e.g. SCH-A1B2C3D4. NOT the database name.';
COMMENT ON COLUMN school.tenant_id   IS 'FK to tenant.id; nullable until provisioning completes';
COMMENT ON COLUMN school.status      IS 'DRAFT=onboarding in progress, ACTIVE=fully operational, SUSPENDED=payment lapsed';
