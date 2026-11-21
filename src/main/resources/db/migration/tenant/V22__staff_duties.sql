-- TENANT V22: staff duties (responsibilities) — HOD, coordinators, in-charges.
-- A staff member can hold any number of duties at the same time (teacher + HOD + exam coordinator …).
-- Class teacher and subject teacher stay in their own assignment tables.

CREATE TABLE duty_type (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    code              VARCHAR(40)  NOT NULL,
    name              VARCHAR(150) NOT NULL,
    description       VARCHAR(255) NULL,
    category          VARCHAR(30)  NOT NULL DEFAULT 'ADMINISTRATION',
    scope_kind        VARCHAR(20)  NOT NULL DEFAULT 'SCHOOL',
    single_holder     BOOLEAN      NOT NULL DEFAULT TRUE,
    access_role_code  VARCHAR(50)  NULL,
    is_system         BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at        TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_duty_type PRIMARY KEY (id),
    CONSTRAINT chk_duty_type_category CHECK (category IN ('ACADEMIC', 'EXAMINATION', 'ADMINISTRATION', 'STUDENT_WELFARE', 'OPERATIONS')),
    CONSTRAINT chk_duty_type_scope CHECK (scope_kind IN ('SCHOOL', 'DEPARTMENT', 'STAGE', 'CLASS', 'SECTION', 'SUBJECT')),
    CONSTRAINT chk_duty_type_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);
CREATE UNIQUE INDEX uq_duty_type_code_active ON duty_type (upper(code)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_duty_type_name_active ON duty_type (lower(name)) WHERE deleted_at IS NULL;

COMMENT ON COLUMN duty_type.scope_kind IS 'What the duty is for: the whole SCHOOL, a DEPARTMENT, a class STAGE, a CLASS, a SECTION or a SUBJECT';
COMMENT ON COLUMN duty_type.single_holder IS 'Only one active holder per scope at a time (e.g. one HOD per department)';
COMMENT ON COLUMN duty_type.access_role_code IS 'Access role (global or this school''s) granted to the holder''s login while the duty is active';

CREATE TABLE staff_duty (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    staff_id             UUID         NOT NULL,
    duty_type_id         UUID         NOT NULL,
    academic_year_id     UUID         NULL,
    department_id        UUID         NULL,
    class_stage          VARCHAR(30)  NULL,
    academic_class_id    UUID         NULL,
    section_id           UUID         NULL,
    subject_id           UUID         NULL,
    start_date           DATE         NOT NULL DEFAULT CURRENT_DATE,
    end_date             DATE         NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    notes                VARCHAR(255) NULL,
    role_assignment_id   UUID         NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           UUID         NULL,
    updated_by           UUID         NULL,
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_staff_duty PRIMARY KEY (id),
    CONSTRAINT fk_staff_duty_staff FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_duty_type FOREIGN KEY (duty_type_id) REFERENCES duty_type(id),
    CONSTRAINT fk_staff_duty_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE SET NULL,
    CONSTRAINT fk_staff_duty_department FOREIGN KEY (department_id) REFERENCES department(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_duty_class FOREIGN KEY (academic_class_id) REFERENCES academic_class(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_duty_section FOREIGN KEY (section_id) REFERENCES section(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_duty_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE CASCADE,
    CONSTRAINT chk_staff_duty_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT chk_staff_duty_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_staff_duty_stage CHECK (class_stage IS NULL OR class_stage IN ('PRE_PRIMARY', 'PRIMARY', 'MIDDLE', 'SECONDARY', 'SENIOR_SECONDARY'))
);
CREATE INDEX idx_staff_duty_staff ON staff_duty (staff_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_staff_duty_type ON staff_duty (duty_type_id) WHERE status = 'ACTIVE';

COMMENT ON COLUMN staff_duty.role_assignment_id IS 'Master user_role_assignment created for this duty (removed when the duty ends)';

-- Standard duties every school gets. Roles marked * are created in the school with a standard permission set the first time they are used.
INSERT INTO duty_type (code, name, description, category, scope_kind, single_holder, access_role_code, is_system) VALUES
    ('HOD',                 'Head of Department',          'Leads a department; approves its teachers'' plans and papers',         'ACADEMIC',        'DEPARTMENT', TRUE,  'COORDINATOR',         TRUE),
    ('STAGE_COORDINATOR',   'Academic Coordinator',        'Coordinates a stage (pre-primary, primary, middle, secondary …)',      'ACADEMIC',        'STAGE',      TRUE,  'COORDINATOR',         TRUE),
    ('SUBJECT_INCHARGE',    'Subject In-charge',           'Owns the syllabus and question banks of one subject',                  'ACADEMIC',        'SUBJECT',    TRUE,  NULL,                  TRUE),
    ('LAB_INCHARGE',        'Laboratory In-charge',        'Runs a laboratory (physics, chemistry, biology, computer …)',          'ACADEMIC',        'SUBJECT',    FALSE, NULL,                  TRUE),
    ('EXAM_COORDINATOR',    'Examination Coordinator',     'Plans exams, collects marks and publishes results',                    'EXAMINATION',     'SCHOOL',     FALSE, 'EXAM_COORDINATOR',    TRUE),
    ('TIMETABLE_INCHARGE',  'Timetable In-charge',         'Builds the timetable and arranges substitutions',                      'ADMINISTRATION',  'SCHOOL',     FALSE, 'TIMETABLE_INCHARGE',  TRUE),
    ('ATTENDANCE_INCHARGE', 'Attendance In-charge',        'Monitors attendance across the school',                                'ADMINISTRATION',  'SCHOOL',     FALSE, 'ATTENDANCE_INCHARGE', TRUE),
    ('ADMISSION_INCHARGE',  'Admissions In-charge',        'Handles enquiries, admissions and transfers',                          'ADMINISTRATION',  'SCHOOL',     FALSE, 'ADMISSION_INCHARGE',  TRUE),
    ('DISCIPLINE_INCHARGE', 'Discipline In-charge',        'Student discipline and conduct',                                       'STUDENT_WELFARE', 'SCHOOL',     FALSE, NULL,                  TRUE),
    ('HOUSE_MASTER',        'House Master / Mistress',     'Leads a school house',                                                 'STUDENT_WELFARE', 'SCHOOL',     FALSE, NULL,                  TRUE),
    ('CULTURAL_INCHARGE',   'Cultural & Activities In-charge', 'Assemblies, annual day, clubs and competitions',                   'STUDENT_WELFARE', 'SCHOOL',     FALSE, NULL,                  TRUE),
    ('SPORTS_INCHARGE',     'Sports In-charge',            'Sports day, teams and inter-school events',                            'STUDENT_WELFARE', 'SCHOOL',     FALSE, NULL,                  TRUE),
    ('LIBRARY_INCHARGE',    'Library In-charge',           'Catalogue, issue and return of books',                                 'OPERATIONS',      'SCHOOL',     FALSE, 'LIBRARY_INCHARGE',    TRUE),
    ('TRANSPORT_INCHARGE',  'Transport In-charge',         'Bus routes, drivers and attendants',                                   'OPERATIONS',      'SCHOOL',     FALSE, NULL,                  TRUE);

-- Existing department heads become HOD duties (the head column stays as a denormalised mirror).
INSERT INTO staff_duty (staff_id, duty_type_id, department_id, start_date, status)
SELECT d.head_staff_id, t.id, d.id, CURRENT_DATE, 'ACTIVE'
FROM department d
JOIN duty_type t ON t.code = 'HOD'
JOIN staff s ON s.id = d.head_staff_id AND s.deleted_at IS NULL
WHERE d.head_staff_id IS NOT NULL AND d.deleted_at IS NULL;
