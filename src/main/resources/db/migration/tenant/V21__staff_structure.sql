-- ============================================================
-- TENANT V21: Staff structure
--   * department  (Science, Languages, Administration, Transport …) with head of department
--   * designation (Principal, PGT, TGT, Accountant, Driver …) with staff type and default login role
--   * staff: department / designation links, reporting line, employment type, qualification,
--            experience, teaching load limit, last working date
--   * staff_subject: subjects (and class range) a teacher can teach
-- Existing free-text staff.department / staff.designation values become rows and are linked.
-- The text columns stay and are kept in sync (older clients read them).
-- ============================================================

CREATE TABLE department (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(255) NULL,
    category        VARCHAR(30)  NOT NULL DEFAULT 'ACADEMIC',
    head_staff_id   UUID         NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at      TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_department PRIMARY KEY (id),
    CONSTRAINT fk_department_head FOREIGN KEY (head_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_department_category CHECK (category IN ('ACADEMIC', 'ADMINISTRATIVE', 'SUPPORT')),
    CONSTRAINT chk_department_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);
CREATE UNIQUE INDEX uq_department_code_active ON department (upper(code)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_department_name_active ON department (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE designation (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    code                   VARCHAR(30)  NOT NULL,
    name                   VARCHAR(150) NOT NULL,
    description            VARCHAR(255) NULL,
    staff_type             VARCHAR(30)  NOT NULL,
    default_role           VARCHAR(50)  NULL,
    default_department_id  UUID         NULL,
    rank                   INT          NOT NULL DEFAULT 100,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at             TIMESTAMPTZ  NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_designation PRIMARY KEY (id),
    CONSTRAINT fk_designation_department FOREIGN KEY (default_department_id) REFERENCES department(id) ON DELETE SET NULL,
    CONSTRAINT chk_designation_staff_type CHECK (staff_type IN ('TEACHER', 'NON_TEACHING', 'ADMINISTRATIVE', 'SUPPORT', 'OTHER')),
    CONSTRAINT chk_designation_role CHECK (default_role IS NULL OR default_role IN
        ('SCHOOL_ADMIN', 'PRINCIPAL', 'COORDINATOR', 'TEACHER', 'SUBJECT_TEACHER', 'ACCOUNTS', 'SCHOOL_STAFF')),
    CONSTRAINT chk_designation_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);
CREATE UNIQUE INDEX uq_designation_code_active ON designation (upper(code)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_designation_name_active ON designation (lower(name)) WHERE deleted_at IS NULL;

COMMENT ON COLUMN designation.default_role IS 'Login role given when staff with this designation get an account (school can override per person)';
COMMENT ON COLUMN designation.rank IS 'Seniority for sorting (Principal 10 … support staff 90)';

ALTER TABLE staff
    ADD COLUMN department_id          UUID          NULL,
    ADD COLUMN designation_id         UUID          NULL,
    ADD COLUMN reporting_to_staff_id  UUID          NULL,
    ADD COLUMN employment_type        VARCHAR(30)   NULL,
    ADD COLUMN qualification          VARCHAR(255)  NULL,
    ADD COLUMN experience_years       NUMERIC(4,1)  NULL,
    ADD COLUMN max_periods_per_week   INT           NULL,
    ADD COLUMN last_working_date      DATE          NULL,
    ADD CONSTRAINT fk_staff_department FOREIGN KEY (department_id) REFERENCES department(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_staff_designation FOREIGN KEY (designation_id) REFERENCES designation(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_staff_reporting_to FOREIGN KEY (reporting_to_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_staff_employment_type CHECK (employment_type IS NULL OR employment_type IN
        ('PERMANENT', 'PROBATION', 'CONTRACT', 'PART_TIME', 'GUEST', 'VOLUNTEER')),
    ADD CONSTRAINT chk_staff_experience CHECK (experience_years IS NULL OR experience_years BETWEEN 0 AND 60),
    ADD CONSTRAINT chk_staff_max_periods CHECK (max_periods_per_week IS NULL OR max_periods_per_week BETWEEN 0 AND 80),
    ADD CONSTRAINT chk_staff_not_own_manager CHECK (reporting_to_staff_id IS NULL OR reporting_to_staff_id <> id);

-- idx_staff_department already exists (V6, on the old free-text column), so this one carries the column name.
CREATE INDEX idx_staff_department_id ON staff(department_id);
CREATE INDEX idx_staff_designation_id ON staff(designation_id);
CREATE INDEX idx_staff_reporting   ON staff(reporting_to_staff_id);

CREATE TABLE staff_subject (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    staff_id          UUID        NOT NULL,
    subject_id        UUID        NOT NULL,
    from_grade_level  INT         NULL,
    to_grade_level    INT         NULL,
    is_primary        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_staff_subject PRIMARY KEY (id),
    CONSTRAINT uq_staff_subject UNIQUE (staff_id, subject_id),
    CONSTRAINT fk_staff_subject_staff FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_subject_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE CASCADE,
    CONSTRAINT chk_staff_subject_levels CHECK (
        (from_grade_level IS NULL OR from_grade_level BETWEEN -3 AND 12)
        AND (to_grade_level IS NULL OR to_grade_level BETWEEN -3 AND 12)
        AND (from_grade_level IS NULL OR to_grade_level IS NULL OR from_grade_level <= to_grade_level))
);
CREATE INDEX idx_staff_subject_subject ON staff_subject(subject_id);

COMMENT ON TABLE staff_subject IS 'Subjects a teacher can teach, optionally limited to a class range (grade levels); used to suggest subject teachers';

-- ---- existing free text → rows -----------------------------------------------------------
INSERT INTO department (code, name, category)
SELECT 'DEP-' || upper(substr(md5(x.d || random()::text), 1, 6)), x.d, 'ACADEMIC'
FROM (SELECT min(trim(department)) AS d FROM staff
      WHERE department IS NOT NULL AND trim(department) <> '' AND deleted_at IS NULL
      GROUP BY lower(trim(department))) x;

INSERT INTO designation (code, name, staff_type, default_role)
SELECT 'DSG-' || upper(substr(md5(x.d || random()::text), 1, 6)), x.d, x.t,
       CASE x.t WHEN 'TEACHER' THEN 'TEACHER' ELSE 'SCHOOL_STAFF' END
FROM (SELECT min(trim(designation)) AS d, mode() WITHIN GROUP (ORDER BY staff_type) AS t FROM staff
      WHERE designation IS NOT NULL AND trim(designation) <> '' AND deleted_at IS NULL
      GROUP BY lower(trim(designation))) x;

UPDATE staff s SET department_id = d.id FROM department d
WHERE s.department IS NOT NULL AND lower(trim(s.department)) = lower(d.name);
UPDATE staff s SET designation_id = g.id FROM designation g
WHERE s.designation IS NOT NULL AND lower(trim(s.designation)) = lower(g.name);

-- Support staff (drivers, guards, housekeeping) often have no email; they need a phone instead.
-- Email stays unique when present; a login still requires one.
ALTER TABLE tenant_user ALTER COLUMN email DROP NOT NULL;
ALTER TABLE tenant_user ADD CONSTRAINT chk_tenant_user_contact CHECK (email IS NOT NULL OR phone IS NOT NULL);
