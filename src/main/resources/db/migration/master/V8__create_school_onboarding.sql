-- ============================================================
-- V8: school_onboarding
-- The primary onboarding record tracking a school's journey
-- from initial registration to full activation
-- ============================================================

CREATE TABLE school_onboarding (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    onboarding_code      VARCHAR(50)  NOT NULL,
    school_id            UUID,                              -- nullable until school record created in step 1
    source               VARCHAR(30)  NOT NULL DEFAULT 'DIRECT',
    community_id         UUID,
    community_member_id  UUID,
    current_step         VARCHAR(50)  NOT NULL DEFAULT 'BASIC_INFORMATION',
    completed_steps      TEXT[]       NOT NULL DEFAULT '{}',
    status               VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    rejection_reason     TEXT,
    started_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    submitted_at         TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_school_onboarding PRIMARY KEY (id),
    CONSTRAINT uq_school_onboarding_code  UNIQUE (onboarding_code),
    CONSTRAINT fk_school_onboarding_school
        FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_school_onboarding_community
        FOREIGN KEY (community_id) REFERENCES community(id),
    CONSTRAINT fk_school_onboarding_member
        FOREIGN KEY (community_member_id) REFERENCES community_member(id),
    CONSTRAINT chk_school_onboarding_source CHECK (source IN (
        'DIRECT', 'COMMUNITY', 'PARTNER', 'REFERRAL'
    )),
    CONSTRAINT chk_school_onboarding_step CHECK (current_step IN (
        'BASIC_INFORMATION', 'CONTACT_INFORMATION', 'ADDRESS',
        'BOARD_CONFIGURATION', 'SCHOOL_DETAILS', 'DOCUMENTS',
        'PLAN_SELECTION', 'PAYMENT', 'VERIFICATION', 'APPROVAL',
        'TENANT_PROVISIONING', 'ADMIN_CREATION', 'ACTIVATION'
    )),
    CONSTRAINT chk_school_onboarding_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'VERIFICATION_PENDING', 'VERIFIED',
        'PLAN_SELECTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED',
        'APPROVAL_PENDING', 'APPROVED', 'PROVISIONING', 'PROVISIONED',
        'ACTIVE', 'REJECTED', 'CANCELLED', 'FAILED'
    ))
);

CREATE INDEX idx_school_onboarding_school    ON school_onboarding(school_id);
CREATE INDEX idx_school_onboarding_status    ON school_onboarding(status);
CREATE INDEX idx_school_onboarding_community ON school_onboarding(community_id);
CREATE INDEX idx_school_onboarding_source    ON school_onboarding(source);

COMMENT ON TABLE  school_onboarding IS 'Multi-step school onboarding process; same table serves both DIRECT and COMMUNITY channels';
COMMENT ON COLUMN school_onboarding.completed_steps IS 'PostgreSQL array of completed step names; used to determine resume point';
COMMENT ON COLUMN school_onboarding.source          IS 'Acquisition channel; COMMUNITY populates community_id and member_id';
