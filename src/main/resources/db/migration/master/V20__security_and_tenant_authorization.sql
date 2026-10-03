-- ============================================================
-- V20: Security, Authentication, and Tenant Authorization Enhancements
-- Adds failed login tracking and account lockout to platform_user
-- Adds refresh_token table for token rotation and revocation
-- ============================================================

-- 1. Add login tracking columns to platform_user
ALTER TABLE platform_user
    ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

-- 2. Create refresh_token table
CREATE TABLE IF NOT EXISTS refresh_token (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id                UUID         NOT NULL,
    token_hash             VARCHAR(64)  NOT NULL,
    expires_at             TIMESTAMPTZ  NOT NULL,
    revoked                BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at             TIMESTAMPTZ,
    replaced_by_token_hash VARCHAR(64),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES platform_user(id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_token_hash ON refresh_token(token_hash);

COMMENT ON TABLE refresh_token IS 'Server-side registry for active and revoked JWT refresh tokens';
COMMENT ON COLUMN refresh_token.token_hash IS 'SHA-256 hash of the issued refresh token; never plaintext';
COMMENT ON COLUMN refresh_token.replaced_by_token_hash IS 'Hash of the successor token issued during token rotation';
