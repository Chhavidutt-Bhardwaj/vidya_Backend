-- TENANT V23: student enrollment setup — profile details, admission number sequences, parent links.

-- Extra per-student details (1:1 with student). Personal data stays in tenant_user_profile.
CREATE TABLE student_profile_detail (
    student_id        UUID         NOT NULL,
    blood_group       VARCHAR(5)   NULL,
    nationality       VARCHAR(60)  NULL DEFAULT 'Indian',
    mother_tongue     VARCHAR(60)  NULL,
    house             VARCHAR(50)  NULL,
    previous_school   VARCHAR(200) NULL,
    previous_class    VARCHAR(50)  NULL,
    tc_number_in      VARCHAR(60)  NULL,
    tc_number_out     VARCHAR(60)  NULL,
    medical_notes     VARCHAR(500) NULL,
    photo_url         VARCHAR(500) NULL,
    leaving_type      VARCHAR(20)  NULL,
    leaving_date      DATE         NULL,
    leaving_reason    VARCHAR(255) NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_student_profile_detail PRIMARY KEY (student_id),
    CONSTRAINT fk_student_profile_detail_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    CONSTRAINT chk_student_profile_blood_group CHECK (blood_group IS NULL OR blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    CONSTRAINT chk_student_profile_leaving_type CHECK (leaving_type IS NULL OR leaving_type IN ('TRANSFERRED', 'WITHDRAWN', 'GRADUATED'))
);

COMMENT ON TABLE student_profile_detail IS 'School-specific student details beyond the person profile; leaving fields are set when the student leaves';
COMMENT ON COLUMN student_profile_detail.tc_number_in IS 'Transfer certificate received from the previous school';
COMMENT ON COLUMN student_profile_detail.tc_number_out IS 'Transfer certificate issued by this school when the student left';

-- Admission numbers: one sequence per academic year, or one school-wide (academic_year_id NULL).
CREATE TABLE admission_sequence (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    academic_year_id  UUID         NULL,
    prefix            VARCHAR(20)  NOT NULL DEFAULT '',
    next_number       INT          NOT NULL DEFAULT 1,
    padding           INT          NOT NULL DEFAULT 4,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_admission_sequence PRIMARY KEY (id),
    CONSTRAINT fk_admission_sequence_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE CASCADE,
    CONSTRAINT chk_admission_sequence_next CHECK (next_number >= 1),
    CONSTRAINT chk_admission_sequence_padding CHECK (padding BETWEEN 0 AND 8)
);
CREATE UNIQUE INDEX uq_admission_sequence_year ON admission_sequence (COALESCE(academic_year_id, '00000000-0000-0000-0000-000000000000'::uuid));

COMMENT ON TABLE admission_sequence IS 'Next admission number per academic year (or school-wide when academic_year_id is NULL); format prefix + zero-padded number';

-- Faster parent lookups (same parent across siblings).
CREATE INDEX IF NOT EXISTS idx_student_guardian_email_lower ON student_guardian (lower(email)) WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tenant_user_phone ON tenant_user (phone) WHERE deleted_at IS NULL AND phone IS NOT NULL;
