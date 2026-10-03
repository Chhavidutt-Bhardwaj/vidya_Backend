-- ============================================================
-- V5: school_branch
-- Physical branch of a school (supports multi-campus future)
-- ============================================================

CREATE TABLE school_branch (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    school_id       UUID         NOT NULL,
    branch_code     VARCHAR(50)  NOT NULL,
    branch_name     VARCHAR(300) NOT NULL,
    is_main_branch  BOOLEAN      NOT NULL DEFAULT FALSE,
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    city            VARCHAR(100),
    state           VARCHAR(100),
    pincode         VARCHAR(20),
    country         VARCHAR(100) NOT NULL DEFAULT 'India',
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(20),
    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_school_branch PRIMARY KEY (id),
    CONSTRAINT uq_school_branch_code UNIQUE (branch_code),
    CONSTRAINT fk_school_branch_school
        FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT chk_school_branch_status CHECK (status IN (
        'ACTIVE', 'INACTIVE'
    ))
);

CREATE INDEX idx_school_branch_school  ON school_branch(school_id);
CREATE INDEX idx_school_branch_status  ON school_branch(status);

COMMENT ON TABLE  school_branch IS 'Physical branch of a school; one school may have multiple campuses';
COMMENT ON COLUMN school_branch.is_main_branch IS 'TRUE for the primary/head campus';
