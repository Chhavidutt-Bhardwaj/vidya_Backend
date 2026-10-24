-- ============================================================
-- V24: Hub Hierarchy, School Relationship Management (RM) & 3-Tier Support Escalation
-- Chain: Platform Users -> Hub Manager / Sub-Manager -> Community (School RM) -> Schools
-- ============================================================

-- 1. Create Hub Table (Regional / State Hubs)
CREATE TABLE IF NOT EXISTS hub (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    hub_code               VARCHAR(50)  NOT NULL,
    name                   VARCHAR(200) NOT NULL,
    state                  VARCHAR(100) NOT NULL,
    region                 VARCHAR(100),
    description            TEXT,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    lead_community_id      UUID,
    hub_manager_user_id    UUID,
    hub_manager_name       VARCHAR(200),
    hub_sub_manager_user_id UUID,
    hub_sub_manager_name   VARCHAR(200),
    contact_email          VARCHAR(255),
    contact_phone          VARCHAR(20),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    version                BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_hub PRIMARY KEY (id),
    CONSTRAINT uq_hub_code UNIQUE (hub_code),
    CONSTRAINT chk_hub_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE INDEX IF NOT EXISTS idx_hub_state ON hub(state);
CREATE INDEX IF NOT EXISTS idx_hub_status ON hub(status);
CREATE INDEX IF NOT EXISTS idx_hub_manager ON hub(hub_manager_user_id);
CREATE INDEX IF NOT EXISTS idx_hub_sub_manager ON hub(hub_sub_manager_user_id);

COMMENT ON TABLE hub IS 'Regional / State operational hubs grouping communities under a lead community, hub manager, and sub-manager';

-- 2. Add hub references and lead flag to community
ALTER TABLE community
    ADD COLUMN IF NOT EXISTS hub_id UUID,
    ADD COLUMN IF NOT EXISTS is_hub_lead BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE community
    ADD CONSTRAINT fk_community_hub
    FOREIGN KEY (hub_id) REFERENCES hub(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_community_hub ON community(hub_id);

-- Foreign key for hub.lead_community_id
ALTER TABLE hub
    ADD CONSTRAINT fk_hub_lead_community
    FOREIGN KEY (lead_community_id) REFERENCES community(id) ON DELETE SET NULL;

-- 3. Add Relationship Management & Hub references to school
ALTER TABLE school
    ADD COLUMN IF NOT EXISTS assigned_community_id UUID,
    ADD COLUMN IF NOT EXISTS assigned_rm_member_id UUID,
    ADD COLUMN IF NOT EXISTS rm_member_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS hub_id UUID,
    ADD COLUMN IF NOT EXISTS rm_assignment_type VARCHAR(30) DEFAULT 'NEAREST_GEO_ALLOCATION',
    ADD COLUMN IF NOT EXISTS rm_assigned_at TIMESTAMPTZ DEFAULT NOW();

ALTER TABLE school
    ADD CONSTRAINT fk_school_assigned_community
    FOREIGN KEY (assigned_community_id) REFERENCES community(id) ON DELETE SET NULL;

ALTER TABLE school
    ADD CONSTRAINT fk_school_hub
    FOREIGN KEY (hub_id) REFERENCES hub(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_school_assigned_community ON school(assigned_community_id);
CREATE INDEX IF NOT EXISTS idx_school_hub ON school(hub_id);
CREATE INDEX IF NOT EXISTS idx_school_rm_member ON school(assigned_rm_member_id);

-- 4. School Support & Problem Query Table (Tickets)
CREATE TABLE IF NOT EXISTS school_support_query (
    id                         UUID         NOT NULL DEFAULT gen_random_uuid(),
    ticket_number              VARCHAR(50)  NOT NULL,
    school_id                  UUID         NOT NULL,
    assigned_community_id      UUID,
    hub_id                     UUID,
    category                   VARCHAR(50)  NOT NULL,
    priority                   VARCHAR(30)  NOT NULL DEFAULT 'MEDIUM',
    status                     VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    current_level              VARCHAR(30)  NOT NULL DEFAULT 'COMMUNITY_RM',
    subject                    VARCHAR(300) NOT NULL,
    description                TEXT         NOT NULL,
    raised_by_user_id          UUID,
    raised_by_name             VARCHAR(200),
    raised_by_role             VARCHAR(50),
    assigned_rm_member_id      UUID,
    escalated_to_hub_manager_id UUID,
    escalated_to_platform_user_id UUID,
    resolution_summary         TEXT,
    resolved_at                TIMESTAMPTZ,
    closed_at                  TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                 UUID,
    updated_by                 UUID,
    version                    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_school_support_query PRIMARY KEY (id),
    CONSTRAINT uq_school_support_query_ticket UNIQUE (ticket_number),
    CONSTRAINT fk_school_support_query_school
        FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE CASCADE,
    CONSTRAINT fk_school_support_query_community
        FOREIGN KEY (assigned_community_id) REFERENCES community(id) ON DELETE SET NULL,
    CONSTRAINT fk_school_support_query_hub
        FOREIGN KEY (hub_id) REFERENCES hub(id) ON DELETE SET NULL,
    CONSTRAINT chk_school_query_category CHECK (category IN (
        'SOFTWARE_BUG', 'PAYMENT_GATEWAY', 'BILLING_FEES', 'ONBOARDING',
        'ACCOUNT_ACCESS', 'FEATURE_REQUEST', 'HARDWARE_IOT', 'OTHER'
    )),
    CONSTRAINT chk_school_query_priority CHECK (priority IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    CONSTRAINT chk_school_query_status CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'ESCALATED_TO_HUB', 'ESCALATED_TO_PLATFORM', 'RESOLVED', 'CLOSED', 'REJECTED'
    )),
    CONSTRAINT chk_school_query_level CHECK (current_level IN (
        'COMMUNITY_RM', 'HUB_MANAGER', 'PLATFORM_ADMIN'
    ))
);

CREATE INDEX IF NOT EXISTS idx_school_query_school ON school_support_query(school_id);
CREATE INDEX IF NOT EXISTS idx_school_query_community ON school_support_query(assigned_community_id);
CREATE INDEX IF NOT EXISTS idx_school_query_hub ON school_support_query(hub_id);
CREATE INDEX IF NOT EXISTS idx_school_query_status ON school_support_query(status);
CREATE INDEX IF NOT EXISTS idx_school_query_level ON school_support_query(current_level);

-- 5. Support Query Escalation Audit Log
CREATE TABLE IF NOT EXISTS school_query_escalation_log (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    query_id             UUID        NOT NULL,
    from_level           VARCHAR(30) NOT NULL,
    to_level             VARCHAR(30) NOT NULL,
    escalated_by_user_id UUID,
    escalated_by_name    VARCHAR(200),
    escalated_by_role    VARCHAR(50),
    escalated_to_user_id UUID,
    reason               TEXT        NOT NULL,
    notes                TEXT,
    occurred_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_school_query_escalation_log PRIMARY KEY (id),
    CONSTRAINT fk_school_query_escalation_query
        FOREIGN KEY (query_id) REFERENCES school_support_query(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_query_escalation_query ON school_query_escalation_log(query_id);
CREATE INDEX IF NOT EXISTS idx_query_escalation_time ON school_query_escalation_log(occurred_at DESC);

-- 6. Support Query Threaded Comments
CREATE TABLE IF NOT EXISTS school_query_comment (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    query_id         UUID        NOT NULL,
    author_user_id   UUID,
    author_name      VARCHAR(200) NOT NULL,
    author_role      VARCHAR(50)  NOT NULL,
    message          TEXT        NOT NULL,
    is_internal_note BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_school_query_comment PRIMARY KEY (id),
    CONSTRAINT fk_school_query_comment_query
        FOREIGN KEY (query_id) REFERENCES school_support_query(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_query_comment_query ON school_query_comment(query_id);

-- 7. Initial Seed: Delhi State Central Hub
INSERT INTO hub (
    id,
    hub_code,
    name,
    state,
    region,
    description,
    status,
    hub_manager_name,
    hub_sub_manager_name,
    contact_email,
    contact_phone
)
VALUES (
    'd0000000-0000-0000-0000-000000000001',
    'HUB-DEL-01',
    'Delhi State Central Hub',
    'Delhi',
    'North India',
    'Primary operational hub covering all Delhi NCT communities and partner schools',
    'ACTIVE',
    'Delhi Regional Lead Manager',
    'Delhi Regional Sub-Manager',
    'delhi.hub@vidya.io',
    '+91-11-23456789'
)
ON CONFLICT (hub_code) DO NOTHING;
