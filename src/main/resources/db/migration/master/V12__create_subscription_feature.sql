-- ============================================================
-- V12: subscription_feature
-- Feature catalog available for inclusion in plans
-- ============================================================

CREATE TABLE subscription_feature (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    feature_code  VARCHAR(50)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    category      VARCHAR(30)  NOT NULL,
    status        VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_subscription_feature PRIMARY KEY (id),
    CONSTRAINT uq_subscription_feature_code UNIQUE (feature_code),
    CONSTRAINT chk_feature_category CHECK (category IN (
        'CORE', 'COMMUNICATION', 'ANALYTICS', 'AI', 'PORTAL', 'INTEGRATION', 'OTHER'
    )),
    CONSTRAINT chk_feature_status CHECK (status IN (
        'ACTIVE', 'INACTIVE'
    ))
);

CREATE INDEX idx_subscription_feature_status   ON subscription_feature(status);
CREATE INDEX idx_subscription_feature_category ON subscription_feature(category);

-- Seed initial feature catalog
INSERT INTO subscription_feature (feature_code, name, description, category, status) VALUES
    ('STUDENT_MANAGEMENT',  'Student Management',   'Manage student profiles and enrollment',    'CORE',          'ACTIVE'),
    ('STAFF_MANAGEMENT',    'Staff Management',     'Manage teacher and staff profiles',          'CORE',          'ACTIVE'),
    ('ATTENDANCE',          'Attendance',           'Daily attendance tracking',                  'CORE',          'ACTIVE'),
    ('FEES',                'Fee Management',       'Fee collection and receipts',               'CORE',          'ACTIVE'),
    ('EXAMINATION',         'Examination',          'Exam scheduling and marks entry',           'CORE',          'ACTIVE'),
    ('RESULTS',             'Results',              'Result publishing and report cards',        'CORE',          'ACTIVE'),
    ('TIMETABLE',           'Timetable',            'Class and teacher timetable management',    'CORE',          'ACTIVE'),
    ('TRANSPORT',           'Transport',            'Bus routes and student transport tracking', 'CORE',          'INACTIVE'),
    ('LIBRARY',             'Library',              'Book catalog and issue/return tracking',    'CORE',          'INACTIVE'),
    ('PARENT_PORTAL',       'Parent Portal',        'Parent-facing web and mobile portal',       'PORTAL',        'INACTIVE'),
    ('TEACHER_PORTAL',      'Teacher Portal',       'Teacher-facing portal',                     'PORTAL',        'INACTIVE'),
    ('STUDENT_PORTAL',      'Student Portal',       'Student self-service portal',               'PORTAL',        'INACTIVE'),
    ('SMS',                 'SMS Notifications',    'SMS alerts for fees, attendance, results',  'COMMUNICATION', 'INACTIVE'),
    ('EMAIL',               'Email Notifications',  'Email alerts and newsletters',              'COMMUNICATION', 'INACTIVE'),
    ('WHATSAPP',            'WhatsApp Integration', 'WhatsApp message integration',              'COMMUNICATION', 'INACTIVE'),
    ('REPORTING',           'Reporting',            'Standard reports and exports',              'ANALYTICS',     'INACTIVE'),
    ('ANALYTICS',           'Analytics',            'Advanced analytics dashboards',             'ANALYTICS',     'INACTIVE'),
    ('AI_INSIGHTS',         'AI Insights',          'AI-powered school performance insights',    'AI',            'INACTIVE');

COMMENT ON TABLE  subscription_feature IS 'Feature catalog; INACTIVE features are architecture placeholders for future implementation';
COMMENT ON COLUMN subscription_feature.feature_code IS 'Stable code used in entitlement checks; never rename after going live';
