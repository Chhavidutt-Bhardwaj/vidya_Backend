-- =====================================================================================
-- V19: Link guardians to parent logins for data-level access control.
--
-- A PARENT account may only see students whose guardian row points at its tenant_user
-- (tenant_user.master_user_id = the platform login). Before this, guardians only had a
-- free-text email/phone and could not be tied to a login.
-- =====================================================================================

ALTER TABLE student_guardian
    ADD COLUMN IF NOT EXISTS guardian_user_id UUID NULL;

ALTER TABLE student_guardian
    ADD CONSTRAINT fk_student_guardian_user
        FOREIGN KEY (guardian_user_id) REFERENCES tenant_user(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_student_guardian_user
    ON student_guardian(guardian_user_id) WHERE deleted_at IS NULL;

COMMENT ON COLUMN student_guardian.guardian_user_id IS
    'Parent login (tenant_user) allowed to see this student; drives OWN data scope for parents';

-- One-time backfill: link existing guardians to the PARENT login with the same email.
-- Only exact (case-insensitive) matches against non-deleted PARENT users; ambiguous emails are skipped.
UPDATE student_guardian g
SET guardian_user_id = tu.id
FROM tenant_user tu
WHERE g.guardian_user_id IS NULL
  AND g.deleted_at IS NULL
  AND g.email IS NOT NULL
  AND tu.user_type = 'PARENT'
  AND tu.deleted_at IS NULL
  AND LOWER(tu.email) = LOWER(TRIM(g.email))
  AND (SELECT COUNT(*) FROM tenant_user t2
       WHERE t2.user_type = 'PARENT' AND t2.deleted_at IS NULL
         AND LOWER(t2.email) = LOWER(TRIM(g.email))) = 1;
