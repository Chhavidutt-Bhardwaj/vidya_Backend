-- ============================================================
-- TENANT V11: Class Teacher / Section Teacher Assignment
-- Designating staff members as class teachers for sections in an academic year
-- ============================================================

CREATE TABLE class_teacher_assignment (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    assignment_code     VARCHAR(50)   NOT NULL,
    staff_id            UUID          NOT NULL,
    academic_year_id    UUID          NOT NULL,
    academic_class_id   UUID          NOT NULL,
    section_id          UUID          NOT NULL,
    start_date          DATE          NULL,
    end_date            DATE          NULL,
    notes               VARCHAR(1000) NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          UUID          NULL,
    updated_by          UUID          NULL,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_class_teacher_assignment PRIMARY KEY (id),
    CONSTRAINT uq_class_teacher_assignment_code UNIQUE (assignment_code),
    CONSTRAINT fk_cta_staff FOREIGN KEY (staff_id)
        REFERENCES staff(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cta_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cta_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cta_section FOREIGN KEY (section_id)
        REFERENCES section(id) ON DELETE RESTRICT,
    CONSTRAINT chk_cta_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'COMPLETED', 'CANCELLED', 'DELETED'))
);

-- Partial unique index to enforce at most one ACTIVE class teacher per section in an academic year
CREATE UNIQUE INDEX idx_cta_unique_active_section
    ON class_teacher_assignment (academic_year_id, section_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

-- Performance and lookup indexes
CREATE INDEX idx_cta_staff_id         ON class_teacher_assignment(staff_id);
CREATE INDEX idx_cta_academic_year_id ON class_teacher_assignment(academic_year_id);
CREATE INDEX idx_cta_class_id         ON class_teacher_assignment(academic_class_id);
CREATE INDEX idx_cta_section_id       ON class_teacher_assignment(section_id);
CREATE INDEX idx_cta_status           ON class_teacher_assignment(status);
CREATE INDEX idx_cta_deleted_at       ON class_teacher_assignment(deleted_at);

COMMENT ON TABLE  class_teacher_assignment                 IS 'Designation of teachers/staff as primary class teachers for sections in an academic year';
COMMENT ON COLUMN class_teacher_assignment.assignment_code IS 'Stable business identifier (e.g. CTA-A1B2C3D4). Immutable after creation.';
COMMENT ON COLUMN class_teacher_assignment.notes           IS 'Optional remarks or handover notes for the class teacher assignment';
