-- =====================================================================================
-- V26: Parent engagement — the Student Daily Activity Report and what feeds it.
--
--   homework               work set by a subject teacher for a class or one section
--   class_activity         what a class or section did today (lab, sports, trip, event ...)
--   announcement           notices to the whole school, a class or a section (EMERGENCY = sent at once)
--   student_daily_remark   a teacher's remark about one student on one day
--   daily_report_settings  whether / when the daily report goes out and what it contains (one row)
--   daily_report_run       one row per report date: claimed by one server, then the counts
--   notification_outbox    every message to send (in-app, email, SMS, WhatsApp) and its delivery state
--
-- Attendance, lessons delivered (lesson_plan) and day status (calendar) are read from existing tables.
-- =====================================================================================

-- ─────────────────────────── Homework ───────────────────────────

CREATE TABLE homework (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    homework_code      VARCHAR(50)  NOT NULL,
    academic_year_id   UUID         NOT NULL,
    academic_class_id  UUID         NOT NULL,
    section_id         UUID         NULL,
    subject_id         UUID         NOT NULL,
    teacher_id         UUID         NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT         NULL,
    assigned_on        DATE         NOT NULL,
    due_on             DATE         NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    deleted_at         TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID         NULL,
    updated_by         UUID         NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_homework PRIMARY KEY (id),
    CONSTRAINT uq_homework_code UNIQUE (homework_code),
    CONSTRAINT fk_homework_year    FOREIGN KEY (academic_year_id)  REFERENCES academic_year(id),
    CONSTRAINT fk_homework_class   FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT fk_homework_section FOREIGN KEY (section_id)        REFERENCES section(id),
    CONSTRAINT fk_homework_subject FOREIGN KEY (subject_id)        REFERENCES subject(id),
    CONSTRAINT fk_homework_teacher FOREIGN KEY (teacher_id)        REFERENCES staff(id),
    CONSTRAINT chk_homework_dates  CHECK (due_on >= assigned_on),
    CONSTRAINT chk_homework_status CHECK (status IN ('PUBLISHED', 'CANCELLED'))
);

CREATE INDEX idx_homework_class_assigned ON homework (academic_class_id, assigned_on) WHERE deleted_at IS NULL;
CREATE INDEX idx_homework_class_due      ON homework (academic_class_id, due_on)      WHERE deleted_at IS NULL;
CREATE INDEX idx_homework_teacher        ON homework (teacher_id, assigned_on DESC)   WHERE deleted_at IS NULL;

COMMENT ON COLUMN homework.section_id IS 'NULL = every section of the class';

-- ─────────────────────────── Class activities ───────────────────────────

CREATE TABLE class_activity (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    activity_code      VARCHAR(50)  NOT NULL,
    academic_year_id   UUID         NOT NULL,
    academic_class_id  UUID         NOT NULL,
    section_id         UUID         NULL,
    subject_id         UUID         NULL,
    teacher_id         UUID         NULL,
    activity_date      DATE         NOT NULL,
    activity_type      VARCHAR(30)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT         NULL,
    deleted_at         TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID         NULL,
    updated_by         UUID         NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_class_activity PRIMARY KEY (id),
    CONSTRAINT uq_class_activity_code UNIQUE (activity_code),
    CONSTRAINT fk_class_activity_year    FOREIGN KEY (academic_year_id)  REFERENCES academic_year(id),
    CONSTRAINT fk_class_activity_class   FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT fk_class_activity_section FOREIGN KEY (section_id)        REFERENCES section(id),
    CONSTRAINT fk_class_activity_subject FOREIGN KEY (subject_id)        REFERENCES subject(id),
    CONSTRAINT fk_class_activity_teacher FOREIGN KEY (teacher_id)        REFERENCES staff(id),
    CONSTRAINT chk_class_activity_type CHECK (activity_type IN
        ('LESSON', 'LAB', 'PROJECT', 'SPORTS', 'ART', 'MUSIC', 'FIELD_TRIP', 'ASSEMBLY', 'EVENT', 'ASSESSMENT', 'OTHER'))
);

CREATE INDEX idx_class_activity_class_date ON class_activity (academic_class_id, activity_date) WHERE deleted_at IS NULL;

-- ─────────────────────────── Announcements ───────────────────────────

CREATE TABLE announcement (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    announcement_code  VARCHAR(50)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    body               TEXT         NOT NULL,
    audience           VARCHAR(20)  NOT NULL,
    academic_class_id  UUID         NULL,
    section_id         UUID         NULL,
    priority           VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    notify             BOOLEAN      NOT NULL DEFAULT TRUE,
    publish_on         DATE         NULL,
    expires_on         DATE         NULL,
    published_at       TIMESTAMPTZ  NULL,
    published_by       UUID         NULL,
    deleted_at         TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID         NULL,
    updated_by         UUID         NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_announcement PRIMARY KEY (id),
    CONSTRAINT uq_announcement_code UNIQUE (announcement_code),
    CONSTRAINT fk_announcement_class   FOREIGN KEY (academic_class_id) REFERENCES academic_class(id),
    CONSTRAINT fk_announcement_section FOREIGN KEY (section_id)        REFERENCES section(id),
    CONSTRAINT chk_announcement_priority CHECK (priority IN ('NORMAL', 'IMPORTANT', 'EMERGENCY')),
    CONSTRAINT chk_announcement_status   CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN')),
    CONSTRAINT chk_announcement_audience CHECK (
           (audience = 'SCHOOL'  AND academic_class_id IS NULL     AND section_id IS NULL)
        OR (audience = 'CLASS'   AND academic_class_id IS NOT NULL AND section_id IS NULL)
        OR (audience = 'SECTION' AND academic_class_id IS NOT NULL AND section_id IS NOT NULL)),
    CONSTRAINT chk_announcement_expiry CHECK (expires_on IS NULL OR publish_on IS NULL OR expires_on >= publish_on)
);

CREATE INDEX idx_announcement_published ON announcement (publish_on DESC) WHERE deleted_at IS NULL AND status = 'PUBLISHED';

-- ─────────────────────────── Teacher remarks ───────────────────────────

CREATE TABLE student_daily_remark (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    student_id            UUID          NOT NULL,
    remark_date           DATE          NOT NULL,
    teacher_id            UUID          NULL,
    subject_id            UUID          NULL,
    category              VARCHAR(30)   NOT NULL DEFAULT 'GENERAL',
    sentiment             VARCHAR(20)   NOT NULL DEFAULT 'NEUTRAL',
    remark                VARCHAR(1000) NOT NULL,
    visible_to_guardians  BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at            TIMESTAMPTZ   NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            UUID          NULL,
    updated_by            UUID          NULL,
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_student_daily_remark PRIMARY KEY (id),
    CONSTRAINT fk_student_daily_remark_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_student_daily_remark_teacher FOREIGN KEY (teacher_id) REFERENCES staff(id),
    CONSTRAINT fk_student_daily_remark_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT chk_student_daily_remark_category CHECK (category IN
        ('ACADEMIC', 'BEHAVIOUR', 'PARTICIPATION', 'HEALTH', 'ACHIEVEMENT', 'GENERAL')),
    CONSTRAINT chk_student_daily_remark_sentiment CHECK (sentiment IN ('POSITIVE', 'NEUTRAL', 'CONCERN'))
);

CREATE INDEX idx_student_daily_remark_student_date ON student_daily_remark (student_id, remark_date) WHERE deleted_at IS NULL;

COMMENT ON COLUMN student_daily_remark.visible_to_guardians IS 'FALSE = staff-only note; never shown to parents or the student';

-- ─────────────────────────── Daily report ───────────────────────────

CREATE TABLE daily_report_settings (
    id                        SMALLINT     NOT NULL DEFAULT 1,
    enabled                   BOOLEAN      NOT NULL DEFAULT FALSE,
    send_time                 TIME         NOT NULL DEFAULT '18:00',
    skip_non_working_days     BOOLEAN      NOT NULL DEFAULT TRUE,
    include_attendance        BOOLEAN      NOT NULL DEFAULT TRUE,
    include_homework          BOOLEAN      NOT NULL DEFAULT TRUE,
    include_class_activities  BOOLEAN      NOT NULL DEFAULT TRUE,
    include_lessons           BOOLEAN      NOT NULL DEFAULT TRUE,
    include_announcements     BOOLEAN      NOT NULL DEFAULT TRUE,
    include_remarks           BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by                UUID         NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_daily_report_settings PRIMARY KEY (id),
    CONSTRAINT chk_daily_report_settings_single CHECK (id = 1)
);

CREATE TABLE daily_report_run (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    report_date  DATE         NOT NULL,
    run_trigger  VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    skip_reason  VARCHAR(255) NULL,
    students     INT          NOT NULL DEFAULT 0,
    recipients   INT          NOT NULL DEFAULT 0,
    messages     INT          NOT NULL DEFAULT 0,
    started_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finished_at  TIMESTAMPTZ  NULL,
    error        TEXT         NULL,
    started_by   UUID         NULL,

    CONSTRAINT pk_daily_report_run PRIMARY KEY (id),
    CONSTRAINT uq_daily_report_run_date UNIQUE (report_date),
    CONSTRAINT chk_daily_report_run_trigger CHECK (run_trigger IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT chk_daily_report_run_status  CHECK (status IN ('RUNNING', 'COMPLETED', 'SKIPPED', 'FAILED'))
);

-- ─────────────────────────── Notification outbox ───────────────────────────

CREATE TABLE notification_outbox (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_code         VARCHAR(50)  NOT NULL,
    channel            VARCHAR(20)  NOT NULL,
    recipient_user_id  UUID         NULL,
    recipient_name     VARCHAR(200) NULL,
    address            VARCHAR(255) NULL,
    subject            VARCHAR(300) NOT NULL,
    body               TEXT         NOT NULL,
    priority           VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    ref_type           VARCHAR(50)  NULL,
    ref_id             UUID         NULL,
    ref_date           DATE         NULL,
    dedupe_key         VARCHAR(300) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts           INT          NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at            TIMESTAMPTZ  NULL,
    read_at            TIMESTAMPTZ  NULL,
    last_error         TEXT         NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_notification_outbox PRIMARY KEY (id),
    CONSTRAINT uq_notification_outbox_dedupe UNIQUE (dedupe_key),
    CONSTRAINT fk_notification_outbox_user FOREIGN KEY (recipient_user_id) REFERENCES tenant_user(id) ON DELETE SET NULL,
    CONSTRAINT chk_notification_outbox_channel  CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'WHATSAPP')),
    CONSTRAINT chk_notification_outbox_status   CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_notification_outbox_priority CHECK (priority IN ('NORMAL', 'IMPORTANT', 'EMERGENCY')),
    CONSTRAINT chk_notification_outbox_target   CHECK (
        (channel = 'IN_APP' AND recipient_user_id IS NOT NULL) OR (channel <> 'IN_APP' AND address IS NOT NULL))
);

CREATE INDEX idx_notification_outbox_due   ON notification_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_notification_outbox_inbox ON notification_outbox (recipient_user_id, created_at DESC) WHERE channel = 'IN_APP';

COMMENT ON COLUMN notification_outbox.dedupe_key IS 'Same key = same message; a second enqueue is ignored (e.g. DAILY_REPORT:2026-11-13:EMAIL:parent@example.com)';
COMMENT ON COLUMN notification_outbox.next_attempt_at IS 'Not sent before this time: quiet hours for SMS / WhatsApp, or the retry back-off';
