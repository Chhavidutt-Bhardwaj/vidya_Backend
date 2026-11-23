-- ============================================================
-- TENANT V20: Academic structure configuration
--   * academic_class: stage (pre-primary … senior secondary), numeric grade level,
--     whether the class is split into streams, and its grading scale
--   * stream: Science / Commerce / Humanities … (senior secondary)
--   * section.stream_id: 11-A is a Science section
--   * curriculum_subject.stream_id: Physics only for the Science stream (NULL = every stream)
-- Grade resolution for marks: exam schedule scale → class scale → school default scale.
-- ============================================================

CREATE TABLE stream (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(255) NULL,
    sort_order          INT          NOT NULL DEFAULT 0,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_stream PRIMARY KEY (id),
    CONSTRAINT chk_stream_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE UNIQUE INDEX uq_stream_code_active ON stream (upper(code)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_stream_name_active ON stream (lower(name)) WHERE deleted_at IS NULL;

COMMENT ON TABLE stream IS 'Senior-secondary streams (Science, Commerce, Humanities …) used by sections and curriculum subjects';

ALTER TABLE academic_class
    ADD COLUMN stage            VARCHAR(30) NULL,
    ADD COLUMN grade_level      INT         NULL,
    ADD COLUMN has_streams      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN grading_scale_id UUID        NULL,
    ADD CONSTRAINT chk_academic_class_stage CHECK (stage IS NULL OR stage IN
        ('PRE_PRIMARY', 'PRIMARY', 'MIDDLE', 'SECONDARY', 'SENIOR_SECONDARY')),
    ADD CONSTRAINT chk_academic_class_grade_level CHECK (grade_level IS NULL OR grade_level BETWEEN -3 AND 12),
    ADD CONSTRAINT fk_academic_class_grading_scale FOREIGN KEY (grading_scale_id)
        REFERENCES grading_scale(id) ON DELETE SET NULL;

CREATE INDEX idx_academic_class_stage ON academic_class(stage);

COMMENT ON COLUMN academic_class.grade_level IS 'Numeric level: Pre-Nursery -3, Nursery -2, LKG -1, UKG 0, Class I 1 … Class XII 12';
COMMENT ON COLUMN academic_class.has_streams IS 'Sections of this class belong to a stream (classes XI–XII)';
COMMENT ON COLUMN academic_class.grading_scale_id IS 'Scale for this class when the exam schedule has none; NULL = school default';

ALTER TABLE section
    ADD COLUMN stream_id UUID NULL,
    ADD CONSTRAINT fk_section_stream FOREIGN KEY (stream_id) REFERENCES stream(id) ON DELETE RESTRICT;
CREATE INDEX idx_section_stream ON section(stream_id);

ALTER TABLE curriculum_subject
    ADD COLUMN stream_id UUID NULL,
    ADD CONSTRAINT fk_curriculum_subject_stream FOREIGN KEY (stream_id) REFERENCES stream(id) ON DELETE RESTRICT;

-- The same subject may now appear once per stream of a class (e.g. Mathematics for Science and Commerce).
DROP INDEX IF EXISTS idx_curriculum_subject_unique_active;
CREATE UNIQUE INDEX idx_curriculum_subject_unique_active
    ON curriculum_subject (curriculum_id, subject_id, academic_class_id,
                           COALESCE(stream_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE deleted_at IS NULL;
CREATE INDEX idx_curriculum_subject_stream ON curriculum_subject(stream_id);

COMMENT ON COLUMN curriculum_subject.stream_id IS 'Only for sections of this stream; NULL = every section of the class';
