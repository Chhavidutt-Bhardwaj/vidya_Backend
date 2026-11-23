-- ============================================================
-- MASTER V28: Onboarding security & integrity fixes (Increment I0)
--
-- 1. school_onboarding.status: add PAYMENT_WAIVED (explicit, authorized payment waiver).
-- 2. school.board: replace a CHECK constraint that accepted any value.
--    `board IN (..., NULL)` evaluates to NULL (= pass) for unlisted values, so the V26
--    constraint never rejected anything. The new constraint allows NULL explicitly and
--    otherwise only the SchoolBoard enum values.
--
-- No existing data is modified. If existing rows violate the corrected board constraint,
-- the constraint is kept NOT VALID (enforced for all new writes) and a WARNING is raised
-- so the rows can be reviewed manually before running VALIDATE CONSTRAINT.
-- ============================================================

-- 1. Onboarding status: add PAYMENT_WAIVED
ALTER TABLE school_onboarding DROP CONSTRAINT IF EXISTS chk_school_onboarding_status;
ALTER TABLE school_onboarding ADD CONSTRAINT chk_school_onboarding_status CHECK (status IN (
    'DRAFT', 'SUBMITTED', 'VERIFICATION_PENDING', 'VERIFIED',
    'PLAN_SELECTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED', 'PAYMENT_WAIVED',
    'APPROVAL_PENDING', 'APPROVED', 'PROVISIONING', 'PROVISIONED',
    'ACTIVE', 'REJECTED', 'CANCELLED', 'FAILED', 'PROVISIONING_FAILED'
));

-- 2. School board: effective CHECK constraint
ALTER TABLE school DROP CONSTRAINT IF EXISTS chk_school_board;
ALTER TABLE school ADD CONSTRAINT chk_school_board CHECK (
    board IS NULL OR board IN ('CBSE', 'ICSE', 'HBSE', 'STATE', 'IB', 'IGCSE', 'OTHER')
) NOT VALID;

DO $$
DECLARE
    invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_count
      FROM school
     WHERE board IS NOT NULL
       AND board NOT IN ('CBSE', 'ICSE', 'HBSE', 'STATE', 'IB', 'IGCSE', 'OTHER');

    IF invalid_count = 0 THEN
        ALTER TABLE school VALIDATE CONSTRAINT chk_school_board;
    ELSE
        RAISE WARNING 'chk_school_board left NOT VALID: % existing school row(s) have an unrecognised board value. Review and correct them, then run: ALTER TABLE school VALIDATE CONSTRAINT chk_school_board;', invalid_count;
    END IF;
END $$;
