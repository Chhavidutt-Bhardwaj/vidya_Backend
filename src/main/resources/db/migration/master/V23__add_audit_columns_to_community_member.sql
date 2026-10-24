-- ============================================================
-- V23: Add audit columns created_by and updated_by to community_member
-- Satisfies AuditableEntity mapping requirements for schema validation
-- ============================================================

ALTER TABLE community_member
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID;

COMMENT ON COLUMN community_member.created_by IS 'Platform user UUID who created this community member record';
COMMENT ON COLUMN community_member.updated_by IS 'Platform user UUID who last modified this community member record';
