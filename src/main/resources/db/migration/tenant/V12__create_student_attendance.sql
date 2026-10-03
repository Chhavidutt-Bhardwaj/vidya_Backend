-- ============================================================
-- TENANT V12: Student Attendance Management Foundation
-- Daily attendance record for students in their academic year / class / section
-- ============================================================

CREATE TABLE student_attendance (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    attendance_code     VARCHAR(50)   NOT NULL,

    student_id          UUID          NOT NULL,
    academic_year_id    UUID          NOT NULL,
    academic_class_id   UUID          NOT NULL,
    section_id          UUID          NOT NULL,

    attendance_date     DATE          NOT NULL,
    status              VARCHAR(30)   NOT NULL,

    remarks             TEXT          NULL,

    marked_by_staff_id  UUID          NULL,
    marked_at           TIMESTAMPTZ   NULL,

    deleted_at          TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          UUID          NULL,
    updated_by          UUID          NULL,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_student_attendance PRIMARY KEY (id),
    CONSTRAINT uq_student_attendance_code UNIQUE (attendance_code),

    CONSTRAINT fk_sa_student FOREIGN KEY (student_id)
        REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sa_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sa_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sa_section FOREIGN KEY (section_id)
        REFERENCES section(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sa_marked_by_staff FOREIGN KEY (marked_by_staff_id)
        REFERENCES staff(id) ON DELETE RESTRICT,

    CONSTRAINT chk_sa_status CHECK (status IN ('PRESENT','ABSENT','LATE','HALF_DAY','EXCUSED','DELETED'))
);

-- ── Core uniqueness constraint ────────────────────────────────────────────────
-- At most one non-deleted attendance record per (student, academic_year, date).
-- This is the database-level guarantee; application-level checks are supplementary.
CREATE UNIQUE INDEX idx_sa_unique_active_student_year_date
    ON student_attendance (student_id, academic_year_id, attendance_date)
    WHERE deleted_at IS NULL;

-- ── Performance indexes ───────────────────────────────────────────────────────
-- Used for: student history queries
CREATE INDEX idx_sa_student_year        ON student_attendance(student_id, academic_year_id);
-- Used for: daily section attendance sheet
CREATE INDEX idx_sa_section_date        ON student_attendance(section_id, attendance_date);
-- Used for: section + year + date queries (daily view with year filter)
CREATE INDEX idx_sa_year_section_date   ON student_attendance(academic_year_id, section_id, attendance_date);
-- Used for: status-based reporting
CREATE INDEX idx_sa_student_year_status ON student_attendance(student_id, academic_year_id, status)
    WHERE deleted_at IS NULL;
-- General ordering / admin queries
CREATE INDEX idx_sa_attendance_date     ON student_attendance(attendance_date);
CREATE INDEX idx_sa_deleted_at          ON student_attendance(deleted_at);
CREATE INDEX idx_sa_marked_by_staff     ON student_attendance(marked_by_staff_id);

-- ── Column documentation ─────────────────────────────────────────────────────
COMMENT ON TABLE  student_attendance                      IS 'Daily attendance record for a student in an academic year / class / section';
COMMENT ON COLUMN student_attendance.attendance_code      IS 'Stable business identifier (e.g. ATT-A1B2C3D4). Immutable after creation.';
COMMENT ON COLUMN student_attendance.attendance_date      IS 'Calendar date for which attendance is recorded (DATE, not timestamp)';
COMMENT ON COLUMN student_attendance.status               IS 'Attendance status: PRESENT, ABSENT, LATE, HALF_DAY, EXCUSED, or DELETED (soft-delete)';
COMMENT ON COLUMN student_attendance.remarks              IS 'Optional free-text remarks (reason for absence, correction note, etc.)';
COMMENT ON COLUMN student_attendance.marked_by_staff_id   IS 'Staff member who last marked or corrected this attendance record';
COMMENT ON COLUMN student_attendance.marked_at            IS 'Timestamp when attendance was last marked or corrected';
