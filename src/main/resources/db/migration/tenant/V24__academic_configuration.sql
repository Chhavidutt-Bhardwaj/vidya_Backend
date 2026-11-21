-- TENANT V24: academic configuration — syllabus (units, topics, coverage per section) and lesson plans.
-- Subjects, class subjects, class teachers and subject teachers already exist (V7, V8, V11).

-- One syllabus per class × subject × academic year.
CREATE TABLE syllabus (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    syllabus_code      VARCHAR(50)   NOT NULL,
    academic_year_id   UUID          NOT NULL,
    academic_class_id  UUID          NOT NULL,
    subject_id         UUID          NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    description        TEXT          NULL,
    reference_books    VARCHAR(1000) NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    deleted_at         TIMESTAMPTZ   NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID          NULL,
    updated_by         UUID          NULL,
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_syllabus PRIMARY KEY (id),
    CONSTRAINT uq_syllabus_code UNIQUE (syllabus_code),
    CONSTRAINT fk_syllabus_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_syllabus_class FOREIGN KEY (academic_class_id) REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_syllabus_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT chk_syllabus_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
CREATE UNIQUE INDEX uq_syllabus_year_class_subject ON syllabus (academic_year_id, academic_class_id, subject_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_syllabus_subject ON syllabus(subject_id);

COMMENT ON TABLE syllabus IS 'What a class studies in a subject in one academic year: units and topics. Teachers see it once PUBLISHED';

CREATE TABLE syllabus_unit (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    syllabus_id        UUID         NOT NULL,
    unit_no            INT          NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT         NULL,
    learning_outcomes  TEXT         NULL,
    exam_term_id       UUID         NULL,
    planned_periods    INT          NULL,
    planned_start      DATE         NULL,
    planned_end        DATE         NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_syllabus_unit PRIMARY KEY (id),
    CONSTRAINT fk_syllabus_unit_syllabus FOREIGN KEY (syllabus_id) REFERENCES syllabus(id) ON DELETE CASCADE,
    CONSTRAINT fk_syllabus_unit_term FOREIGN KEY (exam_term_id) REFERENCES exam_term(id) ON DELETE SET NULL,
    CONSTRAINT chk_syllabus_unit_periods CHECK (planned_periods IS NULL OR planned_periods >= 0),
    CONSTRAINT chk_syllabus_unit_dates CHECK (planned_start IS NULL OR planned_end IS NULL OR planned_end >= planned_start)
);
CREATE INDEX idx_syllabus_unit_syllabus ON syllabus_unit(syllabus_id, unit_no);

COMMENT ON COLUMN syllabus_unit.exam_term_id IS 'Term in which the unit is taught and examined';

CREATE TABLE syllabus_topic (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    syllabus_id      UUID         NOT NULL,
    unit_id          UUID         NOT NULL,
    title            VARCHAR(300) NOT NULL,
    planned_periods  INT          NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_syllabus_topic PRIMARY KEY (id),
    CONSTRAINT fk_syllabus_topic_syllabus FOREIGN KEY (syllabus_id) REFERENCES syllabus(id) ON DELETE CASCADE,
    CONSTRAINT fk_syllabus_topic_unit FOREIGN KEY (unit_id) REFERENCES syllabus_unit(id) ON DELETE CASCADE,
    CONSTRAINT chk_syllabus_topic_periods CHECK (planned_periods IS NULL OR planned_periods >= 0)
);
CREATE INDEX idx_syllabus_topic_unit ON syllabus_topic(unit_id, sort_order);
CREATE INDEX idx_syllabus_topic_syllabus ON syllabus_topic(syllabus_id);

-- Coverage: how far each section is. No row = not started.
CREATE TABLE syllabus_topic_progress (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    topic_id      UUID         NOT NULL,
    section_id    UUID         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    completed_on  DATE         NULL,
    remarks       VARCHAR(255) NULL,
    updated_by    UUID         NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_syllabus_topic_progress PRIMARY KEY (id),
    CONSTRAINT uq_syllabus_topic_progress UNIQUE (topic_id, section_id),
    CONSTRAINT fk_syllabus_progress_topic FOREIGN KEY (topic_id) REFERENCES syllabus_topic(id) ON DELETE CASCADE,
    CONSTRAINT fk_syllabus_progress_section FOREIGN KEY (section_id) REFERENCES section(id) ON DELETE CASCADE,
    CONSTRAINT chk_syllabus_progress_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);
CREATE INDEX idx_syllabus_progress_section ON syllabus_topic_progress(section_id);

COMMENT ON TABLE syllabus_topic_progress IS 'Syllabus coverage per section; a topic without a row has not been started in that section';

-- A teacher's plan for one lesson (or a few periods) of a subject in a class / section.
CREATE TABLE lesson_plan (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    plan_code          VARCHAR(50)  NOT NULL,
    academic_year_id   UUID         NOT NULL,
    academic_class_id  UUID         NOT NULL,
    section_id         UUID         NULL,
    subject_id         UUID         NOT NULL,
    teacher_id         UUID         NOT NULL,
    syllabus_unit_id   UUID         NULL,
    syllabus_topic_id  UUID         NULL,
    title              VARCHAR(200) NOT NULL,
    plan_date          DATE         NOT NULL,
    periods            INT          NOT NULL DEFAULT 1,
    objectives         TEXT         NULL,
    teaching_method    TEXT         NULL,
    resources          TEXT         NULL,
    homework           TEXT         NULL,
    assessment         TEXT         NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    submitted_at       TIMESTAMPTZ  NULL,
    reviewed_by        UUID         NULL,
    reviewed_at        TIMESTAMPTZ  NULL,
    review_remarks     VARCHAR(500) NULL,
    delivered_on       DATE         NULL,
    reflection         TEXT         NULL,
    deleted_at         TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID         NULL,
    updated_by         UUID         NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_lesson_plan PRIMARY KEY (id),
    CONSTRAINT uq_lesson_plan_code UNIQUE (plan_code),
    CONSTRAINT fk_lesson_plan_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lesson_plan_class FOREIGN KEY (academic_class_id) REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lesson_plan_section FOREIGN KEY (section_id) REFERENCES section(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lesson_plan_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lesson_plan_teacher FOREIGN KEY (teacher_id) REFERENCES staff(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lesson_plan_unit FOREIGN KEY (syllabus_unit_id) REFERENCES syllabus_unit(id) ON DELETE SET NULL,
    CONSTRAINT fk_lesson_plan_topic FOREIGN KEY (syllabus_topic_id) REFERENCES syllabus_topic(id) ON DELETE SET NULL,
    CONSTRAINT chk_lesson_plan_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'RETURNED', 'DELIVERED')),
    CONSTRAINT chk_lesson_plan_periods CHECK (periods BETWEEN 1 AND 20)
);
CREATE INDEX idx_lesson_plan_teacher ON lesson_plan(teacher_id, plan_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_lesson_plan_class ON lesson_plan(academic_year_id, academic_class_id, subject_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_lesson_plan_status ON lesson_plan(academic_year_id, status) WHERE deleted_at IS NULL;

COMMENT ON TABLE lesson_plan IS 'Lesson plans written by teachers; section_id NULL = the same plan for every section of the class the teacher teaches';
COMMENT ON COLUMN lesson_plan.reviewed_by IS 'Login (platform user id) of the reviewer who approved or returned the plan';
