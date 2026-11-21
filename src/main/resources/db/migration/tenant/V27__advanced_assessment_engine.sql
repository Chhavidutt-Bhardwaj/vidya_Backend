-- =====================================================================================
-- V27: Advanced examination & assessment engine (multi-board).
--
--  1. Evaluation model   assessment_scheme (+ classes, subjects, components), co-scholastic areas and grades,
--                        competency / learning-outcome framework and ratings, formative/summative assessments
--  2. Results            mark adjustments (grace, moderation, re-evaluation), moderation batches,
--                        re-evaluation requests, effective_exam_mark view, report-card result columns
--  3. Question bank      questions (10 formats), outcome tags, blueprints, generated question papers
--  4. Delivery           online exams (attempts, responses), OMR batches and sheets, question-level scores
--
-- Raw marks in student_mark_entry are never overwritten: every change after entry is a mark_adjustment row,
-- and results read effective_exam_mark (raw + approved adjustments, kept within 0..max).
-- =====================================================================================

-- ═════════════════════════════ 1. Evaluation model ═════════════════════════════

CREATE TABLE assessment_scheme (
    id                           UUID          NOT NULL DEFAULT gen_random_uuid(),
    scheme_code                  VARCHAR(50)   NOT NULL,
    name                         VARCHAR(150)  NOT NULL,
    board                        VARCHAR(20)   NOT NULL,
    academic_year_id             UUID          NOT NULL,
    description                  TEXT          NULL,
    preset_key                   VARCHAR(50)   NULL,
    scholastic_grading_scale_id  UUID          NULL,
    co_scholastic_grades         VARCHAR(100)  NOT NULL DEFAULT 'A,B,C',
    competency_levels            VARCHAR(200)  NOT NULL DEFAULT 'BEGINNING,DEVELOPING,PROFICIENT,ADVANCED',
    subject_pass_percent         NUMERIC(5,2)  NOT NULL DEFAULT 33,
    overall_pass_percent         NUMERIC(5,2)  NULL,
    separate_component_pass      BOOLEAN       NOT NULL DEFAULT FALSE,
    rounding_mode                VARCHAR(20)   NOT NULL DEFAULT 'HALF_UP',
    rounding_scale               INT           NOT NULL DEFAULT 0,
    best_of_subjects             INT           NULL,
    term_aggregation             VARCHAR(20)   NOT NULL DEFAULT 'WEIGHTED',
    grace_max_per_subject        NUMERIC(5,2)  NOT NULL DEFAULT 0,
    grace_max_total              NUMERIC(6,2)  NOT NULL DEFAULT 0,
    grace_max_subjects           INT           NOT NULL DEFAULT 0,
    compartment_max_subjects     INT           NOT NULL DEFAULT 0,
    reevaluation_window_days     INT           NOT NULL DEFAULT 7,
    rank_enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
    status                       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    deleted_at                   TIMESTAMPTZ   NULL,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                   UUID          NULL,
    updated_by                   UUID          NULL,
    version                      BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_assessment_scheme PRIMARY KEY (id),
    CONSTRAINT uq_assessment_scheme_code UNIQUE (scheme_code),
    CONSTRAINT fk_assessment_scheme_year  FOREIGN KEY (academic_year_id) REFERENCES academic_year(id),
    CONSTRAINT fk_assessment_scheme_scale FOREIGN KEY (scholastic_grading_scale_id) REFERENCES grading_scale(id) ON DELETE SET NULL,
    CONSTRAINT chk_assessment_scheme_board CHECK (board IN ('CBSE', 'ICSE', 'ISC', 'STATE', 'IB', 'IGCSE', 'CUSTOM')),
    CONSTRAINT chk_assessment_scheme_rounding CHECK (rounding_mode IN ('NONE', 'HALF_UP', 'UP', 'DOWN')),
    CONSTRAINT chk_assessment_scheme_aggregation CHECK (term_aggregation IN ('WEIGHTED', 'AVERAGE', 'LATEST')),
    CONSTRAINT chk_assessment_scheme_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_assessment_scheme_pass CHECK (subject_pass_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_assessment_scheme_numbers CHECK (rounding_scale BETWEEN 0 AND 2 AND grace_max_per_subject >= 0
        AND grace_max_total >= 0 AND grace_max_subjects >= 0 AND compartment_max_subjects >= 0
        AND (best_of_subjects IS NULL OR best_of_subjects > 0) AND reevaluation_window_days >= 0)
);

CREATE UNIQUE INDEX uq_assessment_scheme_year_name ON assessment_scheme (academic_year_id, lower(name)) WHERE deleted_at IS NULL;

-- One scheme per class per year: a school can run CBSE in some classes and a state board or IGCSE in others.
CREATE TABLE assessment_scheme_class (
    scheme_id          UUID NOT NULL,
    academic_year_id   UUID NOT NULL,
    academic_class_id  UUID NOT NULL,
    CONSTRAINT pk_assessment_scheme_class PRIMARY KEY (scheme_id, academic_class_id),
    CONSTRAINT uq_assessment_scheme_class_year UNIQUE (academic_year_id, academic_class_id),
    CONSTRAINT fk_asc_scheme FOREIGN KEY (scheme_id) REFERENCES assessment_scheme(id) ON DELETE CASCADE,
    CONSTRAINT fk_asc_class  FOREIGN KEY (academic_class_id) REFERENCES academic_class(id)
);

-- Subjects that are not plain scholastic subjects (no row = SCHOLASTIC).
CREATE TABLE assessment_scheme_subject (
    scheme_id     UUID         NOT NULL,
    subject_id    UUID         NOT NULL,
    category      VARCHAR(20)  NOT NULL,
    pass_percent  NUMERIC(5,2) NULL,
    CONSTRAINT pk_assessment_scheme_subject PRIMARY KEY (scheme_id, subject_id),
    CONSTRAINT fk_ass_scheme  FOREIGN KEY (scheme_id) REFERENCES assessment_scheme(id) ON DELETE CASCADE,
    CONSTRAINT fk_ass_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT chk_ass_category CHECK (category IN ('SCHOLASTIC', 'ADDITIONAL', 'GRADE_ONLY', 'EXCLUDED'))
);

-- What a subject's term result is made of, e.g. Periodic Test 10 + Notebook 5 + Enrichment 5 + Term exam 80.
CREATE TABLE assessment_component (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    scheme_id        UUID          NOT NULL,
    code             VARCHAR(30)   NOT NULL,
    name             VARCHAR(100)  NOT NULL,
    assessment_type  VARCHAR(20)   NOT NULL,
    exam_term_id     UUID          NULL,
    weight           NUMERIC(6,2)  NOT NULL,
    aggregation      VARCHAR(20)   NOT NULL DEFAULT 'AVERAGE',
    best_of          INT           NULL,
    source           VARCHAR(20)   NOT NULL DEFAULT 'ASSESSMENTS',
    pass_required    BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order       INT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_assessment_component PRIMARY KEY (id),
    CONSTRAINT uq_assessment_component_code UNIQUE (scheme_id, code),
    CONSTRAINT fk_assessment_component_scheme FOREIGN KEY (scheme_id) REFERENCES assessment_scheme(id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_component_term   FOREIGN KEY (exam_term_id) REFERENCES exam_term(id),
    CONSTRAINT chk_assessment_component_type CHECK (assessment_type IN ('FORMATIVE', 'SUMMATIVE')),
    CONSTRAINT chk_assessment_component_agg  CHECK (aggregation IN ('SUM', 'AVERAGE', 'BEST_OF', 'LATEST', 'MAX')),
    CONSTRAINT chk_assessment_component_src  CHECK (source IN ('EXAM_PAPER', 'ASSESSMENTS')),
    CONSTRAINT chk_assessment_component_weight CHECK (weight > 0),
    CONSTRAINT chk_assessment_component_best CHECK ((aggregation = 'BEST_OF') = (best_of IS NOT NULL) AND (best_of IS NULL OR best_of > 0))
);

COMMENT ON COLUMN assessment_component.exam_term_id IS 'NULL = the component is part of every term of the year';
COMMENT ON COLUMN assessment_component.source IS 'EXAM_PAPER = marks of the term''s exam paper for the subject; ASSESSMENTS = assessments recorded under this component';

CREATE TABLE co_scholastic_area (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    scheme_id   UUID          NOT NULL,
    code        VARCHAR(30)   NOT NULL,
    name        VARCHAR(150)  NOT NULL,
    area_group  VARCHAR(30)   NOT NULL DEFAULT 'CO_SCHOLASTIC',
    description VARCHAR(500)  NULL,
    sort_order  INT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_co_scholastic_area PRIMARY KEY (id),
    CONSTRAINT uq_co_scholastic_area_code UNIQUE (scheme_id, code),
    CONSTRAINT fk_co_scholastic_area_scheme FOREIGN KEY (scheme_id) REFERENCES assessment_scheme(id) ON DELETE CASCADE,
    CONSTRAINT chk_co_scholastic_area_group CHECK (area_group IN ('CO_SCHOLASTIC', 'DISCIPLINE', 'LIFE_SKILLS', 'VALUES', 'OTHER'))
);

CREATE TABLE co_scholastic_grade (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    area_id             UUID          NOT NULL,
    exam_term_id        UUID          NOT NULL,
    student_id          UUID          NOT NULL,
    grade               VARCHAR(10)   NOT NULL,
    remarks             VARCHAR(500)  NULL,
    graded_by_staff_id  UUID          NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          UUID          NULL,
    updated_by          UUID          NULL,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_co_scholastic_grade PRIMARY KEY (id),
    CONSTRAINT uq_co_scholastic_grade UNIQUE (area_id, exam_term_id, student_id),
    CONSTRAINT fk_csg_area    FOREIGN KEY (area_id) REFERENCES co_scholastic_area(id) ON DELETE CASCADE,
    CONSTRAINT fk_csg_term    FOREIGN KEY (exam_term_id) REFERENCES exam_term(id),
    CONSTRAINT fk_csg_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_csg_staff   FOREIGN KEY (graded_by_staff_id) REFERENCES staff(id) ON DELETE SET NULL
);

-- Competencies (subject competencies and cross-cutting skills) and the learning outcomes under them.
CREATE TABLE competency (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(50)   NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    description TEXT          NULL,
    category    VARCHAR(30)   NOT NULL DEFAULT 'SUBJECT',
    subject_id  UUID          NULL,
    board       VARCHAR(20)   NULL,
    parent_id   UUID          NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    deleted_at  TIMESTAMPTZ   NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by  UUID          NULL,
    updated_by  UUID          NULL,
    version     BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_competency PRIMARY KEY (id),
    CONSTRAINT fk_competency_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_competency_parent  FOREIGN KEY (parent_id) REFERENCES competency(id),
    CONSTRAINT chk_competency_category CHECK (category IN ('SUBJECT', 'SKILL', 'SOCIO_EMOTIONAL', 'PHYSICAL', 'CREATIVE', 'DIGITAL', 'OTHER')),
    CONSTRAINT chk_competency_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);
CREATE UNIQUE INDEX uq_competency_code ON competency (lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE learning_outcome (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    code               VARCHAR(50)   NOT NULL,
    competency_id      UUID          NULL,
    subject_id         UUID          NOT NULL,
    academic_class_id  UUID          NULL,
    statement          TEXT          NOT NULL,
    bloom_level        VARCHAR(20)   NULL,
    board              VARCHAR(20)   NULL,
    sort_order         INT           NOT NULL DEFAULT 0,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    deleted_at         TIMESTAMPTZ   NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID          NULL,
    updated_by         UUID          NULL,
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_learning_outcome PRIMARY KEY (id),
    CONSTRAINT fk_learning_outcome_competency FOREIGN KEY (competency_id) REFERENCES competency(id),
    CONSTRAINT fk_learning_outcome_subject    FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_learning_outcome_class      FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT chk_learning_outcome_bloom CHECK (bloom_level IS NULL OR bloom_level IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYSE', 'EVALUATE', 'CREATE')),
    CONSTRAINT chk_learning_outcome_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);
CREATE UNIQUE INDEX uq_learning_outcome_code ON learning_outcome (lower(code)) WHERE deleted_at IS NULL;
CREATE INDEX idx_learning_outcome_subject ON learning_outcome (subject_id, academic_class_id) WHERE deleted_at IS NULL;

CREATE TABLE syllabus_topic_outcome (
    syllabus_topic_id    UUID NOT NULL,
    learning_outcome_id  UUID NOT NULL,
    CONSTRAINT pk_syllabus_topic_outcome PRIMARY KEY (syllabus_topic_id, learning_outcome_id),
    CONSTRAINT fk_sto_topic   FOREIGN KEY (syllabus_topic_id) REFERENCES syllabus_topic(id) ON DELETE CASCADE,
    CONSTRAINT fk_sto_outcome FOREIGN KEY (learning_outcome_id) REFERENCES learning_outcome(id) ON DELETE CASCADE
);

-- A teacher's judgement of a student on a competency or a learning outcome in a term (competency-based assessment).
CREATE TABLE competency_rating (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    student_id           UUID           NOT NULL,
    exam_term_id         UUID           NOT NULL,
    competency_id        UUID           NULL,
    learning_outcome_id  UUID           NULL,
    level                VARCHAR(30)    NOT NULL,
    evidence             VARCHAR(1000)  NULL,
    rated_by_staff_id    UUID           NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by           UUID           NULL,
    updated_by           UUID           NULL,
    version              BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_competency_rating PRIMARY KEY (id),
    CONSTRAINT fk_cr_student    FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_cr_term       FOREIGN KEY (exam_term_id) REFERENCES exam_term(id),
    CONSTRAINT fk_cr_competency FOREIGN KEY (competency_id) REFERENCES competency(id),
    CONSTRAINT fk_cr_outcome    FOREIGN KEY (learning_outcome_id) REFERENCES learning_outcome(id),
    CONSTRAINT fk_cr_staff      FOREIGN KEY (rated_by_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_cr_target CHECK ((competency_id IS NULL) <> (learning_outcome_id IS NULL))
);
CREATE UNIQUE INDEX uq_competency_rating_competency ON competency_rating (student_id, exam_term_id, competency_id) WHERE competency_id IS NOT NULL;
CREATE UNIQUE INDEX uq_competency_rating_outcome    ON competency_rating (student_id, exam_term_id, learning_outcome_id) WHERE learning_outcome_id IS NOT NULL;

-- Formative / summative assessments recorded under a scheme component (class tests, projects, quizzes, orals ...).
CREATE TABLE assessment (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    assessment_code    VARCHAR(50)   NOT NULL,
    component_id       UUID          NOT NULL,
    exam_term_id       UUID          NOT NULL,
    academic_year_id   UUID          NOT NULL,
    academic_class_id  UUID          NOT NULL,
    section_id         UUID          NULL,
    subject_id         UUID          NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    assessment_date    DATE          NOT NULL,
    max_marks          NUMERIC(6,2)  NOT NULL,
    mode               VARCHAR(20)   NOT NULL DEFAULT 'OFFLINE',
    status             VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    deleted_at         TIMESTAMPTZ   NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID          NULL,
    updated_by         UUID          NULL,
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_assessment PRIMARY KEY (id),
    CONSTRAINT uq_assessment_code UNIQUE (assessment_code),
    CONSTRAINT fk_assessment_component FOREIGN KEY (component_id) REFERENCES assessment_component(id),
    CONSTRAINT fk_assessment_term      FOREIGN KEY (exam_term_id) REFERENCES exam_term(id),
    CONSTRAINT fk_assessment_year      FOREIGN KEY (academic_year_id) REFERENCES academic_year(id),
    CONSTRAINT fk_assessment_class     FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT fk_assessment_section   FOREIGN KEY (section_id) REFERENCES section(id),
    CONSTRAINT fk_assessment_subject   FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT chk_assessment_max  CHECK (max_marks > 0),
    CONSTRAINT chk_assessment_mode CHECK (mode IN ('OFFLINE', 'ONLINE', 'OMR', 'PROJECT', 'ORAL', 'PRACTICAL', 'OBSERVATION')),
    CONSTRAINT chk_assessment_status CHECK (status IN ('OPEN', 'LOCKED'))
);
CREATE INDEX idx_assessment_term_class ON assessment (exam_term_id, academic_class_id, subject_id) WHERE deleted_at IS NULL;

CREATE TABLE assessment_score (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    assessment_id          UUID          NOT NULL,
    student_id             UUID          NOT NULL,
    marks                  NUMERIC(6,2)  NULL,
    is_absent              BOOLEAN       NOT NULL DEFAULT FALSE,
    is_exempt              BOOLEAN       NOT NULL DEFAULT FALSE,
    remarks                VARCHAR(255)  NULL,
    evaluated_by_staff_id  UUID          NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by             UUID          NULL,
    updated_by             UUID          NULL,
    version                BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_assessment_score PRIMARY KEY (id),
    CONSTRAINT uq_assessment_score UNIQUE (assessment_id, student_id),
    CONSTRAINT fk_ascore_assessment FOREIGN KEY (assessment_id) REFERENCES assessment(id) ON DELETE CASCADE,
    CONSTRAINT fk_ascore_student    FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_ascore_staff      FOREIGN KEY (evaluated_by_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_ascore_marks CHECK (marks IS NULL OR marks >= 0)
);

-- ═════════════════════════════ 2. Results: grace, moderation, re-evaluation ═════════════════════════════

CREATE TABLE moderation_batch (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    batch_code              VARCHAR(50)   NOT NULL,
    exam_subject_config_id  UUID          NOT NULL,
    method                  VARCHAR(30)   NOT NULL,
    value                   NUMERIC(8,3)  NOT NULL,
    only_below              NUMERIC(6,2)  NULL,
    reason                  VARCHAR(500)  NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'APPLIED',
    students_affected       INT           NOT NULL DEFAULT 0,
    applied_by              UUID          NULL,
    applied_at              TIMESTAMPTZ   NULL,
    reverted_by             UUID          NULL,
    reverted_at             TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_moderation_batch PRIMARY KEY (id),
    CONSTRAINT uq_moderation_batch_code UNIQUE (batch_code),
    CONSTRAINT fk_moderation_batch_config FOREIGN KEY (exam_subject_config_id) REFERENCES exam_subject_config(id),
    CONSTRAINT chk_moderation_batch_method CHECK (method IN ('ADD_FLAT', 'ADD_PERCENT_OF_MAX', 'SCALE_FACTOR', 'RAISE_TO_PASS_WITHIN')),
    CONSTRAINT chk_moderation_batch_status CHECK (status IN ('APPLIED', 'REVERTED'))
);

CREATE TABLE reevaluation_request (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    request_code            VARCHAR(50)   NOT NULL,
    exam_subject_config_id  UUID          NOT NULL,
    student_id              UUID          NOT NULL,
    requested_by            UUID          NULL,
    reason                  VARCHAR(1000) NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED',
    original_marks          NUMERIC(6,2)  NULL,
    revised_marks           NUMERIC(6,2)  NULL,
    reviewer_staff_id       UUID          NULL,
    reviewer_note           VARCHAR(1000) NULL,
    decided_at              TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_reevaluation_request PRIMARY KEY (id),
    CONSTRAINT uq_reevaluation_request_code UNIQUE (request_code),
    CONSTRAINT fk_rr_config  FOREIGN KEY (exam_subject_config_id) REFERENCES exam_subject_config(id),
    CONSTRAINT fk_rr_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_rr_staff   FOREIGN KEY (reviewer_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_rr_status CHECK (status IN ('REQUESTED', 'UNDER_REVIEW', 'REVISED', 'UNCHANGED', 'REJECTED', 'WITHDRAWN'))
);
CREATE UNIQUE INDEX uq_reevaluation_open ON reevaluation_request (exam_subject_config_id, student_id)
    WHERE status IN ('REQUESTED', 'UNDER_REVIEW');

-- Every change to a mark after entry. Results use raw + APPROVED adjustments.
CREATE TABLE mark_adjustment (
    id                       UUID          NOT NULL DEFAULT gen_random_uuid(),
    exam_subject_config_id   UUID          NULL,
    assessment_id            UUID          NULL,
    student_id               UUID          NOT NULL,
    adjustment_type          VARCHAR(20)   NOT NULL,
    delta                    NUMERIC(6,2)  NOT NULL,
    reason                   VARCHAR(500)  NOT NULL,
    moderation_batch_id      UUID          NULL,
    reevaluation_request_id  UUID          NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'PROPOSED',
    proposed_by              UUID          NULL,
    decided_by               UUID          NULL,
    decided_at               TIMESTAMPTZ   NULL,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                  BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_mark_adjustment PRIMARY KEY (id),
    CONSTRAINT fk_ma_config     FOREIGN KEY (exam_subject_config_id) REFERENCES exam_subject_config(id),
    CONSTRAINT fk_ma_assessment FOREIGN KEY (assessment_id) REFERENCES assessment(id) ON DELETE CASCADE,
    CONSTRAINT fk_ma_student    FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_ma_batch      FOREIGN KEY (moderation_batch_id) REFERENCES moderation_batch(id),
    CONSTRAINT fk_ma_reeval     FOREIGN KEY (reevaluation_request_id) REFERENCES reevaluation_request(id),
    CONSTRAINT chk_ma_target CHECK ((exam_subject_config_id IS NULL) <> (assessment_id IS NULL)),
    CONSTRAINT chk_ma_type   CHECK (adjustment_type IN ('GRACE', 'MODERATION', 'REEVALUATION', 'CORRECTION')),
    CONSTRAINT chk_ma_status CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'REVERTED'))
);
CREATE INDEX idx_mark_adjustment_config ON mark_adjustment (exam_subject_config_id, student_id) WHERE status = 'APPROVED';
CREATE INDEX idx_mark_adjustment_assessment ON mark_adjustment (assessment_id, student_id) WHERE status = 'APPROVED';

CREATE VIEW effective_exam_mark AS
SELECT m.id, m.exam_subject_config_id, m.student_id, m.status, m.is_absent, m.is_exempt,
       m.marks_obtained AS raw_marks, m.theory_marks_obtained, m.practical_marks_obtained,
       COALESCE(a.delta, 0) AS adjustment,
       CASE WHEN m.marks_obtained IS NULL THEN NULL
            ELSE LEAST(c.max_marks, GREATEST(0, m.marks_obtained + COALESCE(a.delta, 0))) END AS marks,
       c.max_marks, c.pass_marks
FROM student_mark_entry m
JOIN exam_subject_config c ON c.id = m.exam_subject_config_id AND c.deleted_at IS NULL
LEFT JOIN (SELECT exam_subject_config_id, student_id, SUM(delta) AS delta FROM mark_adjustment
           WHERE status = 'APPROVED' AND exam_subject_config_id IS NOT NULL GROUP BY exam_subject_config_id, student_id) a
       ON a.exam_subject_config_id = m.exam_subject_config_id AND a.student_id = m.student_id
WHERE m.deleted_at IS NULL;

-- Result details produced by the engine.
ALTER TABLE student_report_card
    ADD COLUMN assessment_scheme_id   UUID         NULL REFERENCES assessment_scheme(id) ON DELETE SET NULL,
    ADD COLUMN cumulative_percentage  NUMERIC(5,2) NULL,
    ADD COLUMN cumulative_grade       VARCHAR(10)  NULL,
    ADD COLUMN grace_applied          BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN result_note            VARCHAR(500) NULL;

ALTER TABLE report_card_subject_result
    ADD COLUMN category             VARCHAR(20)  NOT NULL DEFAULT 'SCHOLASTIC',
    ADD COLUMN grace_marks          NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN adjustment_marks     NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN counted_in_total     BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN component_breakdown  JSONB        NULL;

-- ═════════════════════════════ 3. Question bank & papers ═════════════════════════════

CREATE TABLE question (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    question_code          VARCHAR(50)   NOT NULL,
    subject_id             UUID          NOT NULL,
    academic_class_id      UUID          NULL,
    syllabus_unit_id       UUID          NULL,
    syllabus_topic_id      UUID          NULL,
    parent_id              UUID          NULL,
    question_type          VARCHAR(30)   NOT NULL,
    difficulty             VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    bloom_level            VARCHAR(20)   NULL,
    marks                  NUMERIC(5,2)  NOT NULL,
    negative_marks         NUMERIC(5,2)  NOT NULL DEFAULT 0,
    stem                   TEXT          NOT NULL,
    options                JSONB         NULL,
    answer                 JSONB         NULL,
    explanation            TEXT          NULL,
    expected_seconds       INT           NULL,
    language               VARCHAR(10)   NOT NULL DEFAULT 'en',
    board                  VARCHAR(20)   NULL,
    source                 VARCHAR(200)  NULL,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by            UUID          NULL,
    reviewed_at            TIMESTAMPTZ   NULL,
    review_note            VARCHAR(500)  NULL,
    times_used             INT           NOT NULL DEFAULT 0,
    last_used_at           TIMESTAMPTZ   NULL,
    facility_index         NUMERIC(5,4)  NULL,
    discrimination_index   NUMERIC(5,4)  NULL,
    responses_analysed     INT           NOT NULL DEFAULT 0,
    deleted_at             TIMESTAMPTZ   NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by             UUID          NULL,
    updated_by             UUID          NULL,
    version                BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_question PRIMARY KEY (id),
    CONSTRAINT uq_question_code UNIQUE (question_code),
    CONSTRAINT fk_question_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_question_class   FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT fk_question_unit    FOREIGN KEY (syllabus_unit_id) REFERENCES syllabus_unit(id) ON DELETE SET NULL,
    CONSTRAINT fk_question_topic   FOREIGN KEY (syllabus_topic_id) REFERENCES syllabus_topic(id) ON DELETE SET NULL,
    CONSTRAINT fk_question_parent  FOREIGN KEY (parent_id) REFERENCES question(id) ON DELETE CASCADE,
    CONSTRAINT chk_question_type CHECK (question_type IN ('MCQ_SINGLE', 'MCQ_MULTI', 'TRUE_FALSE', 'ASSERTION_REASON', 'FILL_BLANK',
        'NUMERICAL', 'MATCH_FOLLOWING', 'SHORT_ANSWER', 'LONG_ANSWER', 'CASE_BASED')),
    CONSTRAINT chk_question_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT chk_question_bloom CHECK (bloom_level IS NULL OR bloom_level IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYSE', 'EVALUATE', 'CREATE')),
    CONSTRAINT chk_question_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'RETIRED')),
    CONSTRAINT chk_question_marks CHECK (marks >= 0 AND negative_marks >= 0)
);
CREATE INDEX idx_question_pick ON question (subject_id, academic_class_id, question_type, difficulty) WHERE deleted_at IS NULL AND status = 'APPROVED';
CREATE INDEX idx_question_parent ON question (parent_id) WHERE parent_id IS NOT NULL;

CREATE TABLE question_outcome (
    question_id          UUID NOT NULL,
    learning_outcome_id  UUID NOT NULL,
    CONSTRAINT pk_question_outcome PRIMARY KEY (question_id, learning_outcome_id),
    CONSTRAINT fk_qo_question FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE,
    CONSTRAINT fk_qo_outcome  FOREIGN KEY (learning_outcome_id) REFERENCES learning_outcome(id) ON DELETE CASCADE
);
CREATE INDEX idx_question_outcome_outcome ON question_outcome (learning_outcome_id);

CREATE TABLE blueprint (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    blueprint_code     VARCHAR(50)   NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    subject_id         UUID          NOT NULL,
    academic_class_id  UUID          NOT NULL,
    board              VARCHAR(20)   NULL,
    total_marks        NUMERIC(6,2)  NOT NULL,
    duration_minutes   INT           NOT NULL,
    instructions       TEXT          NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    deleted_at         TIMESTAMPTZ   NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID          NULL,
    updated_by         UUID          NULL,
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_blueprint PRIMARY KEY (id),
    CONSTRAINT uq_blueprint_code UNIQUE (blueprint_code),
    CONSTRAINT fk_blueprint_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_blueprint_class   FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT chk_blueprint_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_blueprint_numbers CHECK (total_marks > 0 AND duration_minutes > 0)
);

CREATE TABLE blueprint_section (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    blueprint_id         UUID          NOT NULL,
    position             INT           NOT NULL,
    title                VARCHAR(100)  NOT NULL,
    instructions         VARCHAR(500)  NULL,
    question_type        VARCHAR(30)   NULL,
    marks_per_question   NUMERIC(5,2)  NOT NULL,
    question_count       INT           NOT NULL,
    attempt_count        INT           NULL,
    easy_count           INT           NOT NULL DEFAULT 0,
    medium_count         INT           NOT NULL DEFAULT 0,
    hard_count           INT           NOT NULL DEFAULT 0,
    bloom_levels         VARCHAR(200)  NULL,
    unit_ids             JSONB         NULL,
    outcome_ids          JSONB         NULL,

    CONSTRAINT pk_blueprint_section PRIMARY KEY (id),
    CONSTRAINT uq_blueprint_section_position UNIQUE (blueprint_id, position),
    CONSTRAINT fk_blueprint_section_blueprint FOREIGN KEY (blueprint_id) REFERENCES blueprint(id) ON DELETE CASCADE,
    CONSTRAINT chk_blueprint_section_counts CHECK (question_count > 0 AND marks_per_question > 0
        AND (attempt_count IS NULL OR attempt_count BETWEEN 1 AND question_count)
        AND easy_count >= 0 AND medium_count >= 0 AND hard_count >= 0
        AND easy_count + medium_count + hard_count <= question_count)
);

CREATE TABLE question_paper (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    paper_code              VARCHAR(50)   NOT NULL,
    title                   VARCHAR(200)  NOT NULL,
    subject_id              UUID          NOT NULL,
    academic_class_id       UUID          NOT NULL,
    blueprint_id            UUID          NULL,
    exam_subject_config_id  UUID          NULL,
    assessment_id           UUID          NULL,
    set_label               VARCHAR(5)    NOT NULL DEFAULT 'A',
    parent_paper_id         UUID          NULL,
    total_marks             NUMERIC(6,2)  NOT NULL DEFAULT 0,
    duration_minutes        INT           NULL,
    instructions            TEXT          NULL,
    generation_seed         BIGINT        NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    finalised_at            TIMESTAMPTZ   NULL,
    deleted_at              TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by              UUID          NULL,
    updated_by              UUID          NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_question_paper PRIMARY KEY (id),
    CONSTRAINT uq_question_paper_code UNIQUE (paper_code),
    CONSTRAINT fk_qp_subject    FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_qp_class      FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT fk_qp_blueprint  FOREIGN KEY (blueprint_id) REFERENCES blueprint(id) ON DELETE SET NULL,
    CONSTRAINT fk_qp_config     FOREIGN KEY (exam_subject_config_id) REFERENCES exam_subject_config(id) ON DELETE SET NULL,
    CONSTRAINT fk_qp_assessment FOREIGN KEY (assessment_id) REFERENCES assessment(id) ON DELETE SET NULL,
    CONSTRAINT fk_qp_parent     FOREIGN KEY (parent_paper_id) REFERENCES question_paper(id) ON DELETE CASCADE,
    CONSTRAINT chk_qp_status CHECK (status IN ('DRAFT', 'FINALISED', 'ARCHIVED'))
);

CREATE TABLE question_paper_item (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    paper_id          UUID          NOT NULL,
    section_position  INT           NOT NULL,
    section_title     VARCHAR(100)  NOT NULL,
    section_attempt   INT           NULL,
    position          INT           NOT NULL,
    question_id       UUID          NOT NULL,
    parent_item_id    UUID          NULL,
    marks             NUMERIC(5,2)  NOT NULL,
    negative_marks    NUMERIC(5,2)  NOT NULL DEFAULT 0,

    CONSTRAINT pk_question_paper_item PRIMARY KEY (id),
    CONSTRAINT uq_question_paper_item_position UNIQUE (paper_id, position),
    CONSTRAINT fk_qpi_paper    FOREIGN KEY (paper_id) REFERENCES question_paper(id) ON DELETE CASCADE,
    CONSTRAINT fk_qpi_question FOREIGN KEY (question_id) REFERENCES question(id),
    CONSTRAINT fk_qpi_parent   FOREIGN KEY (parent_item_id) REFERENCES question_paper_item(id) ON DELETE CASCADE
);
CREATE INDEX idx_question_paper_item_question ON question_paper_item (question_id);

-- ═════════════════════════════ 4. Delivery: online exams, OMR, question-level scores ═════════════════════════════

CREATE TABLE online_exam (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    exam_code               VARCHAR(50)   NOT NULL,
    title                   VARCHAR(200)  NOT NULL,
    question_paper_id       UUID          NOT NULL,
    exam_subject_config_id  UUID          NULL,
    assessment_id           UUID          NULL,
    academic_class_id       UUID          NOT NULL,
    section_id              UUID          NULL,
    opens_at                TIMESTAMPTZ   NOT NULL,
    closes_at               TIMESTAMPTZ   NOT NULL,
    duration_minutes        INT           NOT NULL,
    shuffle_questions       BOOLEAN       NOT NULL DEFAULT TRUE,
    shuffle_options         BOOLEAN       NOT NULL DEFAULT TRUE,
    negative_marking        BOOLEAN       NOT NULL DEFAULT FALSE,
    max_attempts            INT           NOT NULL DEFAULT 1,
    result_visibility       VARCHAR(20)   NOT NULL DEFAULT 'AFTER_PUBLISH',
    instructions            TEXT          NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    results_published_at    TIMESTAMPTZ   NULL,
    marks_posted_at         TIMESTAMPTZ   NULL,
    deleted_at              TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by              UUID          NULL,
    updated_by              UUID          NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_online_exam PRIMARY KEY (id),
    CONSTRAINT uq_online_exam_code UNIQUE (exam_code),
    CONSTRAINT fk_oe_paper      FOREIGN KEY (question_paper_id) REFERENCES question_paper(id),
    CONSTRAINT fk_oe_config     FOREIGN KEY (exam_subject_config_id) REFERENCES exam_subject_config(id),
    CONSTRAINT fk_oe_assessment FOREIGN KEY (assessment_id) REFERENCES assessment(id),
    CONSTRAINT fk_oe_class      FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT fk_oe_section    FOREIGN KEY (section_id) REFERENCES section(id),
    CONSTRAINT chk_oe_window CHECK (closes_at > opens_at AND duration_minutes > 0 AND max_attempts BETWEEN 1 AND 10),
    CONSTRAINT chk_oe_target CHECK (exam_subject_config_id IS NULL OR assessment_id IS NULL),
    CONSTRAINT chk_oe_visibility CHECK (result_visibility IN ('IMMEDIATE', 'AFTER_CLOSE', 'AFTER_PUBLISH')),
    CONSTRAINT chk_oe_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'CANCELLED'))
);

CREATE TABLE online_exam_attempt (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    exam_id         UUID          NOT NULL,
    student_id      UUID          NOT NULL,
    attempt_no      INT           NOT NULL,
    started_at      TIMESTAMPTZ   NOT NULL,
    deadline_at     TIMESTAMPTZ   NOT NULL,
    submitted_at    TIMESTAMPTZ   NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS',
    item_order      JSONB         NOT NULL,
    option_order    JSONB         NULL,
    auto_score      NUMERIC(6,2)  NULL,
    manual_score    NUMERIC(6,2)  NULL,
    total_score     NUMERIC(6,2)  NULL,
    max_score       NUMERIC(6,2)  NOT NULL,
    pending_manual  INT           NOT NULL DEFAULT 0,
    client_info     VARCHAR(255)  NULL,
    version         BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_online_exam_attempt PRIMARY KEY (id),
    CONSTRAINT uq_online_exam_attempt UNIQUE (exam_id, student_id, attempt_no),
    CONSTRAINT fk_oea_exam    FOREIGN KEY (exam_id) REFERENCES online_exam(id) ON DELETE CASCADE,
    CONSTRAINT fk_oea_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT chk_oea_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED', 'GRADED'))
);
CREATE INDEX idx_online_exam_attempt_open ON online_exam_attempt (deadline_at) WHERE status = 'IN_PROGRESS';

CREATE TABLE online_exam_response (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    attempt_id          UUID          NOT NULL,
    paper_item_id       UUID          NOT NULL,
    question_id         UUID          NOT NULL,
    answer              JSONB         NULL,
    answered_at         TIMESTAMPTZ   NULL,
    is_correct          BOOLEAN       NULL,
    marks_awarded       NUMERIC(6,2)  NULL,
    needs_manual        BOOLEAN       NOT NULL DEFAULT FALSE,
    graded_by_staff_id  UUID          NULL,
    graded_at           TIMESTAMPTZ   NULL,
    feedback            VARCHAR(1000) NULL,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_online_exam_response PRIMARY KEY (id),
    CONSTRAINT uq_online_exam_response UNIQUE (attempt_id, paper_item_id),
    CONSTRAINT fk_oer_attempt  FOREIGN KEY (attempt_id) REFERENCES online_exam_attempt(id) ON DELETE CASCADE,
    CONSTRAINT fk_oer_item     FOREIGN KEY (paper_item_id) REFERENCES question_paper_item(id),
    CONSTRAINT fk_oer_question FOREIGN KEY (question_id) REFERENCES question(id),
    CONSTRAINT fk_oer_staff    FOREIGN KEY (graded_by_staff_id) REFERENCES staff(id) ON DELETE SET NULL
);
CREATE INDEX idx_online_exam_response_manual ON online_exam_response (attempt_id) WHERE needs_manual AND graded_at IS NULL;

CREATE TABLE omr_batch (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    batch_code              VARCHAR(50)   NOT NULL,
    question_paper_id       UUID          NOT NULL,
    exam_subject_config_id  UUID          NULL,
    assessment_id           UUID          NULL,
    section_id              UUID          NULL,
    source                  VARCHAR(100)  NULL,
    negative_marking        BOOLEAN       NOT NULL DEFAULT FALSE,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'EVALUATED',
    sheets_total            INT           NOT NULL DEFAULT 0,
    sheets_matched          INT           NOT NULL DEFAULT 0,
    sheets_error            INT           NOT NULL DEFAULT 0,
    posted_at               TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by              UUID          NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_omr_batch PRIMARY KEY (id),
    CONSTRAINT uq_omr_batch_code UNIQUE (batch_code),
    CONSTRAINT fk_omr_batch_paper      FOREIGN KEY (question_paper_id) REFERENCES question_paper(id),
    CONSTRAINT fk_omr_batch_config     FOREIGN KEY (exam_subject_config_id) REFERENCES exam_subject_config(id),
    CONSTRAINT fk_omr_batch_assessment FOREIGN KEY (assessment_id) REFERENCES assessment(id),
    CONSTRAINT fk_omr_batch_section    FOREIGN KEY (section_id) REFERENCES section(id),
    CONSTRAINT chk_omr_batch_target CHECK (exam_subject_config_id IS NULL OR assessment_id IS NULL),
    CONSTRAINT chk_omr_batch_status CHECK (status IN ('EVALUATED', 'POSTED'))
);

CREATE TABLE omr_sheet (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    batch_id      UUID          NOT NULL,
    row_no        INT           NOT NULL,
    roll_number   VARCHAR(50)   NULL,
    student_code  VARCHAR(50)   NULL,
    student_id    UUID          NULL,
    set_label     VARCHAR(5)    NULL,
    raw_answers   TEXT          NOT NULL,
    score         NUMERIC(6,2)  NULL,
    correct       INT           NOT NULL DEFAULT 0,
    wrong         INT           NOT NULL DEFAULT 0,
    blank         INT           NOT NULL DEFAULT 0,
    invalid       INT           NOT NULL DEFAULT 0,
    error         VARCHAR(255)  NULL,

    CONSTRAINT pk_omr_sheet PRIMARY KEY (id),
    CONSTRAINT uq_omr_sheet_row UNIQUE (batch_id, row_no),
    CONSTRAINT fk_omr_sheet_batch   FOREIGN KEY (batch_id) REFERENCES omr_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_omr_sheet_student FOREIGN KEY (student_id) REFERENCES student(id)
);

-- Question-level marks from any source: the basis of outcome attainment and question calibration.
CREATE TABLE question_score (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    student_id         UUID          NOT NULL,
    question_id        UUID          NOT NULL,
    question_paper_id  UUID          NOT NULL,
    exam_term_id       UUID          NULL,
    source             VARCHAR(20)   NOT NULL,
    marks_awarded      NUMERIC(6,2)  NOT NULL,
    max_marks          NUMERIC(6,2)  NOT NULL,
    assessed_on        DATE          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_question_score PRIMARY KEY (id),
    CONSTRAINT uq_question_score UNIQUE (student_id, question_id, question_paper_id),
    CONSTRAINT fk_qs_student  FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_qs_question FOREIGN KEY (question_id) REFERENCES question(id),
    CONSTRAINT fk_qs_paper    FOREIGN KEY (question_paper_id) REFERENCES question_paper(id),
    CONSTRAINT fk_qs_term     FOREIGN KEY (exam_term_id) REFERENCES exam_term(id),
    CONSTRAINT chk_qs_source CHECK (source IN ('ONLINE', 'OMR', 'MANUAL'))
);
CREATE INDEX idx_question_score_question ON question_score (question_id);
CREATE INDEX idx_question_score_student  ON question_score (student_id, exam_term_id);
