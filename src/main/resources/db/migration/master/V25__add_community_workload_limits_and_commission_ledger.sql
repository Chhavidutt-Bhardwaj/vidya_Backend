-- ============================================================
-- V25: Community Workload Limits, Onboarding Attribution & Dual Commission Ledger
-- Decouples Onboarding Community (Community A) from RM Community (Community B)
-- Configures Hub Manager capacity limits (e.g. max 10 active queries per community)
-- ============================================================

-- 1. Add capacity limits to community
ALTER TABLE community
    ADD COLUMN IF NOT EXISTS max_active_query_limit INT NOT NULL DEFAULT 10,
    ADD COLUMN IF NOT EXISTS max_assigned_schools_limit INT NOT NULL DEFAULT 25;

COMMENT ON COLUMN community.max_active_query_limit IS 'Workload threshold: maximum concurrent open queries handled by this community (configurable by Hub Manager)';
COMMENT ON COLUMN community.max_assigned_schools_limit IS 'Maximum number of schools that can be assigned to this community as Relationship Manager';

-- 2. Add permanent Onboarding Community attribution to school
ALTER TABLE school
    ADD COLUMN IF NOT EXISTS onboarding_community_id UUID,
    ADD COLUMN IF NOT EXISTS onboarded_at TIMESTAMPTZ DEFAULT NOW();

ALTER TABLE school
    ADD CONSTRAINT fk_school_onboarding_community
    FOREIGN KEY (onboarding_community_id) REFERENCES community(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_school_onboarding_community ON school(onboarding_community_id);

-- 3. Community Commission Ledger (Dual Commission Accounting)
-- Community A receives ONBOARDING commission
-- Community B receives QUERY_RESOLUTION and RELATIONSHIP_MANAGEMENT commission
CREATE TABLE IF NOT EXISTS community_commission_ledger (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID           NOT NULL,
    commission_type   VARCHAR(50)    NOT NULL,
    school_id         UUID,
    school_name       VARCHAR(300),
    support_query_id  UUID,
    ticket_number     VARCHAR(50),
    amount            NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    currency          VARCHAR(10)    NOT NULL DEFAULT 'INR',
    status            VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    notes             TEXT,
    earned_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_commission_ledger PRIMARY KEY (id),
    CONSTRAINT fk_comm_ledger_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT fk_comm_ledger_school
        FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE SET NULL,
    CONSTRAINT fk_comm_ledger_query
        FOREIGN KEY (support_query_id) REFERENCES school_support_query(id) ON DELETE SET NULL,
    CONSTRAINT chk_comm_ledger_type CHECK (commission_type IN (
        'ONBOARDING', 'QUERY_RESOLUTION', 'RELATIONSHIP_MANAGEMENT', 'REFERRAL', 'REVENUE_SHARE', 'MILESTONE'
    )),
    CONSTRAINT chk_comm_ledger_status CHECK (status IN (
        'PENDING', 'APPROVED', 'PAID', 'CANCELLED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_comm_ledger_community ON community_commission_ledger(community_id);
CREATE INDEX IF NOT EXISTS idx_comm_ledger_school ON community_commission_ledger(school_id);
CREATE INDEX IF NOT EXISTS idx_comm_ledger_query ON community_commission_ledger(support_query_id);
CREATE INDEX IF NOT EXISTS idx_comm_ledger_status ON community_commission_ledger(status);
CREATE INDEX IF NOT EXISTS idx_comm_ledger_type ON community_commission_ledger(commission_type);
