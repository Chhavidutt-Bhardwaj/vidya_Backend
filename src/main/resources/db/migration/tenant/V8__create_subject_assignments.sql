-- ============================================================
-- TENANT V8: Teacher Subject Assignment + Class Subject Assignment
-- Deployment of subjects to classes/sections and teacher teaching assignments
-- ============================================================

-- 1. Create teacher_subject_assignment table
CREATE TABLE teacher_subject_assignment (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    assignment_code     VARCHAR(50)  NOT NULL,
    teacher_id          UUID         NOT NULL,
    subject_id          UUID         NOT NULL,
    academic_year_id    UUID         NOT NULL,
    academic_class_id   UUID         NOT NULL,
    section_id          UUID         NULL,
    curriculum_id       UUID         NULL,
    is_primary          BOOLEAN      NOT NULL DEFAULT TRUE,
    start_date          DATE         NULL,
    end_date            DATE         NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_teacher_subject_assignment PRIMARY KEY (id),
    CONSTRAINT uq_teacher_subject_assignment_code UNIQUE (assignment_code),
    CONSTRAINT fk_tsa_teacher FOREIGN KEY (teacher_id)
        REFERENCES staff(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tsa_subject FOREIGN KEY (subject_id)
        REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tsa_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tsa_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tsa_section FOREIGN KEY (section_id)
        REFERENCES section(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tsa_curriculum FOREIGN KEY (curriculum_id)
        REFERENCES curriculum(id) ON DELETE RESTRICT,
    CONSTRAINT chk_tsa_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'COMPLETED', 'CANCELLED', 'DELETED'))
);

-- Partial unique index to prevent duplicate active/draft teacher assignment for same class/section/subject/year
CREATE UNIQUE INDEX idx_tsa_unique_active
    ON teacher_subject_assignment (teacher_id, subject_id, academic_year_id, academic_class_id, COALESCE(section_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE deleted_at IS NULL AND status IN ('DRAFT', 'ACTIVE');

-- Partial unique index to enforce at most one primary active teacher per class/section/subject/year
CREATE UNIQUE INDEX idx_tsa_unique_active_primary
    ON teacher_subject_assignment (academic_year_id, academic_class_id, COALESCE(section_id, '00000000-0000-0000-0000-000000000000'::uuid), subject_id)
    WHERE is_primary = TRUE AND deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX idx_tsa_teacher_id       ON teacher_subject_assignment(teacher_id);
CREATE INDEX idx_tsa_subject_id       ON teacher_subject_assignment(subject_id);
CREATE INDEX idx_tsa_academic_year_id ON teacher_subject_assignment(academic_year_id);
CREATE INDEX idx_tsa_class_id         ON teacher_subject_assignment(academic_class_id);
CREATE INDEX idx_tsa_section_id       ON teacher_subject_assignment(section_id);
CREATE INDEX idx_tsa_curriculum_id    ON teacher_subject_assignment(curriculum_id);
CREATE INDEX idx_tsa_status           ON teacher_subject_assignment(status);
CREATE INDEX idx_tsa_deleted_at       ON teacher_subject_assignment(deleted_at);

COMMENT ON TABLE  teacher_subject_assignment                 IS 'Teaching assignments mapping teachers (staff) to subjects, classes, sections, and academic years';
COMMENT ON COLUMN teacher_subject_assignment.assignment_code IS 'Stable business identifier (e.g. TSA-8A92K3LM). Immutable after creation.';
COMMENT ON COLUMN teacher_subject_assignment.is_primary      IS 'Indicates if this teacher is the primary educator responsible for grading and main instruction';


-- 2. Create class_subject_assignment table
CREATE TABLE class_subject_assignment (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    assignment_code     VARCHAR(50)  NOT NULL,
    academic_year_id    UUID         NOT NULL,
    academic_class_id   UUID         NOT NULL,
    section_id          UUID         NULL,
    subject_id          UUID         NOT NULL,
    curriculum_id       UUID         NULL,
    is_compulsory       BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order       INT          NOT NULL DEFAULT 0,
    periods_per_week    INT          NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_class_subject_assignment PRIMARY KEY (id),
    CONSTRAINT uq_class_subject_assignment_code UNIQUE (assignment_code),
    CONSTRAINT fk_csa_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_csa_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_csa_section FOREIGN KEY (section_id)
        REFERENCES section(id) ON DELETE RESTRICT,
    CONSTRAINT fk_csa_subject FOREIGN KEY (subject_id)
        REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_csa_curriculum FOREIGN KEY (curriculum_id)
        REFERENCES curriculum(id) ON DELETE RESTRICT,
    CONSTRAINT chk_csa_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_csa_display_order CHECK (display_order >= 0),
    CONSTRAINT chk_csa_periods_per_week CHECK (periods_per_week IS NULL OR periods_per_week >= 0)
);

-- Partial unique index to prevent duplicate deployed subject in the same class/section/year
CREATE UNIQUE INDEX idx_csa_unique_active
    ON class_subject_assignment (academic_year_id, academic_class_id, COALESCE(section_id, '00000000-0000-0000-0000-000000000000'::uuid), subject_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_csa_academic_year_id ON class_subject_assignment(academic_year_id);
CREATE INDEX idx_csa_class_id         ON class_subject_assignment(academic_class_id);
CREATE INDEX idx_csa_section_id       ON class_subject_assignment(section_id);
CREATE INDEX idx_csa_subject_id       ON class_subject_assignment(subject_id);
CREATE INDEX idx_csa_curriculum_id    ON class_subject_assignment(curriculum_id);
CREATE INDEX idx_csa_status           ON class_subject_assignment(status);
CREATE INDEX idx_csa_display_order    ON class_subject_assignment(display_order);
CREATE INDEX idx_csa_deleted_at       ON class_subject_assignment(deleted_at);

COMMENT ON TABLE  class_subject_assignment                 IS 'Actual academic deployment of subjects to classes/sections in an academic year';
COMMENT ON COLUMN class_subject_assignment.assignment_code IS 'Stable business identifier (e.g. CSA-8A92K3LM). Immutable after creation.';
