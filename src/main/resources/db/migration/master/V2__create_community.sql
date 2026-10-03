-- ============================================================
-- V2: community
-- Field agent communities / partner organizations
-- ============================================================

CREATE TABLE community (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    community_code  VARCHAR(50)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    type            VARCHAR(30)  NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(20),
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    city            VARCHAR(100),
    state           VARCHAR(100),
    pincode         VARCHAR(20),
    country         VARCHAR(100) NOT NULL DEFAULT 'India',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_community PRIMARY KEY (id),
    CONSTRAINT uq_community_code UNIQUE (community_code),
    CONSTRAINT chk_community_type CHECK (type IN (
        'PARTNER', 'FIELD_AGENCY', 'NGO', 'GOVT', 'OTHER'
    )),
    CONSTRAINT chk_community_status CHECK (status IN (
        'ACTIVE', 'INACTIVE', 'SUSPENDED'
    ))
);

CREATE INDEX idx_community_status ON community(status);
CREATE INDEX idx_community_type   ON community(type);

COMMENT ON TABLE community IS 'Organizations that assist with school onboarding (field agents, NGOs, partners)';
