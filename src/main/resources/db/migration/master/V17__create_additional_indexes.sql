-- ============================================================
-- V17: Additional composite indexes and performance tuning
-- Added separately to keep individual table migrations clean
-- ============================================================

-- ── school ────────────────────────────────────────────────────────────────
-- Compound index for common query: active schools in a status
CREATE INDEX idx_school_status_created
    ON school(status, created_at DESC);

-- ── school_onboarding ─────────────────────────────────────────────────────
-- Community agent's pending onboardings query
CREATE INDEX idx_onboarding_member_status
    ON school_onboarding(community_member_id, status)
    WHERE community_member_id IS NOT NULL;

-- Platform admin's review queue
CREATE INDEX idx_onboarding_status_submitted
    ON school_onboarding(status, submitted_at DESC)
    WHERE status IN ('VERIFICATION_PENDING', 'APPROVAL_PENDING');

-- ── subscription_plan ─────────────────────────────────────────────────────
-- Currently active plans
CREATE INDEX idx_plan_active
    ON subscription_plan(plan_code, effective_from DESC)
    WHERE status = 'ACTIVE';

-- ── school_subscription ───────────────────────────────────────────────────
-- Schools with expiring subscriptions (renewal jobs)
CREATE INDEX idx_subscription_expiry
    ON school_subscription(end_date, status)
    WHERE status IN ('ACTIVE', 'TRIAL') AND end_date IS NOT NULL;

-- ── payment_transaction ───────────────────────────────────────────────────
-- Pending payments that need status reconciliation
CREATE INDEX idx_payment_pending
    ON payment_transaction(initiated_at, payment_provider)
    WHERE status IN ('INITIATED', 'PENDING');

-- ── tenant_provisioning ───────────────────────────────────────────────────
-- Failed provisioning jobs for retry queue
CREATE INDEX idx_provisioning_failed
    ON tenant_provisioning(created_at, attempt_count)
    WHERE status = 'FAILED';

COMMENT ON INDEX idx_school_status_created     IS 'Admin dashboard: list schools by status ordered by creation date';
COMMENT ON INDEX idx_onboarding_member_status  IS 'Field agent dashboard: agent-specific pending onboardings';
COMMENT ON INDEX idx_onboarding_status_submitted IS 'Platform admin review queue: pending verification and approval';
COMMENT ON INDEX idx_subscription_expiry       IS 'Renewal job: find subscriptions expiring soon';
COMMENT ON INDEX idx_payment_pending           IS 'Reconciliation job: find stuck payments needing status check';
COMMENT ON INDEX idx_provisioning_failed       IS 'Provisioning retry job: find failed provisioning attempts';
