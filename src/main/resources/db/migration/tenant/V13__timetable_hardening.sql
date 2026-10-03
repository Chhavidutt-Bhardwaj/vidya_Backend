-- ============================================================
-- TENANT V13: Timetable Hardening & Constraint Enhancements
-- 1. Single default bell_schedule per tenant
-- 2. Timetable effective_from <= effective_to CHECK
-- 3. TimetableEntry teacher_assignment requires teacher_id CHECK
-- 4. BellSchedulePeriod audit columns (created_by, updated_by)
-- 5. Single ACTIVE timetable per academic_year_id
-- ============================================================

-- 1. Ensure at most one active default bell schedule
CREATE UNIQUE INDEX idx_bell_schedule_single_default
    ON bell_schedule (is_default)
    WHERE is_default = TRUE AND deleted_at IS NULL;

-- 2. Ensure timetable effective date range validity
ALTER TABLE timetable
    ADD CONSTRAINT chk_timetable_effective_dates
    CHECK (effective_from IS NULL OR effective_to IS NULL OR effective_from <= effective_to);

-- 3. Ensure that if teacher_assignment_id is present, teacher_id must also be present
ALTER TABLE timetable_entry
    ADD CONSTRAINT chk_tte_teacher_assignment_requires_teacher
    CHECK (teacher_assignment_id IS NULL OR teacher_id IS NOT NULL);

-- 4. Add audit user identifiers to bell_schedule_period
ALTER TABLE bell_schedule_period
    ADD COLUMN created_by UUID NULL,
    ADD COLUMN updated_by UUID NULL;

-- 5. Ensure at most one ACTIVE timetable per academic year
CREATE UNIQUE INDEX idx_timetable_single_active_per_year
    ON timetable (academic_year_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
