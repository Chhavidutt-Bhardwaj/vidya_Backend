-- ============================================================
-- TENANT V5: Academic Structure Foundation
-- AcademicYear, AcademicClass, Section, StudentAcademicAssignment
-- ============================================================

-- 1. Upgrade academic_year table from V2 placeholder
ALTER TABLE academic_year DROP CONSTRAINT IF EXISTS chk_academic_year_status;
ALTER TABLE academic_year DROP CONSTRAINT IF EXISTS uq_academic_year_label;
DROP INDEX IF EXISTS idx_academic_year_current;
DROP INDEX IF EXISTS idx_academic_year_status;

ALTER TABLE academic_year ADD COLUMN IF NOT EXISTS code VARCHAR(50);
UPDATE academic_year SET code = label WHERE code IS NULL;
ALTER TABLE academic_year ALTER COLUMN code SET NOT NULL;

ALTER TABLE academic_year ADD COLUMN IF NOT EXISTS name VARCHAR(100);
UPDATE academic_year SET name = label WHERE name IS NULL;
ALTER TABLE academic_year ALTER COLUMN name SET NOT NULL;

ALTER TABLE academic_year ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;
ALTER TABLE academic_year ADD COLUMN IF NOT EXISTS created_by UUID NULL;
ALTER TABLE academic_year ADD COLUMN IF NOT EXISTS updated_by UUID NULL;

ALTER TABLE academic_year DROP COLUMN IF EXISTS label;

ALTER TABLE academic_year ADD CONSTRAINT uq_academic_year_code UNIQUE (code);
ALTER TABLE academic_year ADD CONSTRAINT chk_academic_year_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'CANCELLED', 'DELETED'));

CREATE UNIQUE INDEX idx_academic_year_unique_current ON academic_year(is_current) WHERE (is_current = TRUE AND deleted_at IS NULL);
CREATE INDEX idx_academic_year_code ON academic_year(code);
CREATE INDEX idx_academic_year_status ON academic_year(status);
CREATE INDEX idx_academic_year_dates ON academic_year(start_date, end_date);
CREATE INDEX idx_academic_year_deleted_at ON academic_year(deleted_at);

COMMENT ON TABLE  academic_year             IS 'Operational academic year lifecycle definitions';
COMMENT ON COLUMN academic_year.code        IS 'Unique identifier code, e.g. AY2026-27';
COMMENT ON COLUMN academic_year.is_current  IS 'True if this is the active current operational academic year (at most one per tenant)';

-- 2. Create academic_class table
CREATE TABLE academic_class (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    display_name        VARCHAR(150) NULL,
    sort_order          INT          NOT NULL DEFAULT 0,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_academic_class PRIMARY KEY (id),
    CONSTRAINT uq_academic_class_code UNIQUE (code),
    CONSTRAINT chk_academic_class_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE INDEX idx_academic_class_code ON academic_class(code);
CREATE INDEX idx_academic_class_sort ON academic_class(sort_order);
CREATE INDEX idx_academic_class_status ON academic_class(status);
CREATE INDEX idx_academic_class_deleted_at ON academic_class(deleted_at);

COMMENT ON TABLE academic_class IS 'Reusable grade/class level definitions for the school tenant';

-- 3. Create section table
CREATE TABLE section (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    academic_year_id    UUID         NOT NULL,
    academic_class_id   UUID         NOT NULL,
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    capacity            INT          NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_section PRIMARY KEY (id),
    CONSTRAINT uq_section_year_class_code UNIQUE (academic_year_id, academic_class_id, code),
    CONSTRAINT fk_section_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_section_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT chk_section_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT chk_section_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE INDEX idx_section_year_class ON section(academic_year_id, academic_class_id);
CREATE INDEX idx_section_code ON section(code);
CREATE INDEX idx_section_status ON section(status);
CREATE INDEX idx_section_deleted_at ON section(deleted_at);

COMMENT ON TABLE section IS 'Class section instantiated for a specific academic year';

-- 4. Create student_academic_assignment table
CREATE TABLE student_academic_assignment (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    student_id          UUID         NOT NULL,
    academic_year_id    UUID         NOT NULL,
    academic_class_id   UUID         NOT NULL,
    section_id          UUID         NOT NULL,
    roll_number         VARCHAR(50)  NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    start_date          DATE         NOT NULL,
    end_date            DATE         NULL,
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_student_academic_assignment PRIMARY KEY (id),
    CONSTRAINT fk_assignment_student FOREIGN KEY (student_id)
        REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_section FOREIGN KEY (section_id)
        REFERENCES section(id) ON DELETE RESTRICT,
    CONSTRAINT chk_assignment_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_assignment_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'TRANSFERRED', 'CANCELLED', 'DELETED'))
);

-- At most one ACTIVE placement per student per academic year
CREATE UNIQUE INDEX idx_assignment_unique_active ON student_academic_assignment(student_id, academic_year_id)
    WHERE (status = 'ACTIVE' AND deleted_at IS NULL);

CREATE INDEX idx_assignment_student ON student_academic_assignment(student_id);
CREATE INDEX idx_assignment_year ON student_academic_assignment(academic_year_id);
CREATE INDEX idx_assignment_class ON student_academic_assignment(academic_class_id);
CREATE INDEX idx_assignment_section ON student_academic_assignment(section_id);
CREATE INDEX idx_assignment_status ON student_academic_assignment(status);
CREATE INDEX idx_assignment_deleted_at ON student_academic_assignment(deleted_at);

COMMENT ON TABLE student_academic_assignment IS 'Historical and active student placement in class and section for an academic year';
