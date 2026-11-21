-- TENANT V25: operational configuration — the settings the school fills in before day-to-day work starts.
--   Fees           fee heads, year settings (late fine, receipts), instalment plan, fee structure per class, concession types
--   Attendance     marking rules (single row)
--   Transport      vehicles, routes and stops
--   Library        lending rules (single row) and book categories
--   Communication  channels (single row) and message templates per event
-- Timetable (V13/V14) and examinations (V17) already have their own setup tables.
-- Collection, issue/return and message delivery are not part of this migration.

-- ─────────────────────────────── Fees ───────────────────────────────

CREATE TABLE fee_head (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    code           VARCHAR(30)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    category       VARCHAR(30)  NOT NULL DEFAULT 'OTHER',
    frequency      VARCHAR(20)  NOT NULL DEFAULT 'ANNUAL',
    is_optional    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_refundable  BOOLEAN      NOT NULL DEFAULT FALSE,
    description    VARCHAR(255) NULL,
    sort_order     INT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at     TIMESTAMPTZ  NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_head PRIMARY KEY (id),
    CONSTRAINT chk_fee_head_category CHECK (category IN ('TUITION', 'ADMISSION', 'EXAMINATION', 'TRANSPORT', 'LIBRARY',
        'LABORATORY', 'ACTIVITY', 'HOSTEL', 'UNIFORM', 'BOOKS', 'DEPOSIT', 'OTHER')),
    CONSTRAINT chk_fee_head_frequency CHECK (frequency IN ('ONE_TIME', 'ANNUAL', 'HALF_YEARLY', 'QUARTERLY', 'MONTHLY')),
    CONSTRAINT chk_fee_head_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE UNIQUE INDEX uq_fee_head_code_active ON fee_head (upper(code)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_fee_head_name_active ON fee_head (lower(name)) WHERE deleted_at IS NULL;

COMMENT ON TABLE fee_head IS 'What the school charges for (tuition, admission, exam …) and how often';
COMMENT ON COLUMN fee_head.frequency IS 'How many times a year the amount in a fee structure is charged: ONE_TIME/ANNUAL 1, HALF_YEARLY 2, QUARTERLY 4, MONTHLY 12';

CREATE TABLE fee_settings (
    academic_year_id       UUID          NOT NULL,
    collection_frequency   VARCHAR(20)   NOT NULL DEFAULT 'QUARTERLY',
    due_day                INT           NOT NULL DEFAULT 10,
    grace_days             INT           NOT NULL DEFAULT 0,
    late_fine_type         VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    late_fine_value        NUMERIC(10,2) NOT NULL DEFAULT 0,
    late_fine_cap          NUMERIC(10,2) NULL,
    allow_partial_payment  BOOLEAN       NOT NULL DEFAULT TRUE,
    allow_online_payment   BOOLEAN       NOT NULL DEFAULT FALSE,
    receipt_prefix         VARCHAR(10)   NOT NULL DEFAULT 'RCP',
    currency               VARCHAR(3)    NOT NULL DEFAULT 'INR',
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by             UUID          NULL,
    version                BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_settings PRIMARY KEY (academic_year_id),
    CONSTRAINT fk_fee_settings_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE CASCADE,
    CONSTRAINT chk_fee_settings_frequency CHECK (collection_frequency IN ('ANNUAL', 'HALF_YEARLY', 'QUARTERLY', 'MONTHLY')),
    CONSTRAINT chk_fee_settings_due_day CHECK (due_day BETWEEN 1 AND 28),
    CONSTRAINT chk_fee_settings_grace CHECK (grace_days BETWEEN 0 AND 90),
    CONSTRAINT chk_fee_settings_fine_type CHECK (late_fine_type IN ('NONE', 'FIXED', 'PER_DAY', 'PERCENT')),
    CONSTRAINT chk_fee_settings_fine_value CHECK (late_fine_value >= 0 AND (late_fine_type <> 'PERCENT' OR late_fine_value <= 100)),
    CONSTRAINT chk_fee_settings_fine_cap CHECK (late_fine_cap IS NULL OR late_fine_cap >= 0)
);

CREATE TABLE fee_installment (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    academic_year_id  UUID         NOT NULL,
    installment_no    INT          NOT NULL,
    name              VARCHAR(100) NOT NULL,
    due_date          DATE         NOT NULL,
    percent           NUMERIC(5,2) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_fee_installment PRIMARY KEY (id),
    CONSTRAINT uq_fee_installment_no UNIQUE (academic_year_id, installment_no),
    CONSTRAINT fk_fee_installment_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE CASCADE,
    CONSTRAINT chk_fee_installment_percent CHECK (percent > 0 AND percent <= 100)
);

COMMENT ON TABLE fee_installment IS 'When the year''s fee falls due. Recurring heads are split by percent (sum 100); ONE_TIME heads fall due with instalment 1';

CREATE TABLE fee_structure (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    structure_code     VARCHAR(50)  NOT NULL,
    academic_year_id   UUID         NOT NULL,
    academic_class_id  UUID         NOT NULL,
    applies_to         VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    name               VARCHAR(150) NOT NULL,
    remarks            VARCHAR(500) NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    deleted_at         TIMESTAMPTZ  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID         NULL,
    updated_by         UUID         NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_structure PRIMARY KEY (id),
    CONSTRAINT uq_fee_structure_code UNIQUE (structure_code),
    CONSTRAINT fk_fee_structure_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_structure_class FOREIGN KEY (academic_class_id) REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT chk_fee_structure_applies CHECK (applies_to IN ('ALL', 'NEW_ADMISSION', 'EXISTING')),
    CONSTRAINT chk_fee_structure_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);
CREATE UNIQUE INDEX uq_fee_structure_year_class ON fee_structure (academic_year_id, academic_class_id, applies_to) WHERE deleted_at IS NULL;
CREATE INDEX idx_fee_structure_class ON fee_structure(academic_class_id);

COMMENT ON COLUMN fee_structure.applies_to IS 'ALL students of the class, or a separate structure for NEW_ADMISSION / EXISTING students';

CREATE TABLE fee_structure_item (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    fee_structure_id  UUID          NOT NULL,
    fee_head_id       UUID          NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,

    CONSTRAINT pk_fee_structure_item PRIMARY KEY (id),
    CONSTRAINT uq_fee_structure_item UNIQUE (fee_structure_id, fee_head_id),
    CONSTRAINT fk_fee_structure_item_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structure(id) ON DELETE CASCADE,
    CONSTRAINT fk_fee_structure_item_head FOREIGN KEY (fee_head_id) REFERENCES fee_head(id) ON DELETE RESTRICT,
    CONSTRAINT chk_fee_structure_item_amount CHECK (amount >= 0)
);
CREATE INDEX idx_fee_structure_item_head ON fee_structure_item(fee_head_id);

COMMENT ON COLUMN fee_structure_item.amount IS 'Amount per occurrence of the head''s frequency (a MONTHLY head of 2000 is 24000 a year)';

CREATE TABLE fee_concession_type (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    code               VARCHAR(30)   NOT NULL,
    name               VARCHAR(150)  NOT NULL,
    kind               VARCHAR(20)   NOT NULL DEFAULT 'PERCENT',
    value              NUMERIC(12,2) NOT NULL,
    fee_head_id        UUID          NULL,
    requires_approval  BOOLEAN       NOT NULL DEFAULT TRUE,
    description        VARCHAR(255)  NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    deleted_at         TIMESTAMPTZ   NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_concession_type PRIMARY KEY (id),
    CONSTRAINT fk_fee_concession_head FOREIGN KEY (fee_head_id) REFERENCES fee_head(id) ON DELETE RESTRICT,
    CONSTRAINT chk_fee_concession_kind CHECK (kind IN ('PERCENT', 'FIXED')),
    CONSTRAINT chk_fee_concession_value CHECK (value > 0 AND (kind <> 'PERCENT' OR value <= 100)),
    CONSTRAINT chk_fee_concession_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE UNIQUE INDEX uq_fee_concession_code_active ON fee_concession_type (upper(code)) WHERE deleted_at IS NULL;

COMMENT ON TABLE fee_concession_type IS 'Kinds of discount the school gives (sibling, staff ward, scholarship …), on one fee head or on all';

-- ───────────────────────────── Attendance ─────────────────────────────

CREATE TABLE attendance_settings (
    id                          SMALLINT     NOT NULL DEFAULT 1,
    marking_deadline            TIME         NULL,
    late_after                  TIME         NULL,
    backdate_days               INT          NULL,
    late_counts_as              VARCHAR(20)  NOT NULL DEFAULT 'PRESENT',
    minimum_percent             NUMERIC(5,2) NOT NULL DEFAULT 75,
    notify_guardian_on_absence  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by                  UUID         NULL,
    version                     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_attendance_settings PRIMARY KEY (id),
    CONSTRAINT chk_attendance_settings_single CHECK (id = 1),
    CONSTRAINT chk_attendance_settings_backdate CHECK (backdate_days IS NULL OR backdate_days BETWEEN 0 AND 365),
    CONSTRAINT chk_attendance_settings_late CHECK (late_counts_as IN ('PRESENT', 'HALF_DAY', 'ABSENT')),
    CONSTRAINT chk_attendance_settings_min CHECK (minimum_percent BETWEEN 0 AND 100)
);

COMMENT ON COLUMN attendance_settings.backdate_days IS 'How many days back a teacher may mark or correct attendance (NULL = no limit). Staff with whole-school attendance scope are not limited';
COMMENT ON COLUMN attendance_settings.late_counts_as IS 'How a LATE mark counts in the attendance percentage';

-- ───────────────────────────── Transport ─────────────────────────────

CREATE TABLE transport_vehicle (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    vehicle_code          VARCHAR(50)  NOT NULL,
    registration_no       VARCHAR(30)  NOT NULL,
    vehicle_type          VARCHAR(20)  NOT NULL DEFAULT 'BUS',
    capacity              INT          NOT NULL,
    driver_staff_id       UUID         NULL,
    attendant_staff_id    UUID         NULL,
    driver_name           VARCHAR(150) NULL,
    driver_phone          VARCHAR(20)  NULL,
    insurance_valid_till  DATE         NULL,
    fitness_valid_till    DATE         NULL,
    permit_valid_till     DATE         NULL,
    puc_valid_till        DATE         NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at            TIMESTAMPTZ  NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_transport_vehicle PRIMARY KEY (id),
    CONSTRAINT uq_transport_vehicle_code UNIQUE (vehicle_code),
    CONSTRAINT fk_transport_vehicle_driver FOREIGN KEY (driver_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT fk_transport_vehicle_attendant FOREIGN KEY (attendant_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_transport_vehicle_type CHECK (vehicle_type IN ('BUS', 'MINI_BUS', 'VAN', 'CAR', 'OTHER')),
    CONSTRAINT chk_transport_vehicle_capacity CHECK (capacity BETWEEN 1 AND 200),
    CONSTRAINT chk_transport_vehicle_status CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'INACTIVE'))
);
CREATE UNIQUE INDEX uq_transport_vehicle_reg_active ON transport_vehicle (upper(replace(registration_no, ' ', ''))) WHERE deleted_at IS NULL;

CREATE TABLE transport_route (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    route_code   VARCHAR(50)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    vehicle_id   UUID         NULL,
    description  VARCHAR(255) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at   TIMESTAMPTZ  NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_transport_route PRIMARY KEY (id),
    CONSTRAINT uq_transport_route_code UNIQUE (route_code),
    CONSTRAINT fk_transport_route_vehicle FOREIGN KEY (vehicle_id) REFERENCES transport_vehicle(id) ON DELETE SET NULL,
    CONSTRAINT chk_transport_route_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE UNIQUE INDEX uq_transport_route_name_active ON transport_route (lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_transport_route_vehicle ON transport_route(vehicle_id);

CREATE TABLE transport_stop (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    route_id      UUID          NOT NULL,
    stop_no       INT           NOT NULL,
    name          VARCHAR(150)  NOT NULL,
    pickup_time   TIME          NULL,
    drop_time     TIME          NULL,
    distance_km   NUMERIC(6,2)  NULL,
    monthly_fee   NUMERIC(10,2) NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_transport_stop PRIMARY KEY (id),
    CONSTRAINT fk_transport_stop_route FOREIGN KEY (route_id) REFERENCES transport_route(id) ON DELETE CASCADE,
    CONSTRAINT chk_transport_stop_distance CHECK (distance_km IS NULL OR distance_km >= 0),
    CONSTRAINT chk_transport_stop_fee CHECK (monthly_fee IS NULL OR monthly_fee >= 0)
);
CREATE INDEX idx_transport_stop_route ON transport_stop(route_id, stop_no);

-- ────────────────────────────── Library ──────────────────────────────

CREATE TABLE library_settings (
    id                        SMALLINT      NOT NULL DEFAULT 1,
    student_max_books         INT           NOT NULL DEFAULT 2,
    student_loan_days         INT           NOT NULL DEFAULT 14,
    staff_max_books           INT           NOT NULL DEFAULT 5,
    staff_loan_days           INT           NOT NULL DEFAULT 30,
    max_renewals              INT           NOT NULL DEFAULT 1,
    fine_per_day              NUMERIC(8,2)  NOT NULL DEFAULT 0,
    fine_grace_days           INT           NOT NULL DEFAULT 0,
    max_fine                  NUMERIC(10,2) NULL,
    lost_book_charge_percent  NUMERIC(6,2)  NOT NULL DEFAULT 100,
    allow_reservation         BOOLEAN       NOT NULL DEFAULT TRUE,
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by                UUID          NULL,
    version                   BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_library_settings PRIMARY KEY (id),
    CONSTRAINT chk_library_settings_single CHECK (id = 1),
    CONSTRAINT chk_library_settings_limits CHECK (student_max_books BETWEEN 0 AND 50 AND staff_max_books BETWEEN 0 AND 50
        AND student_loan_days BETWEEN 1 AND 365 AND staff_loan_days BETWEEN 1 AND 365 AND max_renewals BETWEEN 0 AND 20),
    CONSTRAINT chk_library_settings_fine CHECK (fine_per_day >= 0 AND fine_grace_days BETWEEN 0 AND 90
        AND (max_fine IS NULL OR max_fine >= 0) AND lost_book_charge_percent BETWEEN 0 AND 500)
);

CREATE TABLE library_category (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(255) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at   TIMESTAMPTZ  NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_library_category PRIMARY KEY (id),
    CONSTRAINT chk_library_category_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE UNIQUE INDEX uq_library_category_code_active ON library_category (upper(code)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_library_category_name_active ON library_category (lower(name)) WHERE deleted_at IS NULL;

-- ─────────────────────────── Communication ───────────────────────────

CREATE TABLE communication_settings (
    id                SMALLINT     NOT NULL DEFAULT 1,
    in_app_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    email_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    sms_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    whatsapp_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    sender_name       VARCHAR(100) NULL,
    reply_to_email    VARCHAR(255) NULL,
    sms_sender_id     VARCHAR(11)  NULL,
    quiet_from        TIME         NULL,
    quiet_to          TIME         NULL,
    default_language  VARCHAR(10)  NOT NULL DEFAULT 'en',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by        UUID         NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_communication_settings PRIMARY KEY (id),
    CONSTRAINT chk_communication_settings_single CHECK (id = 1),
    CONSTRAINT chk_communication_settings_quiet CHECK ((quiet_from IS NULL) = (quiet_to IS NULL))
);

COMMENT ON COLUMN communication_settings.quiet_from IS 'No SMS / WhatsApp between quiet_from and quiet_to (may cross midnight); held until the window ends';

-- One row per event the school has customised; events without a row use the built-in default text.
CREATE TABLE notification_template (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_code  VARCHAR(50)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    channels    VARCHAR(100) NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by  UUID         NULL,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_notification_template PRIMARY KEY (id),
    CONSTRAINT uq_notification_template_event UNIQUE (event_code)
);
