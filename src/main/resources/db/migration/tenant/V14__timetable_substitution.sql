-- ============================================================
-- TENANT V14: Timetable Substitution Management
-- 1. Table: timetable_substitution
-- 2. Check Constraints: type, status, and type-specific payload requirements
-- 3. Partial Unique Indexes: single active substitution per slot, teacher clash, and room clash
-- ============================================================

CREATE TABLE IF NOT EXISTS timetable_substitution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    substitution_code VARCHAR(50) NOT NULL UNIQUE,
    timetable_id UUID NOT NULL REFERENCES timetable(id),
    substitution_date DATE NOT NULL,
    period_id UUID NOT NULL REFERENCES bell_schedule_period(id),
    academic_class_id UUID NOT NULL REFERENCES academic_class(id),
    section_id UUID REFERENCES section(id),
    substitution_type VARCHAR(30) NOT NULL,
    original_teacher_id UUID REFERENCES staff(id),
    original_subject_id UUID REFERENCES subject(id),
    substitute_teacher_id UUID REFERENCES staff(id),
    substitute_room_id UUID REFERENCES room(id),
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NULL,
    updated_by UUID NULL,
    deleted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_sub_type CHECK (substitution_type IN ('TEACHER_SUBSTITUTE', 'ROOM_CHANGE', 'CLASS_CANCELLED')),
    CONSTRAINT chk_sub_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'DELETED')),
    CONSTRAINT chk_sub_payload CHECK (
        (substitution_type = 'TEACHER_SUBSTITUTE' AND substitute_teacher_id IS NOT NULL) OR
        (substitution_type = 'ROOM_CHANGE' AND substitute_room_id IS NOT NULL) OR
        (substitution_type = 'CLASS_CANCELLED' AND substitute_teacher_id IS NULL AND substitute_room_id IS NULL)
    )
);

-- Partial unique indexes for active substitutions
CREATE UNIQUE INDEX idx_sub_slot_active ON timetable_substitution (
    timetable_id, substitution_date, period_id, academic_class_id, COALESCE(section_id, '00000000-0000-0000-0000-000000000000'::uuid)
) WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE UNIQUE INDEX idx_sub_teacher_active ON timetable_substitution (
    substitution_date, period_id, substitute_teacher_id
) WHERE deleted_at IS NULL AND status = 'ACTIVE' AND substitute_teacher_id IS NOT NULL;

CREATE UNIQUE INDEX idx_sub_room_active ON timetable_substitution (
    substitution_date, period_id, substitute_room_id
) WHERE deleted_at IS NULL AND status = 'ACTIVE' AND substitute_room_id IS NOT NULL;

CREATE INDEX idx_sub_date_status ON timetable_substitution (substitution_date, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_sub_timetable_id ON timetable_substitution (timetable_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_sub_section_id ON timetable_substitution (section_id) WHERE deleted_at IS NULL;
