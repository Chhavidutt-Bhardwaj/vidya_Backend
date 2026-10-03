-- ============================================================
-- TENANT V9: Timetable & Scheduling Foundation
-- Room, BellSchedule, BellSchedulePeriod, Timetable, TimetableEntry
-- ============================================================

-- 1. Create room table
CREATE TABLE room (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    room_code   VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    room_type   VARCHAR(30)  NOT NULL DEFAULT 'CLASSROOM',
    capacity    INT          NULL,
    building    VARCHAR(100) NULL,
    floor       VARCHAR(50)  NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at  TIMESTAMPTZ  NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  UUID         NULL,
    updated_by  UUID         NULL,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_room PRIMARY KEY (id),
    CONSTRAINT uq_room_code UNIQUE (room_code),
    CONSTRAINT chk_room_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'DELETED')),
    CONSTRAINT chk_room_capacity CHECK (capacity IS NULL OR capacity >= 0),
    CONSTRAINT chk_room_type CHECK (room_type IN ('CLASSROOM', 'LAB', 'COMPUTER_LAB', 'SCIENCE_LAB', 'LIBRARY', 'AUDITORIUM', 'SPORTS', 'OTHER'))
);

CREATE UNIQUE INDEX idx_room_name_active ON room (LOWER(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_room_type ON room(room_type);
CREATE INDEX idx_room_status ON room(status);

-- 2. Create bell_schedule table
CREATE TABLE bell_schedule (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    schedule_code VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    status        VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at    TIMESTAMPTZ  NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID         NULL,
    updated_by    UUID         NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_bell_schedule PRIMARY KEY (id),
    CONSTRAINT uq_bell_schedule_code UNIQUE (schedule_code),
    CONSTRAINT chk_bell_schedule_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE UNIQUE INDEX idx_bell_schedule_name_active ON bell_schedule (LOWER(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_bell_schedule_status ON bell_schedule(status);

-- 3. Create bell_schedule_period table
CREATE TABLE bell_schedule_period (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    period_code      VARCHAR(50)  NOT NULL,
    bell_schedule_id UUID         NOT NULL,
    name             VARCHAR(50)  NOT NULL,
    period_sequence  INT          NOT NULL,
    start_time       TIME         NOT NULL,
    end_time         TIME         NOT NULL,
    period_type      VARCHAR(30)  NOT NULL DEFAULT 'TEACHING',
    deleted_at       TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_bell_schedule_period PRIMARY KEY (id),
    CONSTRAINT uq_bell_schedule_period_code UNIQUE (period_code),
    CONSTRAINT fk_bsp_bell_schedule FOREIGN KEY (bell_schedule_id)
        REFERENCES bell_schedule(id) ON DELETE CASCADE,
    CONSTRAINT chk_bsp_sequence CHECK (period_sequence > 0),
    CONSTRAINT chk_bsp_times CHECK (start_time < end_time),
    CONSTRAINT chk_bsp_period_type CHECK (period_type IN ('TEACHING', 'BREAK', 'LUNCH', 'ASSEMBLY', 'ACTIVITY', 'FREE', 'OTHER'))
);

CREATE UNIQUE INDEX idx_bsp_schedule_sequence ON bell_schedule_period (bell_schedule_id, period_sequence) WHERE deleted_at IS NULL;
CREATE INDEX idx_bsp_schedule_id ON bell_schedule_period(bell_schedule_id);

-- 4. Create timetable table
CREATE TABLE timetable (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    timetable_code    VARCHAR(50)  NOT NULL,
    academic_year_id  UUID         NOT NULL,
    bell_schedule_id  UUID         NOT NULL,
    name              VARCHAR(150) NOT NULL,
    description       VARCHAR(500) NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    effective_from    DATE         NULL,
    effective_to      DATE         NULL,
    deleted_at        TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID         NULL,
    updated_by        UUID         NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_timetable PRIMARY KEY (id),
    CONSTRAINT uq_timetable_code UNIQUE (timetable_code),
    CONSTRAINT fk_timetable_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_timetable_bell_schedule FOREIGN KEY (bell_schedule_id)
        REFERENCES bell_schedule(id) ON DELETE RESTRICT,
    CONSTRAINT chk_timetable_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ACTIVE', 'ARCHIVED', 'DELETED'))
);

CREATE INDEX idx_timetable_academic_year_id ON timetable(academic_year_id);
CREATE INDEX idx_timetable_bell_schedule_id ON timetable(bell_schedule_id);
CREATE INDEX idx_timetable_status ON timetable(status);

-- 5. Create timetable_entry table
CREATE TABLE timetable_entry (
    id                            UUID         NOT NULL DEFAULT gen_random_uuid(),
    entry_code                    VARCHAR(50)  NOT NULL,
    timetable_id                  UUID         NOT NULL,
    academic_year_id              UUID         NOT NULL,
    period_id                     UUID         NOT NULL,
    day_of_week                   VARCHAR(20)  NOT NULL,
    academic_class_id             UUID         NOT NULL,
    section_id                    UUID         NULL,
    subject_id                    UUID         NULL,
    teacher_assignment_id         UUID         NULL,
    teacher_id                    UUID         NULL,
    class_subject_assignment_id   UUID         NULL,
    room_id                       UUID         NULL,
    entry_type                    VARCHAR(30)  NOT NULL DEFAULT 'REGULAR',
    deleted_at                    TIMESTAMPTZ  NULL,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                    UUID         NULL,
    updated_by                    UUID         NULL,
    version                       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_timetable_entry PRIMARY KEY (id),
    CONSTRAINT uq_timetable_entry_code UNIQUE (entry_code),
    CONSTRAINT fk_tte_timetable FOREIGN KEY (timetable_id)
        REFERENCES timetable(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_period FOREIGN KEY (period_id)
        REFERENCES bell_schedule_period(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_academic_class FOREIGN KEY (academic_class_id)
        REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_section FOREIGN KEY (section_id)
        REFERENCES section(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_subject FOREIGN KEY (subject_id)
        REFERENCES subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_teacher_assignment FOREIGN KEY (teacher_assignment_id)
        REFERENCES teacher_subject_assignment(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_teacher FOREIGN KEY (teacher_id)
        REFERENCES staff(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_class_subject_assignment FOREIGN KEY (class_subject_assignment_id)
        REFERENCES class_subject_assignment(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tte_room FOREIGN KEY (room_id)
        REFERENCES room(id) ON DELETE RESTRICT,
    CONSTRAINT chk_tte_day CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT chk_tte_entry_type CHECK (entry_type IN ('REGULAR', 'LAB', 'ACTIVITY', 'BREAK', 'ASSEMBLY', 'SUBSTITUTE', 'SPECIAL'))
);

-- Partial unique indexes to prevent scheduling clashes at DB level
-- 1. Teacher double-booking prevention:
CREATE UNIQUE INDEX idx_tte_teacher_clash
    ON timetable_entry (timetable_id, teacher_id, day_of_week, period_id)
    WHERE deleted_at IS NULL AND teacher_id IS NOT NULL;

-- 2. Class double-booking prevention (accounting for nullable section):
CREATE UNIQUE INDEX idx_tte_class_clash
    ON timetable_entry (timetable_id, academic_class_id, COALESCE(section_id, '00000000-0000-0000-0000-000000000000'::uuid), day_of_week, period_id)
    WHERE deleted_at IS NULL;

-- 3. Room double-booking prevention:
CREATE UNIQUE INDEX idx_tte_room_clash
    ON timetable_entry (timetable_id, room_id, day_of_week, period_id)
    WHERE deleted_at IS NULL AND room_id IS NOT NULL;

-- Access pattern indexes for fast lookups and grid views
CREATE INDEX idx_tte_timetable_id ON timetable_entry(timetable_id);
CREATE INDEX idx_tte_academic_year_id ON timetable_entry(academic_year_id);
CREATE INDEX idx_tte_teacher_lookup ON timetable_entry(teacher_id, day_of_week);
CREATE INDEX idx_tte_class_lookup ON timetable_entry(academic_class_id, section_id, day_of_week);
CREATE INDEX idx_tte_room_lookup ON timetable_entry(room_id, day_of_week);
CREATE INDEX idx_tte_period_lookup ON timetable_entry(period_id);
