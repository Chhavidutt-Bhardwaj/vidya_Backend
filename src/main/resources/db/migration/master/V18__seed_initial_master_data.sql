-- ============================================================
-- V18: Seed initial master data for development and testing
-- ============================================================

-- 1. Platform Users
-- Passwords hashed with BCrypt (password: 'Password@123' -> $2a$10$7EqJtq98hPqEX7fNZaFWoOiiK7k5U7sT6wG.Wp5e9DfxbYm5lTpi.)
INSERT INTO platform_user (id, user_code, email, password_hash, first_name, last_name, phone, status, platform_role, created_at, updated_at, version)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'USR-ADMIN01', 'admin@vidya.io', '$2a$10$7EqJtq98hPqEX7fNZaFWoOiiK7k5U7sT6wG.Wp5e9DfxbYm5lTpi.', 'Super', 'Admin', '+919999999991', 'ACTIVE', 'PLATFORM_SUPER_ADMIN', NOW(), NOW(), 0),
    ('a0000000-0000-0000-0000-000000000002', 'USR-COMMADMIN01', 'agency.lead@edupartners.org', '$2a$10$7EqJtq98hPqEX7fNZaFWoOiiK7k5U7sT6wG.Wp5e9DfxbYm5lTpi.', 'Rajesh', 'Kumar', '+919876543210', 'ACTIVE', 'COMMUNITY_ADMIN', NOW(), NOW(), 0),
    ('a0000000-0000-0000-0000-000000000003', 'USR-AGENT01', 'agent.rahul@edupartners.org', '$2a$10$7EqJtq98hPqEX7fNZaFWoOiiK7k5U7sT6wG.Wp5e9DfxbYm5lTpi.', 'Rahul', 'Sharma', '+919876543211', 'ACTIVE', 'FIELD_AGENT', NOW(), NOW(), 0)
ON CONFLICT (user_code) DO NOTHING;

-- 2. Communities
INSERT INTO community (id, community_code, name, description, type, status, contact_email, contact_phone, address_line1, city, state, pincode, country, created_at, updated_at, version)
VALUES
    ('b0000000-0000-0000-0000-000000000001', 'COM-EDUPARTNER01', 'EduPartners Field Agency', 'Primary educational consulting and school onboarding partner in North India', 'FIELD_AGENCY', 'ACTIVE', 'contact@edupartners.org', '+919876543210', 'Connaught Place', 'New Delhi', 'Delhi', '110001', 'India', NOW(), NOW(), 0),
    ('b0000000-0000-0000-0000-000000000002', 'COM-NGOBHARAT01', 'Vidya Vikas Foundation', 'Non-profit organization driving digital literacy in rural schools', 'NGO', 'ACTIVE', 'info@vidyavikas.org', '+919876543211', 'Indiranagar', 'Bengaluru', 'Karnataka', '560038', 'India', NOW(), NOW(), 0)
ON CONFLICT (community_code) DO NOTHING;

-- 3. Community Members
INSERT INTO community_member (id, community_id, platform_user_id, member_code, role, status, joined_at, created_at, updated_at, version)
VALUES
    ('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 'MEM-COMMADM01', 'COMMUNITY_ADMIN', 'ACTIVE', NOW(), NOW(), NOW(), 0),
    ('c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003', 'MEM-AGENT001', 'FIELD_AGENT', 'ACTIVE', NOW(), NOW(), NOW(), 0)
ON CONFLICT (member_code) DO NOTHING;

-- 4. Subscription Plans (Matching V11 Schema)
INSERT INTO subscription_plan (id, plan_code, version, name, description, billing_cycle, base_price, currency, trial_days, status, effective_from, effective_to, created_at, updated_at, version_lock)
VALUES
    ('e0000000-0000-0000-0000-000000000001', 'BASIC', 1, 'Basic Starter Plan', 'Designed for small schools and rural institutions', 'MONTHLY', 1999.00, 'INR', 14, 'ACTIVE', CURRENT_DATE, NULL, NOW(), NOW(), 0),
    ('e0000000-0000-0000-0000-000000000002', 'STANDARD', 1, 'Standard Growth Plan', 'Designed for mid-sized K-12 schools needing full digital management', 'MONTHLY', 4999.00, 'INR', 14, 'ACTIVE', CURRENT_DATE, NULL, NOW(), NOW(), 0),
    ('e0000000-0000-0000-0000-000000000003', 'PREMIUM', 1, 'Enterprise Pro Plan', 'Multi-branch school networks with custom integrations and priority support', 'ANNUAL', 49999.00, 'INR', 30, 'ACTIVE', CURRENT_DATE, NULL, NOW(), NOW(), 0)
ON CONFLICT (plan_code, version) DO NOTHING;

-- 5. Link Plan Features (V13 Schema with V12 features)
INSERT INTO subscription_plan_feature (id, plan_id, feature_id, is_included, usage_limit, created_at)
SELECT gen_random_uuid(), p.id, f.id, true, NULL, NOW()
FROM subscription_plan p
CROSS JOIN subscription_feature f
WHERE p.plan_code = 'BASIC' AND f.feature_code IN ('STUDENT_MANAGEMENT', 'STAFF_MANAGEMENT', 'ATTENDANCE', 'FEES')
ON CONFLICT (plan_id, feature_id) DO NOTHING;

INSERT INTO subscription_plan_feature (id, plan_id, feature_id, is_included, usage_limit, created_at)
SELECT gen_random_uuid(), p.id, f.id, true, NULL, NOW()
FROM subscription_plan p
CROSS JOIN subscription_feature f
WHERE p.plan_code = 'STANDARD' AND f.feature_code IN ('STUDENT_MANAGEMENT', 'STAFF_MANAGEMENT', 'ATTENDANCE', 'FEES', 'EXAMINATION', 'RESULTS', 'TIMETABLE')
ON CONFLICT (plan_id, feature_id) DO NOTHING;

INSERT INTO subscription_plan_feature (id, plan_id, feature_id, is_included, usage_limit, created_at)
SELECT gen_random_uuid(), p.id, f.id, true, NULL, NOW()
FROM subscription_plan p
CROSS JOIN subscription_feature f
WHERE p.plan_code = 'PREMIUM'
ON CONFLICT (plan_id, feature_id) DO NOTHING;

-- 7. Sample Schools
INSERT INTO school (id, school_code, tenant_id, school_name, legal_name, registration_number, board, school_type, email, phone, website, status, created_at, updated_at, version)
VALUES
    ('f0000000-0000-0000-0000-000000000001', 'SCH-DELHIPUB01', NULL, 'Delhi Heritage Public School', 'Delhi Heritage Educational Society', 'REG-DHPS-2018', 'CBSE', 'SENIOR_SECONDARY', 'principal@delhiheritage.edu.in', '+911122334455', 'https://delhiheritage.edu.in', 'ACTIVE', NOW(), NOW(), 0),
    ('f0000000-0000-0000-0000-000000000002', 'SCH-BLRINTL01', NULL, 'Bangalore International Academy', 'Vikas Education Foundation', 'REG-BIA-2020', 'ICSE', 'ALL', 'contact@bia.edu.in', '+918022334455', 'https://bia.edu.in', 'DRAFT', NOW(), NOW(), 0)
ON CONFLICT (school_code) DO NOTHING;

-- 8. Sample School Branches
INSERT INTO school_branch (id, school_id, branch_code, branch_name, is_main_branch, address_line1, address_line2, city, state, pincode, country, contact_email, contact_phone, status, created_at, updated_at, version)
VALUES
    ('f1000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'BRN-DELHI-MAIN', 'Dwarka Main Campus', true, 'Plot 14, Sector 6', 'Dwarka', 'New Delhi', 'Delhi', '110075', 'India', 'dwarka@delhiheritage.edu.in', '+911122334455', 'ACTIVE', NOW(), NOW(), 0),
    ('f1000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000001', 'BRN-DELHI-ROHINI', 'Rohini Campus', false, 'Sector 15', 'Rohini', 'New Delhi', 'Delhi', '110085', 'India', 'rohini@delhiheritage.edu.in', '+911122334456', 'ACTIVE', NOW(), NOW(), 0),
    ('f1000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000002', 'BRN-BLR-KORAMANGALA', 'Koramangala Campus', true, '80 Feet Road, 4th Block', 'Koramangala', 'Bengaluru', 'Karnataka', '560034', 'India', 'koramangala@bia.edu.in', '+918022334455', 'ACTIVE', NOW(), NOW(), 0)
ON CONFLICT (branch_code) DO NOTHING;

