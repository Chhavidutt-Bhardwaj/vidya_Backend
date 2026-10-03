-- ============================================================
-- V19: Workflow orchestration enhancements
-- Adds school_id & school admin roles to platform_user,
-- and adds PROVISIONING_FAILED status to school_onboarding.
-- ============================================================

-- 1. Add school_id to platform_user
ALTER TABLE platform_user ADD COLUMN IF NOT EXISTS school_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_platform_user_school'
    ) THEN
        ALTER TABLE platform_user
            ADD CONSTRAINT fk_platform_user_school
            FOREIGN KEY (school_id) REFERENCES school(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_platform_user_school ON platform_user(school_id);

-- 2. Update platform_user role check constraint to include SCHOOL_OWNER and SCHOOL_ADMIN
ALTER TABLE platform_user DROP CONSTRAINT IF EXISTS chk_platform_user_role;
ALTER TABLE platform_user ADD CONSTRAINT chk_platform_user_role CHECK (platform_role IN (
    'PLATFORM_SUPER_ADMIN', 'PLATFORM_ADMIN',
    'COMMUNITY_ADMIN', 'COMMUNITY_MANAGER', 'FIELD_AGENT',
    'SCHOOL_OWNER', 'SCHOOL_ADMIN'
));

-- 3. Update school_onboarding status check constraint to include PROVISIONING_FAILED
ALTER TABLE school_onboarding DROP CONSTRAINT IF EXISTS chk_school_onboarding_status;
ALTER TABLE school_onboarding ADD CONSTRAINT chk_school_onboarding_status CHECK (status IN (
    'DRAFT', 'SUBMITTED', 'VERIFICATION_PENDING', 'VERIFIED',
    'PLAN_SELECTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED',
    'APPROVAL_PENDING', 'APPROVED', 'PROVISIONING', 'PROVISIONED',
    'ACTIVE', 'REJECTED', 'CANCELLED', 'FAILED', 'PROVISIONING_FAILED'
));
