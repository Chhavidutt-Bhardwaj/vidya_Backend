-- ============================================================
-- V3: community_member
-- Links platform_users to communities with a role
-- ============================================================

CREATE TABLE community_member (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID        NOT NULL,
    platform_user_id  UUID        NOT NULL,
    member_code       VARCHAR(50) NOT NULL,
    role              VARCHAR(30) NOT NULL,
    status            VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_member PRIMARY KEY (id),
    CONSTRAINT uq_community_member_code UNIQUE (member_code),
    CONSTRAINT uq_community_member_user UNIQUE (community_id, platform_user_id),
    CONSTRAINT fk_community_member_community
        FOREIGN KEY (community_id) REFERENCES community(id),
    CONSTRAINT fk_community_member_user
        FOREIGN KEY (platform_user_id) REFERENCES platform_user(id),
    CONSTRAINT chk_community_member_role CHECK (role IN (
        'COMMUNITY_ADMIN', 'COMMUNITY_MANAGER', 'FIELD_AGENT'
    )),
    CONSTRAINT chk_community_member_status CHECK (status IN (
        'ACTIVE', 'INACTIVE'
    ))
);

CREATE INDEX idx_community_member_community ON community_member(community_id);
CREATE INDEX idx_community_member_user      ON community_member(platform_user_id);
CREATE INDEX idx_community_member_status    ON community_member(status);

COMMENT ON TABLE community_member IS 'Links a platform_user to a community with an operational role';
COMMENT ON COLUMN community_member.role IS 'Role within this community; separate from platform_user.platform_role';
