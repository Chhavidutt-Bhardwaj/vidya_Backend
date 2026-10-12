-- =============================================================================
-- Migration: V16__attendance_non_working_override.sql
-- Description: Add non-working day override support to student attendance
-- =============================================================================

ALTER TABLE student_attendance
    ADD COLUMN IF NOT EXISTS non_working_day_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS override_reason VARCHAR(255) NULL;

ALTER TABLE student_attendance
    ADD CONSTRAINT chk_student_attendance_override_reason
    CHECK (
        (non_working_day_override = FALSE) OR
        (non_working_day_override = TRUE AND override_reason IS NOT NULL AND length(trim(override_reason)) > 0)
    );
