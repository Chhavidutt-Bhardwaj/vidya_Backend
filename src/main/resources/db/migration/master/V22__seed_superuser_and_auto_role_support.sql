-- ============================================================
-- V22: Ensure SuperUser in master DB with full platform permissions
-- and guarantee community role permission and audit log schema
-- ============================================================

-- 1. Ensure community_role_audit_log exists
CREATE TABLE IF NOT EXISTS community_role_audit_log (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    community_id       UUID         NOT NULL,
    actor_user_id      UUID         NOT NULL,
    actor_name         VARCHAR(255),
    target_member_id   UUID,
    role_definition_id UUID,
    role_name_snapshot VARCHAR(100),
    action             VARCHAR(50)  NOT NULL,
    reason             TEXT,
    previous_role_name VARCHAR(100),
    actor_ip_address   VARCHAR(50),
    occurred_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_community_role_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_community_role_audit_log_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_role_audit_log_member
        FOREIGN KEY (target_member_id) REFERENCES community_member(id) ON DELETE SET NULL,
    CONSTRAINT fk_community_role_audit_log_role
        FOREIGN KEY (role_definition_id) REFERENCES community_role_definition(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_community_role_audit_log_community ON community_role_audit_log(community_id);
CREATE INDEX IF NOT EXISTS idx_community_role_audit_log_actor ON community_role_audit_log(actor_user_id);
CREATE INDEX IF NOT EXISTS idx_community_role_audit_log_time ON community_role_audit_log(occurred_at DESC);

-- 2. Ensure community_role_permission has universal integer matrix columns
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'community_role_permission' AND column_name = 'section_code'
    ) THEN
        ALTER TABLE community_role_permission ADD COLUMN IF NOT EXISTS section_code INT NOT NULL DEFAULT 1;
        ALTER TABLE community_role_permission ADD COLUMN IF NOT EXISTS action_code INT NOT NULL DEFAULT -9;
        ALTER TABLE community_role_permission ADD COLUMN IF NOT EXISTS granted BOOLEAN NOT NULL DEFAULT TRUE;
        ALTER TABLE community_role_permission DROP COLUMN IF EXISTS permission_id;
        ALTER TABLE community_role_permission DROP COLUMN IF EXISTS can_view;
        ALTER TABLE community_role_permission DROP COLUMN IF EXISTS can_create;
        ALTER TABLE community_role_permission DROP COLUMN IF EXISTS can_edit;
        ALTER TABLE community_role_permission DROP COLUMN IF EXISTS can_delete;
        ALTER TABLE community_role_permission DROP COLUMN IF EXISTS can_approve;
        ALTER TABLE community_role_permission DROP COLUMN IF EXISTS can_export;
    END IF;
END $$;

-- 3. SuperUser Seed
INSERT INTO platform_user (
    id,
    user_code,
    email,
    password_hash,
    first_name,
    last_name,
    phone,
    status,
    platform_role,
    school_id,
    failed_login_attempts,
    locked_until,
    created_at,
    updated_at,
    version
)
VALUES (
    'a0000000-0000-0000-0000-000000000000',
    'USR-SUPERADMIN01',
    'superuser@vidya.io',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOiiK7k5U7sT6wG.Wp5e9DfxbYm5lTpi.',
    'Super',
    'User',
    '+919999999999',
    'ACTIVE',
    'PLATFORM_SUPER_ADMIN',
    NULL,
    0,
    NULL,
    NOW(),
    NOW(),
    0
)
ON CONFLICT (email) DO UPDATE
SET status = 'ACTIVE',
    platform_role = 'PLATFORM_SUPER_ADMIN',
    failed_login_attempts = 0,
    locked_until = NULL,
    updated_at = NOW();

-- 4. Ensure default admin@vidya.io has PLATFORM_SUPER_ADMIN
UPDATE platform_user
SET platform_role = 'PLATFORM_SUPER_ADMIN',
    status = 'ACTIVE',
    failed_login_attempts = 0,
    locked_until = NULL,
    updated_at = NOW()
WHERE email = 'admin@vidya.io';
