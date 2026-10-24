-- ============================================================
-- V26: Normalize School Sub-Tables and Multi-Step Onboarding Journey
--
-- Breaks monolithic school details into isolated, normalized tables:
-- 1. school_contact_detail: Designated contacts (Principal, Admin, Billing, etc.)
-- 2. school_address: Physical campus, registered office, correspondence addresses
-- 3. school_board_affiliation: Multi-board affiliations (CBSE, HBSE, ICSE, IB, State)
-- 4. school_document: Compliance, statutory certificates, NOCs & verification lifecycle
-- 5. Updates OnboardingSource with WEBSITE and SchoolBoard with HBSE
-- ============================================================

-- 1. Update School Board check constraint to include HBSE
ALTER TABLE school DROP CONSTRAINT IF EXISTS chk_school_board;
ALTER TABLE school ADD CONSTRAINT chk_school_board CHECK (board IN (
    'CBSE', 'ICSE', 'STATE', 'HBSE', 'IB', 'IGCSE', 'OTHER', NULL
));

-- 2. Update School Onboarding Source to support WEBSITE
ALTER TABLE school_onboarding DROP CONSTRAINT IF EXISTS chk_school_onboarding_source;
ALTER TABLE school_onboarding ADD CONSTRAINT chk_school_onboarding_source CHECK (source IN (
    'DIRECT', 'COMMUNITY', 'PARTNER', 'REFERRAL', 'WEBSITE'
));

-- 3. Create school_contact_detail table
CREATE TABLE IF NOT EXISTS school_contact_detail (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    school_id         UUID         NOT NULL,
    branch_id         UUID,
    contact_type      VARCHAR(50)  NOT NULL,
    full_name         VARCHAR(200) NOT NULL,
    designation       VARCHAR(100),
    email             VARCHAR(255) NOT NULL,
    phone             VARCHAR(20)  NOT NULL,
    alternate_phone   VARCHAR(20),
    is_primary        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_school_contact_detail PRIMARY KEY (id),
    CONSTRAINT fk_school_contact_school FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE CASCADE,
    CONSTRAINT fk_school_contact_branch FOREIGN KEY (branch_id) REFERENCES school_branch(id) ON DELETE SET NULL,
    CONSTRAINT chk_school_contact_type CHECK (contact_type IN (
        'PRINCIPAL', 'VICE_PRINCIPAL', 'ADMINISTRATIVE_HEAD', 'BILLING_FINANCE',
        'ACADEMIC_DIRECTOR', 'EXAM_COORDINATOR', 'ADMISSIONS_INCHARGE', 'LEGAL_COUNSEL', 'OTHER'
    ))
);

CREATE INDEX IF NOT EXISTS idx_school_contact_school ON school_contact_detail(school_id);
CREATE INDEX IF NOT EXISTS idx_school_contact_type   ON school_contact_detail(contact_type);

-- 4. Create school_address table
CREATE TABLE IF NOT EXISTS school_address (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    school_id         UUID         NOT NULL,
    branch_id         UUID,
    address_type      VARCHAR(50)  NOT NULL DEFAULT 'CAMPUS_PRIMARY',
    address_line1     VARCHAR(255) NOT NULL,
    address_line2     VARCHAR(255),
    city              VARCHAR(100) NOT NULL,
    state             VARCHAR(100) NOT NULL,
    pincode           VARCHAR(20)  NOT NULL,
    country           VARCHAR(100) NOT NULL DEFAULT 'India',
    landmark          VARCHAR(200),
    latitude          NUMERIC(10, 7),
    longitude         NUMERIC(10, 7),
    is_primary        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_school_address PRIMARY KEY (id),
    CONSTRAINT fk_school_address_school FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE CASCADE,
    CONSTRAINT fk_school_address_branch FOREIGN KEY (branch_id) REFERENCES school_branch(id) ON DELETE SET NULL,
    CONSTRAINT chk_school_address_type CHECK (address_type IN (
        'CAMPUS_PRIMARY', 'REGISTERED_OFFICE', 'CORRESPONDENCE', 'BRANCH_CAMPUS'
    ))
);

CREATE INDEX IF NOT EXISTS idx_school_address_school    ON school_address(school_id);
CREATE INDEX IF NOT EXISTS idx_school_address_location  ON school_address(state, city);

-- 5. Create school_board_affiliation table (multi-board support: CBSE, HBSE, ICSE, IB, State)
CREATE TABLE IF NOT EXISTS school_board_affiliation (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    school_id              UUID         NOT NULL,
    board_type             VARCHAR(50)  NOT NULL,
    board_name             VARCHAR(150),
    affiliation_number     VARCHAR(100) NOT NULL,
    affiliation_level      VARCHAR(50)  NOT NULL DEFAULT 'ALL_LEVELS',
    valid_from             DATE,
    valid_to               DATE,
    is_primary_board       BOOLEAN      NOT NULL DEFAULT FALSE,
    affiliation_letter_url VARCHAR(500),
    status                 VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_school_board_affiliation PRIMARY KEY (id),
    CONSTRAINT fk_school_board_school FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE CASCADE,
    CONSTRAINT chk_school_board_type CHECK (board_type IN (
        'CBSE', 'ICSE', 'HBSE', 'STATE_BOARD', 'IB', 'CAMBRIDGE_IGCSE', 'OTHER'
    )),
    CONSTRAINT chk_school_board_level CHECK (affiliation_level IN (
        'PRIMARY', 'MIDDLE', 'SECONDARY', 'SENIOR_SECONDARY', 'ALL_LEVELS'
    )),
    CONSTRAINT chk_school_board_status CHECK (status IN (
        'ACTIVE', 'PENDING_RENEWAL', 'EXPIRED', 'PROVISIONAL'
    ))
);

CREATE INDEX IF NOT EXISTS idx_school_board_affiliation_school ON school_board_affiliation(school_id);
CREATE INDEX IF NOT EXISTS idx_school_board_affiliation_type   ON school_board_affiliation(board_type);

-- 6. Create school_document table (statutory certificates, NOCs, Trust Deed, Fire Safety, etc.)
CREATE TABLE IF NOT EXISTS school_document (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    school_id            UUID         NOT NULL,
    document_type        VARCHAR(60)  NOT NULL,
    document_name        VARCHAR(200) NOT NULL,
    document_number      VARCHAR(100),
    file_url             VARCHAR(500) NOT NULL,
    file_size_bytes      BIGINT,
    mime_type            VARCHAR(100),
    issued_by            VARCHAR(200),
    issued_date          DATE,
    expiry_date          DATE,
    verification_status  VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    verified_by_user_id  UUID,
    verified_at          TIMESTAMPTZ,
    rejection_reason     TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_school_document PRIMARY KEY (id),
    CONSTRAINT fk_school_document_school FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE CASCADE,
    CONSTRAINT chk_school_document_type CHECK (document_type IN (
        'BOARD_AFFILIATION_LETTER',
        'SOCIETY_TRUST_REGISTRATION',
        'RECOGNITION_CERTIFICATE',
        'FIRE_SAFETY_NOC',
        'BUILDING_SAFETY_CERTIFICATE',
        'LAND_TITLE_DEED',
        'WATER_SANITATION_CERTIFICATE',
        'SECTION_12A_80G',
        'MINORITY_STATUS_CERTIFICATE',
        'OTHER'
    )),
    CONSTRAINT chk_school_document_verification CHECK (verification_status IN (
        'PENDING', 'VERIFIED', 'REJECTED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_school_document_school       ON school_document(school_id);
CREATE INDEX IF NOT EXISTS idx_school_document_type         ON school_document(document_type);
CREATE INDEX IF NOT EXISTS idx_school_document_verification ON school_document(verification_status);

COMMENT ON TABLE school_contact_detail    IS 'Normalized contact directory for a school (Principal, Admin In-charge, Billing, etc.)';
COMMENT ON TABLE school_address           IS 'Normalized physical addresses for a school (Campus address, registered office, etc.)';
COMMENT ON TABLE school_board_affiliation IS 'Educational board affiliations (CBSE, HBSE, ICSE, IB) supporting multi-board institutions';
COMMENT ON TABLE school_document          IS 'Compliance, statutory certificates and accreditation documentation with audit verification';
