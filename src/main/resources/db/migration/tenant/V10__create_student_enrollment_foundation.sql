-- ============================================================
-- TENANT V10: Student Academic Enrollment & Admission Foundation
-- student_academic_enrollment, student_subject_enrollment, student_guardian
-- ============================================================

-- 1. Create student_academic_enrollment table
CREATE TABLE student_academic_enrollment (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    enrollment_code     VARCHAR(50)  NOT NULL,
    student_id          UUID         NOT NULL,
    academic_year_id    UUID         NOT NULL,
    academic_class_id   UUID         NOT NULL,
    section_id          UUID         NULL,
    roll_number         VARCHAR(50)  NULL,
    enrollment_status   VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    admission_date      DATE         NULL,
    enrollment_date     DATE         NOT NULL DEFAULT CURRENT_DATE,
    withdrawal_date     DATE         NULL,
    withdrawal_reason   VARCHAR(255) NULL,
    remarks             TEXT         NULL,
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_student_academic_enrollment PRIMARY KEY (id),
    CONSTRAINT uq_student_academic_enrollment_code UNIQUE (enrollment_code),
    CONSTRAINT fk_sae_student FOREIGN KEY (student_id)
        REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sae_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sae_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sae_section FOREIGN KEY (section_id)
        REFERENCES section(id) ON DELETE RESTRICT,
    CONSTRAINT chk_sae_status CHECK (
        enrollment_status IN ('ADMITTED', 'ACTIVE', 'PROMOTED', 'TRANSFERRED', 'WITHDRAWN', 'COMPLETED', 'SUSPENDED', 'DELETED')
    ),
    CONSTRAINT chk_sae_dates CHECK (withdrawal_date IS NULL OR withdrawal_date >= enrollment_date)
);

-- At most one active/admitted enrollment per student per academic year
CREATE UNIQUE INDEX idx_sae_unique_active
    ON student_academic_enrollment (student_id, academic_year_id)
    WHERE deleted_at IS NULL AND enrollment_status IN ('ADMITTED', 'ACTIVE');

-- Unique roll number per active enrollment within Academic Year + Class + Section
CREATE UNIQUE INDEX idx_sae_unique_roll
    ON student_academic_enrollment (academic_year_id, academic_class_id, COALESCE(section_id, '00000000-0000-0000-0000-000000000000'::uuid), roll_number)
    WHERE deleted_at IS NULL AND roll_number IS NOT NULL AND roll_number <> '' AND enrollment_status IN ('ADMITTED', 'ACTIVE');

CREATE INDEX idx_sae_student_id       ON student_academic_enrollment(student_id);
CREATE INDEX idx_sae_academic_year_id ON student_academic_enrollment(academic_year_id);
CREATE INDEX idx_sae_class_id         ON student_academic_enrollment(academic_class_id);
CREATE INDEX idx_sae_section_id       ON student_academic_enrollment(section_id);
CREATE INDEX idx_sae_status           ON student_academic_enrollment(enrollment_status);
CREATE INDEX idx_sae_enrollment_date  ON student_academic_enrollment(enrollment_date);
CREATE INDEX idx_sae_roll_number      ON student_academic_enrollment(roll_number);
CREATE INDEX idx_sae_deleted_at       ON student_academic_enrollment(deleted_at);

COMMENT ON TABLE  student_academic_enrollment                   IS 'Academic enrollment representing a student placement in class and section for an academic year';
COMMENT ON COLUMN student_academic_enrollment.enrollment_code   IS 'Unique business identifier for academic enrollment (e.g. ENR-8F3K91)';
COMMENT ON COLUMN student_academic_enrollment.enrollment_status IS 'Controlled lifecycle: ADMITTED, ACTIVE, PROMOTED, TRANSFERRED, WITHDRAWN, COMPLETED, SUSPENDED, DELETED';
COMMENT ON COLUMN student_academic_enrollment.roll_number       IS 'Class/Section-specific roll number assigned for this academic enrollment';


-- 2. Create student_subject_enrollment table
CREATE TABLE student_subject_enrollment (
    id                          UUID         NOT NULL DEFAULT gen_random_uuid(),
    subject_enrollment_code     VARCHAR(50)  NOT NULL,
    enrollment_id               UUID         NOT NULL,
    subject_id                  UUID         NOT NULL,
    class_subject_assignment_id UUID         NULL,
    status                      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    effective_from              DATE         NOT NULL DEFAULT CURRENT_DATE,
    effective_to                DATE         NULL,
    remarks                     TEXT         NULL,
    deleted_at                  TIMESTAMPTZ  NULL,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                  UUID         NULL,
    updated_by                  UUID         NULL,
    version                     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_student_subject_enrollment PRIMARY KEY (id),
    CONSTRAINT uq_student_subject_enrollment_code UNIQUE (subject_enrollment_code),
    CONSTRAINT fk_sse_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES student_academic_enrollment(id) ON DELETE CASCADE,
    CONSTRAINT fk_sse_subject FOREIGN KEY (subject_id)
        REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sse_csa FOREIGN KEY (class_subject_assignment_id)
        REFERENCES class_subject_assignment(id) ON DELETE RESTRICT,
    CONSTRAINT chk_sse_status CHECK (status IN ('ACTIVE', 'DROPPED', 'EXEMPTED', 'COMPLETED', 'DELETED')),
    CONSTRAINT chk_sse_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- At most one active subject enrollment per student enrollment and subject
CREATE UNIQUE INDEX idx_sse_unique_active
    ON student_subject_enrollment (enrollment_id, subject_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX idx_sse_enrollment_id ON student_subject_enrollment(enrollment_id);
CREATE INDEX idx_sse_subject_id    ON student_subject_enrollment(subject_id);
CREATE INDEX idx_sse_csa_id        ON student_subject_enrollment(class_subject_assignment_id);
CREATE INDEX idx_sse_status        ON student_subject_enrollment(status);
CREATE INDEX idx_sse_deleted_at    ON student_subject_enrollment(deleted_at);

COMMENT ON TABLE  student_subject_enrollment                         IS 'Subject enrollment mappings for students during their academic enrollment';
COMMENT ON COLUMN student_subject_enrollment.subject_enrollment_code IS 'Unique business identifier (e.g. SSE-8F3K91)';
COMMENT ON COLUMN student_subject_enrollment.status                  IS 'Status: ACTIVE, DROPPED, EXEMPTED, COMPLETED, DELETED';


-- 3. Create student_guardian table
CREATE TABLE student_guardian (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    guardian_code         VARCHAR(50)   NOT NULL,
    student_id            UUID          NOT NULL,
    guardian_name         VARCHAR(150)  NOT NULL,
    relationship_type     VARCHAR(50)   NOT NULL,
    phone                 VARCHAR(30)   NULL,
    email                 VARCHAR(255)  NULL,
    occupation            VARCHAR(100)  NULL,
    annual_income         NUMERIC(15,2) NULL,
    address_line1         VARCHAR(255)  NULL,
    address_line2         VARCHAR(255)  NULL,
    city                  VARCHAR(100)  NULL,
    state                 VARCHAR(100)  NULL,
    postal_code           VARCHAR(20)   NULL,
    is_primary            BOOLEAN       NOT NULL DEFAULT FALSE,
    is_emergency_contact  BOOLEAN       NOT NULL DEFAULT FALSE,
    status                VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    deleted_at            TIMESTAMPTZ   NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            UUID          NULL,
    updated_by            UUID          NULL,
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_student_guardian PRIMARY KEY (id),
    CONSTRAINT uq_student_guardian_code UNIQUE (guardian_code),
    CONSTRAINT fk_student_guardian_student FOREIGN KEY (student_id)
        REFERENCES student(id) ON DELETE CASCADE,
    CONSTRAINT chk_student_guardian_relationship CHECK (
        relationship_type IN ('FATHER', 'MOTHER', 'LEGAL_GUARDIAN', 'GRANDPARENT', 'SIBLING', 'OTHER')
    ),
    CONSTRAINT chk_student_guardian_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

-- At most one active primary guardian per student
CREATE UNIQUE INDEX idx_guardian_unique_primary
    ON student_guardian (student_id)
    WHERE is_primary = TRUE AND deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX idx_student_guardian_student_id ON student_guardian(student_id);
CREATE INDEX idx_student_guardian_name       ON student_guardian(guardian_name);
CREATE INDEX idx_student_guardian_phone      ON student_guardian(phone);
CREATE INDEX idx_student_guardian_email      ON student_guardian(email);
CREATE INDEX idx_student_guardian_status     ON student_guardian(status);
CREATE INDEX idx_student_guardian_deleted_at ON student_guardian(deleted_at);

COMMENT ON TABLE  student_guardian                   IS 'Guardian, parent, and emergency contact records for students';
COMMENT ON COLUMN student_guardian.guardian_code     IS 'Unique business identifier for guardian record (e.g. GRD-8F3K91)';
COMMENT ON COLUMN student_guardian.relationship_type IS 'Relationship: FATHER, MOTHER, LEGAL_GUARDIAN, GRANDPARENT, SIBLING, OTHER';
