-- ============================================================
-- V21: Community Refactoring & Role Customization System
-- Normalizes community data into separate tables and adds role customization
-- ============================================================

-- 1. Community Personal Details Table
CREATE TABLE community_personal_detail (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID        NOT NULL,
    contact_person_name VARCHAR(200),
    designation       VARCHAR(100),
    alternate_email   VARCHAR(255),
    alternate_phone   VARCHAR(20),
    date_of_birth     DATE,
    gender            VARCHAR(20),
    pan_number        VARCHAR(20),
    aadhaar_number    VARCHAR(20),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_personal_detail PRIMARY KEY (id),
    CONSTRAINT uq_community_personal_detail UNIQUE (community_id),
    CONSTRAINT fk_community_personal_detail_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT chk_community_personal_detail_gender CHECK (gender IN (
        'MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'
    ))
);

COMMENT ON TABLE community_personal_detail IS 'Personal information details for a community';
COMMENT ON COLUMN community_personal_detail.community_id IS 'Reference to the community record';

-- 2. Community Professional Details Table
CREATE TABLE community_professional_detail (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID        NOT NULL,
    organization_type VARCHAR(100),
    industry          VARCHAR(100),
    business_registration_number VARCHAR(50),
    gst_number        VARCHAR(20),
    company_website   VARCHAR(255),
    linkedin_profile  VARCHAR(255),
    years_in_operation INT,
    employee_count    INT,
    annual_revenue_range VARCHAR(50),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_professional_detail PRIMARY KEY (id),
    CONSTRAINT uq_community_professional_detail UNIQUE (community_id),
    CONSTRAINT fk_community_professional_detail_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT chk_community_professional_detail_revenue CHECK (annual_revenue_range IN (
        'UNDER_10L', '10L_50L', '50L_1CR', '1CR_5CR', '5CR_10CR', '10CR_50CR', 'ABOVE_50CR'
    ))
);

COMMENT ON TABLE community_professional_detail IS 'Professional/business details for a community';

-- 3. Community Address Detail Table (Existing entity structure)
CREATE TABLE community_address_detail (
    id                          UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id                UUID        NOT NULL,
    billing_address_line1       VARCHAR(255),
    billing_address_line2       VARCHAR(255),
    billing_city                VARCHAR(100),
    billing_state               VARCHAR(100),
    billing_pincode             VARCHAR(20),
    billing_country             VARCHAR(100) DEFAULT 'India',
    operational_address_line1    VARCHAR(255),
    operational_address_line2    VARCHAR(255),
    operational_city            VARCHAR(100),
    operational_state           VARCHAR(100),
    operational_pincode          VARCHAR(20),
    operational_country          VARCHAR(100) DEFAULT 'India',
    same_as_billing             BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID,
    version                     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_address_detail PRIMARY KEY (id),
    CONSTRAINT uq_community_address_detail UNIQUE (community_id),
    CONSTRAINT fk_community_address_detail_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE
);

COMMENT ON TABLE community_address_detail IS 'Billing and operational address information for a community';

-- 4. Community Additional Address Table (Supports multiple addresses)
CREATE TABLE community_address (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID        NOT NULL,
    address_type      VARCHAR(30) NOT NULL,
    address_line1     VARCHAR(255) NOT NULL,
    address_line2     VARCHAR(255),
    landmark          VARCHAR(100),
    city              VARCHAR(100) NOT NULL,
    state             VARCHAR(100) NOT NULL,
    pincode           VARCHAR(20) NOT NULL,
    country           VARCHAR(100) NOT NULL DEFAULT 'India',
    is_primary        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_address PRIMARY KEY (id),
    CONSTRAINT fk_community_address_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT chk_community_address_type CHECK (address_type IN (
        'REGISTERED', 'OPERATIONAL', 'BILLING', 'CORRESPONDENCE', 'WAREHOUSE'
    ))
);

CREATE INDEX idx_community_address_community ON community_address(community_id);
CREATE INDEX idx_community_address_type ON community_address(address_type);

COMMENT ON TABLE community_address IS 'Additional address details for a community (supports multiple addresses)';
COMMENT ON COLUMN community_address.address_type IS 'Type of address: REGISTERED, OPERATIONAL, BILLING, etc.';

-- 5. Community Bank Details Table
CREATE TABLE community_bank_detail (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID        NOT NULL,
    bank_name         VARCHAR(200) NOT NULL,
    account_number    VARCHAR(50) NOT NULL,
    account_type      VARCHAR(30) NOT NULL,
    ifsc_code         VARCHAR(20) NOT NULL,
    branch_name       VARCHAR(200),
    account_holder_name VARCHAR(200) NOT NULL,
    is_primary        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_bank_detail PRIMARY KEY (id),
    CONSTRAINT fk_community_bank_detail_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT chk_community_bank_account_type CHECK (account_type IN (
        'SAVINGS', 'CURRENT', 'NRE', 'NRO'
    ))
);

CREATE INDEX idx_community_bank_detail_community ON community_bank_detail(community_id);

COMMENT ON TABLE community_bank_detail IS 'Bank account details for a community';

-- 6. Community Payroll Configuration Table
CREATE TABLE community_payroll (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID        NOT NULL,
    payroll_frequency VARCHAR(30) NOT NULL,
    payroll_day       INT,
    payroll_cycle_start_day INT,
    payroll_cycle_end_day INT,
    base_salary_structure JSONB,
    deduction_config  JSONB,
    allowance_config  JSONB,
    tax_config        JSONB,
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    effective_from    DATE        NOT NULL,
    effective_to      DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_payroll PRIMARY KEY (id),
    CONSTRAINT uq_community_payroll_active UNIQUE (community_id, is_active),
    CONSTRAINT fk_community_payroll_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT chk_community_payroll_frequency CHECK (payroll_frequency IN (
        'MONTHLY', 'BI_WEEKLY', 'WEEKLY', 'DAILY'
    )),
    CONSTRAINT chk_community_payroll_day CHECK (payroll_day BETWEEN 1 AND 31),
    CONSTRAINT chk_community_payroll_cycle CHECK (
        payroll_cycle_start_day BETWEEN 1 AND 31 AND
        payroll_cycle_end_day BETWEEN 1 AND 31
    )
);

CREATE INDEX idx_community_payroll_community ON community_payroll(community_id);

COMMENT ON TABLE community_payroll IS 'Payroll configuration for a community';
COMMENT ON COLUMN community_payroll.base_salary_structure IS 'JSONB config for salary bands and structures';

-- 7. Community Commission Structure Table
CREATE TABLE community_commission (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID        NOT NULL,
    commission_type   VARCHAR(30) NOT NULL,
    commission_rate   DECIMAL(10, 4) NOT NULL,
    commission_basis  VARCHAR(30) NOT NULL,
    tier_config       JSONB,
    minimum_threshold DECIMAL(15, 2),
    maximum_cap       DECIMAL(15, 2),
    payment_terms     VARCHAR(100),
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    effective_from    DATE        NOT NULL,
    effective_to      DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_commission PRIMARY KEY (id),
    CONSTRAINT fk_community_commission_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT chk_community_commission_type CHECK (commission_type IN (
        'REFERRAL', 'ONBOARDING', 'REVENUE_SHARE', 'PERFORMANCE', 'MILESTONE'
    )),
    CONSTRAINT chk_community_commission_basis CHECK (commission_basis IN (
        'PERCENTAGE', 'FIXED_AMOUNT', 'TIERED_PERCENTAGE', 'HYBRID'
    )),
    CONSTRAINT chk_community_commission_rate CHECK (commission_rate > 0)
);

CREATE INDEX idx_community_commission_community ON community_commission(community_id);
CREATE INDEX idx_community_commission_type ON community_commission(commission_type);

COMMENT ON TABLE community_commission IS 'Commission structure for a community';
COMMENT ON COLUMN community_commission.tier_config IS 'JSONB config for tier-based commission rates';

-- 8. Community Login History Table
CREATE TABLE community_login_history (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    community_id      UUID        NOT NULL,
    platform_user_id  UUID        NOT NULL,
    login_time        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    logout_time       TIMESTAMPTZ,
    ip_address        VARCHAR(50),
    user_agent        VARCHAR(500),
    device_type       VARCHAR(50),
    browser           VARCHAR(100),
    location          VARCHAR(200),
    login_status      VARCHAR(30) NOT NULL,
    failure_reason    VARCHAR(255),
    session_id        VARCHAR(255),

    CONSTRAINT pk_community_login_history PRIMARY KEY (id),
    CONSTRAINT fk_community_login_history_community
        FOREIGN KEY (community_id) REFERENCES community(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_login_history_user
        FOREIGN KEY (platform_user_id) REFERENCES platform_user(id) ON DELETE CASCADE,
    CONSTRAINT chk_community_login_status CHECK (login_status IN (
        'SUCCESS', 'FAILURE', 'LOCKED', 'TIMEOUT'
    ))
);

CREATE INDEX idx_community_login_history_community ON community_login_history(community_id);
CREATE INDEX idx_community_login_history_user ON community_login_history(platform_user_id);
CREATE INDEX idx_community_login_history_time ON community_login_history(login_time DESC);

COMMENT ON TABLE community_login_history IS 'Login history tracking for community members';

-- 9. Community Role Definition Table (Custom roles per community type)
CREATE TABLE community_role_definition (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    role_code         VARCHAR(50) NOT NULL,
    role_name         VARCHAR(100) NOT NULL,
    role_description  TEXT,
    community_type    VARCHAR(30) NOT NULL,
    is_system_role   BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    level             INT         NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_community_role_definition PRIMARY KEY (id),
    CONSTRAINT uq_community_role_code UNIQUE (role_code),
    CONSTRAINT uq_community_role_name_type UNIQUE (role_name, community_type),
    CONSTRAINT chk_community_role_type CHECK (community_type IN (
        'PARTNER', 'FIELD_AGENCY', 'NGO', 'GOVT', 'OTHER'
    ))
);

CREATE INDEX idx_community_role_definition_type ON community_role_definition(community_type);
CREATE INDEX idx_community_role_definition_active ON community_role_definition(is_active);

COMMENT ON TABLE community_role_definition IS 'Custom role definitions per community type';
COMMENT ON COLUMN community_role_definition.is_system_role IS 'System roles cannot be deleted';

-- 9. Community Permission Definition Table
CREATE TABLE community_permission (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    permission_code   VARCHAR(100) NOT NULL,
    permission_name   VARCHAR(200) NOT NULL,
    permission_group  VARCHAR(50) NOT NULL,
    description       TEXT,
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_community_permission PRIMARY KEY (id),
    CONSTRAINT uq_community_permission_code UNIQUE (permission_code)
);

CREATE INDEX idx_community_permission_group ON community_permission(permission_group);
CREATE INDEX idx_community_permission_active ON community_permission(is_active);

COMMENT ON TABLE community_permission IS 'Permission definitions for community roles';
COMMENT ON COLUMN community_permission.permission_group IS 'Group: DASHBOARD, MEMBERS, SCHOOLS, PAYROLL, COMMISSION, SETTINGS';

-- 10. Community Role Permission Mapping Table (Universal Integer Matrix)
CREATE TABLE community_role_permission (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    role_definition_id UUID       NOT NULL,
    section_code      INT         NOT NULL,
    action_code       INT         NOT NULL,
    granted           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_community_role_permission PRIMARY KEY (id),
    CONSTRAINT uq_role_section_action UNIQUE (role_definition_id, section_code, action_code),
    CONSTRAINT fk_community_role_permission_role
        FOREIGN KEY (role_definition_id) REFERENCES community_role_definition(id) ON DELETE CASCADE
);

CREATE INDEX idx_community_role_permission_role ON community_role_permission(role_definition_id);
CREATE INDEX idx_community_role_permission_section ON community_role_permission(section_code);

COMMENT ON TABLE community_role_permission IS 'Maps roles to permissions via universal section_code and action_code integers';

-- 11. Community Role Audit Log Table
CREATE TABLE community_role_audit_log (
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

CREATE INDEX idx_community_role_audit_log_community ON community_role_audit_log(community_id);
CREATE INDEX idx_community_role_audit_log_actor ON community_role_audit_log(actor_user_id);
CREATE INDEX idx_community_role_audit_log_time ON community_role_audit_log(occurred_at DESC);

COMMENT ON TABLE community_role_audit_log IS 'Audit log of all role definition changes and member role assignments';

-- 12. Update community_member to use custom roles
ALTER TABLE community_member
ADD COLUMN custom_role_id UUID,
ADD COLUMN last_active_at TIMESTAMPTZ,
ADD CONSTRAINT fk_community_member_custom_role
    FOREIGN KEY (custom_role_id) REFERENCES community_role_definition(id);

-- Rename existing role column to legacy_role for backward compatibility
ALTER TABLE community_member RENAME COLUMN role TO legacy_role;

CREATE INDEX idx_community_member_custom_role ON community_member(custom_role_id);

-- 13. Migrate existing community data to new structure
-- Migrate address from community to community_address
INSERT INTO community_address (
    community_id, address_type, address_line1, address_line2,
    city, state, pincode, country, is_primary,
    created_at, updated_at, created_by, updated_by, version
)
SELECT
    id, 'REGISTERED', address_line1, address_line2,
    city, state, pincode, country, TRUE,
    created_at, updated_at, created_by, updated_by, version
FROM community
WHERE address_line1 IS NOT NULL;

-- Drop legacy address and contact columns from community table
ALTER TABLE community
    DROP COLUMN IF EXISTS contact_email,
    DROP COLUMN IF EXISTS contact_phone,
    DROP COLUMN IF EXISTS address_line1,
    DROP COLUMN IF EXISTS address_line2,
    DROP COLUMN IF EXISTS city,
    DROP COLUMN IF EXISTS state,
    DROP COLUMN IF EXISTS pincode,
    DROP COLUMN IF EXISTS country;



-- Seed default role definitions for each community type
-- PARTNER community roles
INSERT INTO community_role_definition (role_code, role_name, role_description, community_type, is_system_role, level) VALUES
('PARTNER_ADMIN', 'Partner Admin', 'Full administrative access for partner community', 'PARTNER', TRUE, 1),
('PARTNER_MANAGER', 'Partner Manager', 'Managerial access with limited admin rights', 'PARTNER', TRUE, 2),
('PARTNER_AGENT', 'Partner Agent', 'Field agent with basic access', 'PARTNER', TRUE, 3),
('PARTNER_VIEWER', 'Partner Viewer', 'Read-only access', 'PARTNER', TRUE, 4);

-- FIELD_AGENCY community roles
INSERT INTO community_role_definition (role_code, role_name, role_description, community_type, is_system_role, level) VALUES
('FIELD_AGENCY_ADMIN', 'Agency Admin', 'Full administrative access for field agency', 'FIELD_AGENCY', TRUE, 1),
('FIELD_AGENCY_SUPERVISOR', 'Agency Supervisor', 'Supervisor with team management access', 'FIELD_AGENCY', TRUE, 2),
('FIELD_AGENT', 'Field Agent', 'Field agent with basic access', 'FIELD_AGENCY', TRUE, 3),
('FIELD_AGENCY_VIEWER', 'Agency Viewer', 'Read-only access', 'FIELD_AGENCY', TRUE, 4);

-- NGO community roles
INSERT INTO community_role_definition (role_code, role_name, role_description, community_type, is_system_role, level) VALUES
('NGO_ADMIN', 'NGO Admin', 'Full administrative access for NGO', 'NGO', TRUE, 1),
('NGO_COORDINATOR', 'NGO Coordinator', 'Coordinator with program management access', 'NGO', TRUE, 2),
('NGO_VOLUNTEER', 'NGO Volunteer', 'Volunteer with basic access', 'NGO', TRUE, 3),
('NGO_VIEWER', 'NGO Viewer', 'Read-only access', 'NGO', TRUE, 4);

-- GOVT community roles
INSERT INTO community_role_definition (role_code, role_name, role_description, community_type, is_system_role, level) VALUES
('GOVT_ADMIN', 'Government Admin', 'Full administrative access for government entity', 'GOVT', TRUE, 1),
('GOVT_OFFICER', 'Government Officer', 'Officer with standard access', 'GOVT', TRUE, 2),
('GOVT_CLERK', 'Government Clerk', 'Clerk with limited access', 'GOVT', TRUE, 3),
('GOVT_VIEWER', 'Government Viewer', 'Read-only access', 'GOVT', TRUE, 4);

-- OTHER community roles
INSERT INTO community_role_definition (role_code, role_name, role_description, community_type, is_system_role, level) VALUES
('OTHER_ADMIN', 'Admin', 'Full administrative access', 'OTHER', TRUE, 1),
('OTHER_MANAGER', 'Manager', 'Managerial access', 'OTHER', TRUE, 2),
('OTHER_MEMBER', 'Member', 'Standard member access', 'OTHER', TRUE, 3),
('OTHER_VIEWER', 'Viewer', 'Read-only access', 'OTHER', TRUE, 4);

-- Seed default permissions for community roles
INSERT INTO community_permission (permission_code, permission_name, permission_group, description) VALUES
-- Dashboard permissions
('dashboard.view', 'View Dashboard', 'DASHBOARD', 'View community dashboard and analytics'),
('dashboard.export', 'Export Dashboard Data', 'DASHBOARD', 'Export dashboard reports'),

-- Member management permissions
('members.view', 'View Members', 'MEMBERS', 'View community member list'),
('members.create', 'Add Members', 'MEMBERS', 'Add new members to community'),
('members.edit', 'Edit Members', 'MEMBERS', 'Edit member details'),
('members.delete', 'Delete Members', 'MEMBERS', 'Remove members from community'),
('members.assign_role', 'Assign Roles', 'MEMBERS', 'Assign roles to members'),

-- School management permissions
('schools.view', 'View Schools', 'SCHOOLS', 'View associated schools'),
('schools.create', 'Create Schools', 'SCHOOLS', 'Create new school records'),
('schools.edit', 'Edit Schools', 'SCHOOLS', 'Edit school details'),
('schools.delete', 'Delete Schools', 'SCHOOLS', 'Delete school records'),

-- Payroll permissions
('payroll.view', 'View Payroll', 'PAYROLL', 'View payroll data'),
('payroll.create', 'Create Payroll', 'PAYROLL', 'Create payroll entries'),
('payroll.edit', 'Edit Payroll', 'PAYROLL', 'Edit payroll entries'),
('payroll.approve', 'Approve Payroll', 'PAYROLL', 'Approve payroll for payment'),
('payroll.export', 'Export Payroll', 'PAYROLL', 'Export payroll reports'),

-- Commission permissions
('commission.view', 'View Commission', 'COMMISSION', 'View commission data'),
('commission.create', 'Create Commission', 'COMMISSION', 'Create commission entries'),
('commission.edit', 'Edit Commission', 'COMMISSION', 'Edit commission entries'),
('commission.approve', 'Approve Commission', 'COMMISSION', 'Approve commission payments'),

-- Settings permissions
('settings.view', 'View Settings', 'SETTINGS', 'View community settings'),
('settings.edit', 'Edit Settings', 'SETTINGS', 'Edit community settings'),
('settings.roles', 'Manage Roles', 'SETTINGS', 'Manage custom roles and permissions');

-- Seed default permissions for PARTNER_ADMIN role (full access)
INSERT INTO community_role_permission (role_definition_id, section_code, action_code, granted)
SELECT rd.id, sec.val, act.val, TRUE
FROM community_role_definition rd
CROSS JOIN (VALUES (1), (4), (20), (21), (22), (23)) AS sec(val)
CROSS JOIN (VALUES (-9), (-8), (-7), (-6), (-4)) AS act(val)
WHERE rd.role_code = 'PARTNER_ADMIN';

-- Seed default permissions for PARTNER_VIEWER role (read-only)
INSERT INTO community_role_permission (role_definition_id, section_code, action_code, granted)
SELECT rd.id, sec.val, -9, TRUE
FROM community_role_definition rd
CROSS JOIN (VALUES (1), (4), (20), (21)) AS sec(val)
WHERE rd.role_code = 'PARTNER_VIEWER';

-- Seed default permissions for FIELD_AGENT role
INSERT INTO community_role_permission (role_definition_id, section_code, action_code, granted)
SELECT rd.id, sec.val, act.val, TRUE
FROM community_role_definition rd
CROSS JOIN (VALUES (1), (4), (20), (21)) AS sec(val)
CROSS JOIN (VALUES (-9), (-8)) AS act(val)
WHERE rd.role_code = 'FIELD_AGENT';

