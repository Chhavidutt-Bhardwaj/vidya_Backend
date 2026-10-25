-- ============================================================
-- V27: subscription_tiers_modular_features_and_overrides
-- Subscription Plan Tiers (Free, Starter, Pro, Max, Custom, Tier 1/2/3),
-- Modular Feature & Sub-Category Catalog, Module-wise Pricing,
-- and School Subscription Feature Overrides.
-- ============================================================

-- 1. Enhance subscription_plan with tier, is_custom, and target_school_id
ALTER TABLE subscription_plan
    ADD COLUMN IF NOT EXISTS tier VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
    ADD COLUMN IF NOT EXISTS is_custom BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS target_school_id UUID;

ALTER TABLE subscription_plan
    DROP CONSTRAINT IF EXISTS chk_subscription_plan_tier;

ALTER TABLE subscription_plan
    ADD CONSTRAINT chk_subscription_plan_tier CHECK (tier IN (
        'FREE', 'STARTER', 'PRO', 'MAX', 'TIER_1', 'TIER_2', 'TIER_3', 'CUSTOM'
    ));

ALTER TABLE subscription_plan
    DROP CONSTRAINT IF EXISTS fk_subscription_plan_target_school;

ALTER TABLE subscription_plan
    ADD CONSTRAINT fk_subscription_plan_target_school
        FOREIGN KEY (target_school_id) REFERENCES school(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_subscription_plan_tier ON subscription_plan(tier);
CREATE INDEX IF NOT EXISTS idx_subscription_plan_custom ON subscription_plan(is_custom, target_school_id);

-- Update existing plans with tier classifications
UPDATE subscription_plan SET tier = 'STARTER' WHERE plan_code = 'BASIC';
UPDATE subscription_plan SET tier = 'PRO' WHERE plan_code = 'STANDARD';
UPDATE subscription_plan SET tier = 'MAX' WHERE plan_code = 'PREMIUM';

-- Seed FREE Tier Plan if not exists
INSERT INTO subscription_plan (id, plan_code, version, name, description, billing_cycle, base_price, currency, trial_days, status, effective_from, created_at, updated_at, version_lock, tier, is_custom)
VALUES
    ('e0000000-0000-0000-0000-000000000000', 'FREE', 1, 'Vidya Free Community Edition',
     'Zero-cost tier for small community and rural schools with essential student and staff registries',
     'ANNUAL', 0.00, 'INR', 0, 'ACTIVE', CURRENT_DATE, NOW(), NOW(), 0, 'FREE', FALSE)
ON CONFLICT (plan_code, version) DO NOTHING;

-- 2. Enhance subscription_feature with modular hierarchy, sub-categories, and module-wise pricing
ALTER TABLE subscription_feature
    ADD COLUMN IF NOT EXISTS parent_module_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS sub_category VARCHAR(50),
    ADD COLUMN IF NOT EXISTS base_price NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS is_addon BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_subscription_feature_module ON subscription_feature(parent_module_code);
CREATE INDEX IF NOT EXISTS idx_subscription_feature_subcat ON subscription_feature(sub_category);

-- Update existing features with module mappings
UPDATE subscription_feature SET parent_module_code = 'STUDENT_MANAGEMENT', sub_category = 'CORE_STUDENT', base_price = 500.00, display_order = 10 WHERE feature_code = 'STUDENT_MANAGEMENT';
UPDATE subscription_feature SET parent_module_code = 'STAFF_MANAGEMENT',   sub_category = 'CORE_STAFF',   base_price = 500.00, display_order = 20 WHERE feature_code = 'STAFF_MANAGEMENT';
UPDATE subscription_feature SET parent_module_code = 'ATTENDANCE',         sub_category = 'ATTENDANCE',   base_price = 300.00, display_order = 30 WHERE feature_code = 'ATTENDANCE';
UPDATE subscription_feature SET parent_module_code = 'FEES',               sub_category = 'FINANCE',      base_price = 800.00, display_order = 40 WHERE feature_code = 'FEES';
UPDATE subscription_feature SET parent_module_code = 'EXAMINATION',        sub_category = 'ACADEMICS',    base_price = 600.00, display_order = 50 WHERE feature_code = 'EXAMINATION';
UPDATE subscription_feature SET parent_module_code = 'EXAMINATION',        sub_category = 'ACADEMICS',    base_price = 400.00, display_order = 55 WHERE feature_code = 'RESULTS';
UPDATE subscription_feature SET parent_module_code = 'TIMETABLE',          sub_category = 'ACADEMICS',    base_price = 300.00, display_order = 60 WHERE feature_code = 'TIMETABLE';
UPDATE subscription_feature SET parent_module_code = 'TRANSPORT',          sub_category = 'OPERATIONS',   base_price = 700.00, is_addon = TRUE, display_order = 70 WHERE feature_code = 'TRANSPORT';
UPDATE subscription_feature SET parent_module_code = 'LIBRARY',            sub_category = 'OPERATIONS',   base_price = 400.00, is_addon = TRUE, display_order = 80 WHERE feature_code = 'LIBRARY';

-- 3. Seed Granular Sub-Features (Staff Attendance, Staff Payroll, Leave, Admissions, Biometric, etc.)
INSERT INTO subscription_feature (id, feature_code, name, description, category, status, parent_module_code, sub_category, base_price, is_addon, display_order, created_at, updated_at, version)
VALUES
    -- Teacher / Staff Section Sub-Features
    (gen_random_uuid(), 'STAFF_DIRECTORY', 'Staff Directory & Documents', 'Staff profile records, credentials, and verification', 'CORE', 'ACTIVE', 'STAFF_MANAGEMENT', 'STAFF_PROFILE', 200.00, FALSE, 21, NOW(), NOW(), 0),
    (gen_random_uuid(), 'STAFF_ATTENDANCE', 'Teacher & Staff Attendance', 'Daily teacher clock-in, biometric tracking, and duty logs', 'CORE', 'ACTIVE', 'STAFF_MANAGEMENT', 'ATTENDANCE', 400.00, TRUE, 22, NOW(), NOW(), 0),
    (gen_random_uuid(), 'STAFF_PAYROLL', 'Teacher & Staff Payroll', 'Monthly salary structures, payslip generation, deductions, and disbursement', 'CORE', 'ACTIVE', 'STAFF_MANAGEMENT', 'PAYROLL', 1200.00, TRUE, 23, NOW(), NOW(), 0),
    (gen_random_uuid(), 'STAFF_LEAVE', 'Staff Leave Management', 'Leave application, approval workflow, casual/medical leave balances', 'CORE', 'ACTIVE', 'STAFF_MANAGEMENT', 'LEAVE', 350.00, TRUE, 24, NOW(), NOW(), 0),
    (gen_random_uuid(), 'STAFF_SUBJECT_MAPPING', 'Subject & Class Teacher Allocation', 'Workload balancing, period allotment, and class teacher designation', 'CORE', 'ACTIVE', 'STAFF_MANAGEMENT', 'ACADEMICS', 250.00, FALSE, 25, NOW(), NOW(), 0),

    -- Student Section Sub-Features
    (gen_random_uuid(), 'STUDENT_ADMISSIONS', 'Admissions & Enrollment Wizard', 'Online and counter student enquiry, token tracking, and onboarding', 'CORE', 'ACTIVE', 'STUDENT_MANAGEMENT', 'ADMISSION', 600.00, TRUE, 11, NOW(), NOW(), 0),
    (gen_random_uuid(), 'STUDENT_DIRECTORY', 'Student Profiles & Guardians', '360 student records, parent linkage, medical info, and KYC', 'CORE', 'ACTIVE', 'STUDENT_MANAGEMENT', 'STUDENT_PROFILE', 300.00, FALSE, 12, NOW(), NOW(), 0),
    (gen_random_uuid(), 'STUDENT_PROMOTION', 'Annual Student Promotion Engine', 'Bulk progression between academic sessions and section allocations', 'CORE', 'ACTIVE', 'STUDENT_MANAGEMENT', 'PROMOTION', 300.00, FALSE, 13, NOW(), NOW(), 0),
    (gen_random_uuid(), 'STUDENT_TRANSFER', 'Transfer Certificates (TC) & Exit', 'Automated statutory TC generation, clearance tracking, and student archiving', 'CORE', 'ACTIVE', 'STUDENT_MANAGEMENT', 'EXIT', 250.00, FALSE, 14, NOW(), NOW(), 0),

    -- Attendance Sub-Features
    (gen_random_uuid(), 'STUDENT_DAILY_ATTENDANCE', 'Daily Section Attendance', 'Class teacher register, mobile app roll-call, and excused absence marking', 'CORE', 'ACTIVE', 'ATTENDANCE', 'ATTENDANCE', 250.00, FALSE, 31, NOW(), NOW(), 0),
    (gen_random_uuid(), 'ATTENDANCE_SMS_ALERTS', 'Instant Absence SMS Alerts', 'Automated SMS alerts to parents when student is absent without notice', 'COMMUNICATION', 'ACTIVE', 'ATTENDANCE', 'ALERTS', 400.00, TRUE, 32, NOW(), NOW(), 0),
    (gen_random_uuid(), 'BIOMETRIC_SYNC', 'Biometric & RFID Device Integration', 'Real-time synchronization with school biometric turnstiles and bus RFID', 'INTEGRATION', 'ACTIVE', 'ATTENDANCE', 'HARDWARE', 800.00, TRUE, 33, NOW(), NOW(), 0),

    -- Examination & Assessment Sub-Features
    (gen_random_uuid(), 'EXAM_SCHEDULING', 'Exam Terms & Datesheet Manager', 'Term creation, datesheet publishing, hall tickets, and seating arrangement', 'CORE', 'ACTIVE', 'EXAMINATION', 'EXAM_PLANNING', 400.00, FALSE, 51, NOW(), NOW(), 0),
    (gen_random_uuid(), 'MARKS_ENTRY', 'Subject Marks & Grading Workflow', 'Online marks entry by teachers, lock/unlock deadlines, and moderation', 'CORE', 'ACTIVE', 'EXAMINATION', 'MARKS', 350.00, FALSE, 52, NOW(), NOW(), 0),
    (gen_random_uuid(), 'REPORT_CARDS', 'Automated Report Card Generation', 'Standard CBSE, HBSE, and State Board printable report cards with grades/percentiles', 'CORE', 'ACTIVE', 'EXAMINATION', 'REPORT_CARD', 500.00, TRUE, 53, NOW(), NOW(), 0),

    -- Fee & Finance Sub-Features
    (gen_random_uuid(), 'FEE_STRUCTURE', 'Fee Head & Installment Templates', 'Flexible tuition, lab, exam, transport installment schedule builder', 'CORE', 'ACTIVE', 'FEES', 'FEE_SETUP', 400.00, FALSE, 41, NOW(), NOW(), 0),
    (gen_random_uuid(), 'FEE_COLLECTION', 'Counter Fee Collection & Receipts', 'Multi-mode offline cash/cheque collection, GST receipts, and dues ledger', 'CORE', 'ACTIVE', 'FEES', 'COLLECTION', 500.00, FALSE, 42, NOW(), NOW(), 0),
    (gen_random_uuid(), 'ONLINE_PAYMENT_GATEWAY', 'Online Parent Payment Gateway', 'Integrated Razorpay/Cashfree payment gateway for parent mobile app', 'INTEGRATION', 'ACTIVE', 'FEES', 'PAYMENT_GATEWAY', 900.00, TRUE, 43, NOW(), NOW(), 0),
    (gen_random_uuid(), 'FEE_CONCESSIONS', 'Scholarships & Fee Concessions', 'Category-based fee discounts, sibling waivers, and management quotas', 'CORE', 'ACTIVE', 'FEES', 'CONCESSIONS', 350.00, TRUE, 44, NOW(), NOW(), 0)
ON CONFLICT (feature_code) DO UPDATE SET
    parent_module_code = EXCLUDED.parent_module_code,
    sub_category = EXCLUDED.sub_category,
    base_price = EXCLUDED.base_price,
    is_addon = EXCLUDED.is_addon,
    display_order = EXCLUDED.display_order;

-- Link core features to FREE plan
INSERT INTO subscription_plan_feature (id, plan_id, feature_id, is_included, usage_limit, created_at)
SELECT gen_random_uuid(), 'e0000000-0000-0000-0000-000000000000', f.id, true, 100, NOW()
FROM subscription_feature f
WHERE f.feature_code IN ('STUDENT_DIRECTORY', 'STAFF_DIRECTORY', 'STUDENT_DAILY_ATTENDANCE')
ON CONFLICT (plan_id, feature_id) DO NOTHING;

-- 4. School Subscription Feature Override Table
-- Allows SuperUser or Platform Admin to enable/disable specific modules or sub-features
-- directly on an individual school's subscription (e.g. Add Payroll only, or disable Leave)
CREATE TABLE IF NOT EXISTS school_subscription_feature_override (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    school_id           UUID           NOT NULL,
    subscription_id     UUID,
    feature_code        VARCHAR(50)    NOT NULL,
    is_enabled          BOOLEAN        NOT NULL,
    custom_usage_limit  INTEGER,
    addon_price         NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    notes               TEXT,
    overridden_by       UUID,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_school_subscription_feature_override PRIMARY KEY (id),
    CONSTRAINT uq_school_feature_override UNIQUE (school_id, feature_code),
    CONSTRAINT fk_override_school
        FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE CASCADE,
    CONSTRAINT fk_override_subscription
        FOREIGN KEY (subscription_id) REFERENCES school_subscription(id) ON DELETE CASCADE,
    CONSTRAINT fk_override_feature_code
        FOREIGN KEY (feature_code) REFERENCES subscription_feature(feature_code) ON DELETE RESTRICT,
    CONSTRAINT chk_override_addon_price CHECK (addon_price >= 0),
    CONSTRAINT chk_override_usage_limit CHECK (custom_usage_limit IS NULL OR custom_usage_limit > 0)
);

CREATE INDEX IF NOT EXISTS idx_override_school ON school_subscription_feature_override(school_id);
CREATE INDEX IF NOT EXISTS idx_override_feature ON school_subscription_feature_override(feature_code);
CREATE INDEX IF NOT EXISTS idx_override_subscription ON school_subscription_feature_override(subscription_id);

COMMENT ON TABLE school_subscription_feature_override IS 'Granular school-level feature and sub-feature toggles enabled/disabled by SuperUsers';
