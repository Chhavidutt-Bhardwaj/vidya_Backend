-- ============================================================
-- TENANT V17: Examination, Assessment & Grading Foundation
-- Manages exam terms, grading scales, exam schedules,
-- subject configurations, and student mark entries.
-- ============================================================

-- ── 1. Grading Scale & Tiers ─────────────────────────────────
CREATE TABLE grading_scale (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    scale_code          VARCHAR(50)   NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    description         VARCHAR(255)  NULL,
    is_default          BOOLEAN       NOT NULL DEFAULT false,

    deleted_at          TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          UUID          NULL,
    updated_by          UUID          NULL,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_grading_scale PRIMARY KEY (id),
    CONSTRAINT uq_grading_scale_code UNIQUE (scale_code)
);

CREATE TABLE grading_scale_tier (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    grading_scale_id    UUID          NOT NULL,
    grade               VARCHAR(10)   NOT NULL,
    min_percentage      NUMERIC(5,2)  NOT NULL,
    max_percentage      NUMERIC(5,2)  NOT NULL,
    grade_point         NUMERIC(4,2)  NOT NULL DEFAULT 0.00,
    description         VARCHAR(50)   NULL,
    is_passing          BOOLEAN       NOT NULL DEFAULT true,
    sort_order          INT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_grading_scale_tier PRIMARY KEY (id),
    CONSTRAINT fk_gst_grading_scale FOREIGN KEY (grading_scale_id)
        REFERENCES grading_scale(id) ON DELETE CASCADE
);

CREATE INDEX idx_gst_scale_id ON grading_scale_tier(grading_scale_id);

-- ── 2. Exam Term ─────────────────────────────────────────────
CREATE TABLE exam_term (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    term_code             VARCHAR(50)   NOT NULL,
    academic_year_id      UUID          NOT NULL,
    name                  VARCHAR(100)  NOT NULL,
    start_date            DATE          NOT NULL,
    end_date              DATE          NOT NULL,
    weightage_percentage  NUMERIC(5,2)  NOT NULL DEFAULT 0.00,
    status                VARCHAR(30)   NOT NULL DEFAULT 'PLANNED',

    deleted_at            TIMESTAMPTZ   NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            UUID          NULL,
    updated_by            UUID          NULL,
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_exam_term PRIMARY KEY (id),
    CONSTRAINT uq_exam_term_code UNIQUE (term_code),
    CONSTRAINT fk_et_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT chk_et_status CHECK (status IN ('PLANNED','SCHEDULED','IN_PROGRESS','EVALUATING','COMPLETED','CANCELLED'))
);

CREATE UNIQUE INDEX idx_et_unique_year_name
    ON exam_term(academic_year_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_et_year_status ON exam_term(academic_year_id, status);

-- ── 3. Exam Schedule ─────────────────────────────────────────
CREATE TABLE exam_schedule (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    schedule_code       VARCHAR(50)   NOT NULL,
    exam_term_id        UUID          NOT NULL,
    academic_class_id   UUID          NOT NULL,
    grading_scale_id    UUID          NULL,
    name                VARCHAR(100)  NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',

    deleted_at          TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          UUID          NULL,
    updated_by          UUID          NULL,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_exam_schedule PRIMARY KEY (id),
    CONSTRAINT uq_exam_schedule_code UNIQUE (schedule_code),
    CONSTRAINT fk_es_exam_term FOREIGN KEY (exam_term_id)
        REFERENCES exam_term(id) ON DELETE RESTRICT,
    CONSTRAINT fk_es_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_es_grading_scale FOREIGN KEY (grading_scale_id)
        REFERENCES grading_scale(id) ON DELETE SET NULL,
    CONSTRAINT chk_es_status CHECK (status IN ('DRAFT','PUBLISHED','IN_PROGRESS','CONCLUDED','LOCKED','CANCELLED'))
);

CREATE UNIQUE INDEX idx_es_unique_term_class
    ON exam_schedule(exam_term_id, academic_class_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_es_term_id  ON exam_schedule(exam_term_id);
CREATE INDEX idx_es_class_id ON exam_schedule(academic_class_id);

-- ── 4. Exam Subject Configuration ────────────────────────────
CREATE TABLE exam_subject_config (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    config_code           VARCHAR(50)   NOT NULL,
    exam_schedule_id      UUID          NOT NULL,
    subject_id            UUID          NOT NULL,
    exam_date             DATE          NOT NULL,
    start_time            TIME          NOT NULL,
    end_time              TIME          NOT NULL,
    max_marks             NUMERIC(6,2)  NOT NULL,
    pass_marks            NUMERIC(6,2)  NOT NULL,
    theory_max_marks      NUMERIC(6,2)  NULL,
    practical_max_marks   NUMERIC(6,2)  NULL,
    room_id               UUID          NULL,

    deleted_at            TIMESTAMPTZ   NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            UUID          NULL,
    updated_by            UUID          NULL,
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_exam_subject_config PRIMARY KEY (id),
    CONSTRAINT uq_exam_subject_config_code UNIQUE (config_code),
    CONSTRAINT fk_esc_schedule FOREIGN KEY (exam_schedule_id)
        REFERENCES exam_schedule(id) ON DELETE CASCADE,
    CONSTRAINT fk_esc_subject FOREIGN KEY (subject_id)
        REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_esc_room FOREIGN KEY (room_id)
        REFERENCES room(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX idx_esc_unique_schedule_subject
    ON exam_subject_config(exam_schedule_id, subject_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_esc_schedule_date ON exam_subject_config(exam_schedule_id, exam_date);

-- ── 5. Student Mark Entry ────────────────────────────────────
CREATE TABLE student_mark_entry (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    entry_code              VARCHAR(50)   NOT NULL,
    exam_subject_config_id  UUID          NOT NULL,
    student_id              UUID          NOT NULL,
    marks_obtained          NUMERIC(6,2)  NULL,
    theory_marks_obtained   NUMERIC(6,2)  NULL,
    practical_marks_obtained NUMERIC(6,2) NULL,
    grade                   VARCHAR(10)   NULL,
    grade_point             NUMERIC(4,2)  NULL,
    is_absent               BOOLEAN       NOT NULL DEFAULT false,
    is_exempt               BOOLEAN       NOT NULL DEFAULT false,
    remarks                 VARCHAR(255)  NULL,
    evaluated_by_staff_id   UUID          NULL,
    status                  VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',

    deleted_at              TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by              UUID          NULL,
    updated_by              UUID          NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_student_mark_entry PRIMARY KEY (id),
    CONSTRAINT uq_student_mark_entry_code UNIQUE (entry_code),
    CONSTRAINT fk_sme_config FOREIGN KEY (exam_subject_config_id)
        REFERENCES exam_subject_config(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sme_student FOREIGN KEY (student_id)
        REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sme_evaluator FOREIGN KEY (evaluated_by_staff_id)
        REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_sme_status CHECK (status IN ('DRAFT','SUBMITTED','VERIFIED','LOCKED'))
);

CREATE UNIQUE INDEX idx_sme_unique_config_student
    ON student_mark_entry(exam_subject_config_id, student_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_sme_config_status ON student_mark_entry(exam_subject_config_id, status);
CREATE INDEX idx_sme_student       ON student_mark_entry(student_id);
