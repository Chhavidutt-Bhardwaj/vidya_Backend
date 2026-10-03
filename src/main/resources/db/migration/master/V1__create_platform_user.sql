-- ============================================================
-- V1: platform_user
-- Platform-level users: admins, community agents, school admins
-- ============================================================

CREATE TABLE platform_user (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_code        VARCHAR(50) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    phone            VARCHAR(20),
    status           VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    platform_role    VARCHAR(50) NOT NULL,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_platform_user PRIMARY KEY (id),
    CONSTRAINT uq_platform_user_code  UNIQUE (user_code),
    CONSTRAINT uq_platform_user_email UNIQUE (email),
    CONSTRAINT chk_platform_user_status CHECK (status IN (
        'ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED'
    )),
    CONSTRAINT chk_platform_user_role CHECK (platform_role IN (
        'PLATFORM_SUPER_ADMIN', 'PLATFORM_ADMIN',
        'COMMUNITY_ADMIN', 'COMMUNITY_MANAGER', 'FIELD_AGENT'
    ))
);

CREATE INDEX idx_platform_user_status ON platform_user(status);
CREATE INDEX idx_platform_user_role   ON platform_user(platform_role);

COMMENT ON TABLE  platform_user IS 'Platform-level users who manage the SaaS platform, communities, and school onboarding';
COMMENT ON COLUMN platform_user.user_code     IS 'Human-readable unique code, e.g. USR-A1B2C3D4';
COMMENT ON COLUMN platform_user.platform_role IS 'Role scoped to the platform layer; school-scoped roles live in the tenant DB';
COMMENT ON COLUMN platform_user.password_hash IS 'BCrypt or Argon2 hash; NEVER plain text';
