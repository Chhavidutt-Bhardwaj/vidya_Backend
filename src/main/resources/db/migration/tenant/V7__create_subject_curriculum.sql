-- ============================================================
-- TENANT V7: Subject & Curriculum Foundation
-- Subject definitions, Curriculum structures, and CurriculumSubject mappings
-- ============================================================

-- 1. Create subject table
CREATE TABLE subject (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    subject_code        VARCHAR(50)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    short_name          VARCHAR(50)  NULL,
    description         TEXT         NULL,
    subject_type        VARCHAR(30)  NOT NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    display_order       INT          NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_subject PRIMARY KEY (id),
    CONSTRAINT uq_subject_code UNIQUE (subject_code),
    CONSTRAINT chk_subject_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_subject_type CHECK (
        subject_type IN ('CORE', 'ELECTIVE', 'LANGUAGE', 'CO_CURRICULAR', 'EXTRACURRICULAR', 'VOCATIONAL', 'OTHER')
    ),
    CONSTRAINT chk_subject_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_subject_code          ON subject(subject_code);
CREATE INDEX idx_subject_status        ON subject(status);
CREATE INDEX idx_subject_type          ON subject(subject_type);
CREATE INDEX idx_subject_name          ON subject(name);
CREATE INDEX idx_subject_display_order ON subject(display_order);
CREATE INDEX idx_subject_deleted_at    ON subject(deleted_at);

COMMENT ON TABLE  subject               IS 'Academic subject definitions within the school tenant database';
COMMENT ON COLUMN subject.subject_code  IS 'Stable Vidya business identifier for the subject (e.g. SUB-8A92K3LM). Immutable after creation.';
COMMENT ON COLUMN subject.subject_type  IS 'Controlled classification: CORE, ELECTIVE, LANGUAGE, CO_CURRICULAR, EXTRACURRICULAR, VOCATIONAL, OTHER.';
COMMENT ON COLUMN subject.deleted_at    IS 'Soft deletion timestamp. Historical records retained for academic audit integrity.';

-- 2. Create curriculum table
CREATE TABLE curriculum (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    curriculum_code     VARCHAR(50)  NOT NULL,
    academic_year_id    UUID         NOT NULL,
    name                VARCHAR(150) NOT NULL,
    description         TEXT         NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_curriculum PRIMARY KEY (id),
    CONSTRAINT uq_curriculum_code UNIQUE (curriculum_code),
    CONSTRAINT fk_curriculum_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT chk_curriculum_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'DELETED'))
);

CREATE INDEX idx_curriculum_code          ON curriculum(curriculum_code);
CREATE INDEX idx_curriculum_academic_year ON curriculum(academic_year_id);
CREATE INDEX idx_curriculum_status        ON curriculum(status);
CREATE INDEX idx_curriculum_deleted_at    ON curriculum(deleted_at);

COMMENT ON TABLE  curriculum                 IS 'Curriculum configurations tied to an AcademicYear';
COMMENT ON COLUMN curriculum.curriculum_code IS 'Stable Vidya business identifier for the curriculum (e.g. CUR-8A92K3LM). Immutable after creation.';

-- 3. Create curriculum_subject table
CREATE TABLE curriculum_subject (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    curriculum_id       UUID         NOT NULL,
    subject_id          UUID         NOT NULL,
    academic_class_id   UUID         NOT NULL,
    is_compulsory       BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order       INT          NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_curriculum_subject PRIMARY KEY (id),
    CONSTRAINT fk_curriculum_subject_curriculum FOREIGN KEY (curriculum_id)
        REFERENCES curriculum(id) ON DELETE RESTRICT,
    CONSTRAINT fk_curriculum_subject_subject FOREIGN KEY (subject_id)
        REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_curriculum_subject_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT chk_curriculum_subject_display_order CHECK (display_order >= 0)
);

-- Ensure at most one active mapping per (curriculum, subject, class) triplet
CREATE UNIQUE INDEX idx_curriculum_subject_unique_active
    ON curriculum_subject (curriculum_id, subject_id, academic_class_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_curriculum_subject_curriculum    ON curriculum_subject(curriculum_id);
CREATE INDEX idx_curriculum_subject_subject       ON curriculum_subject(subject_id);
CREATE INDEX idx_curriculum_subject_class         ON curriculum_subject(academic_class_id);
CREATE INDEX idx_curriculum_subject_display_order ON curriculum_subject(display_order);
CREATE INDEX idx_curriculum_subject_deleted_at    ON curriculum_subject(deleted_at);

COMMENT ON TABLE  curriculum_subject               IS 'Subject applicability mapping to Curriculum at AcademicClass level';
COMMENT ON COLUMN curriculum_subject.is_compulsory IS 'True if subject is compulsory for this class level, false if elective';
