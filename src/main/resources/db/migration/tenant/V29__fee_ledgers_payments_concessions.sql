-- =====================================================================================
-- V29: Fee collection — student ledgers (invoices), payments and receipts, concession
--      requests (maker-checker) and fee reminders. Built on the set-up of V25
--      (fee_head, fee_settings, fee_installment, fee_structure, fee_concession_type).
--      See tenantdata/fee/FEES.md.
--
-- Money is integer paise (set-up amounts in V25 are rupees; they are converted on billing).
-- A line's outstanding due is amount − concession − paid − pending (cheques not yet cleared).
-- Who did what is kept on the rows themselves (collected_by, reversed_by, cancelled_by, reviewed_by ...).
-- =====================================================================================

-- Running numbers per academic year: INV/26/00001, RCP/26/00001, CON/26/00001
CREATE TABLE fee_number_sequence (
    kind              VARCHAR(20)  NOT NULL,
    academic_year_id  UUID         NOT NULL,
    next_value        BIGINT       NOT NULL DEFAULT 1,

    CONSTRAINT pk_fee_number_sequence PRIMARY KEY (kind, academic_year_id),
    CONSTRAINT fk_fee_number_sequence_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE CASCADE,
    CONSTRAINT chk_fee_number_sequence_kind CHECK (kind IN ('INVOICE', 'RECEIPT', 'CONCESSION'))
);

-- One ledger (invoice) per student per academic year
CREATE TABLE fee_ledger (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    invoice_number     VARCHAR(40)  NOT NULL,
    student_id         UUID         NOT NULL,
    academic_year_id   UUID         NOT NULL,
    academic_class_id  UUID         NOT NULL,
    section_id         UUID         NULL,
    fee_structure_id   UUID         NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID         NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_ledger PRIMARY KEY (id),
    CONSTRAINT uq_fee_ledger_invoice UNIQUE (invoice_number),
    CONSTRAINT uq_fee_ledger_student_year UNIQUE (student_id, academic_year_id),
    CONSTRAINT fk_fee_ledger_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_ledger_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_ledger_class FOREIGN KEY (academic_class_id) REFERENCES academic_class(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_ledger_section FOREIGN KEY (section_id) REFERENCES section(id) ON DELETE SET NULL,
    CONSTRAINT fk_fee_ledger_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structure(id) ON DELETE SET NULL
);

CREATE INDEX idx_fee_ledger_year_class ON fee_ledger (academic_year_id, academic_class_id, section_id);

-- What the student owes: one line per fee head per instalment (STRUCTURE) or a one-off charge (CHARGE).
-- installment_no / installment_name are copied, because replacing the instalment plan deletes fee_installment rows.
CREATE TABLE fee_ledger_line (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    ledger_id          UUID          NOT NULL,
    fee_head_id        UUID          NOT NULL,
    installment_id     UUID          NULL,
    installment_no     INT           NULL,
    installment_name   VARCHAR(100)  NULL,
    label              VARCHAR(200)  NOT NULL,
    source             VARCHAR(20)   NOT NULL,
    amount_paise       BIGINT        NOT NULL,
    concession_paise   BIGINT        NOT NULL DEFAULT 0,
    paid_paise         BIGINT        NOT NULL DEFAULT 0,
    pending_paise      BIGINT        NOT NULL DEFAULT 0,
    due_date           DATE          NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    reason             VARCHAR(500)  NULL,
    cancelled_at       TIMESTAMPTZ   NULL,
    cancelled_by       UUID          NULL,
    cancel_reason      VARCHAR(500)  NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         UUID          NULL,
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_ledger_line PRIMARY KEY (id),
    CONSTRAINT fk_fee_line_ledger FOREIGN KEY (ledger_id) REFERENCES fee_ledger(id) ON DELETE CASCADE,
    CONSTRAINT fk_fee_line_head FOREIGN KEY (fee_head_id) REFERENCES fee_head(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_line_installment FOREIGN KEY (installment_id) REFERENCES fee_installment(id) ON DELETE SET NULL,
    CONSTRAINT chk_fee_line_source CHECK (source IN ('STRUCTURE', 'CHARGE')),
    CONSTRAINT chk_fee_line_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT chk_fee_line_amounts CHECK (amount_paise >= 0 AND concession_paise >= 0 AND paid_paise >= 0 AND pending_paise >= 0
                                          AND concession_paise + paid_paise + pending_paise <= amount_paise),
    CONSTRAINT chk_fee_line_installment CHECK (source = 'CHARGE' OR installment_no IS NOT NULL)
);

-- A structure line is billed once, even if it was later cancelled (re-generating does not bring it back)
CREATE UNIQUE INDEX uq_fee_line_structure ON fee_ledger_line (ledger_id, fee_head_id, installment_no) WHERE source = 'STRUCTURE';
CREATE INDEX idx_fee_line_ledger ON fee_ledger_line (ledger_id);
CREATE INDEX idx_fee_line_due ON fee_ledger_line (due_date) WHERE status = 'ACTIVE';
CREATE INDEX idx_fee_line_head ON fee_ledger_line (fee_head_id);

-- Money received. A cheque is PENDING_CLEARANCE (held on the lines as pending_paise) until CLEARED or BOUNCED.
-- realized_on is the day the money counts as collected: the payment date, or the clearance date for cheques.
CREATE TABLE fee_payment (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    receipt_number     VARCHAR(40)   NOT NULL,
    ledger_id          UUID          NOT NULL,
    student_id         UUID          NOT NULL,
    academic_year_id   UUID          NOT NULL,
    amount_paise       BIGINT        NOT NULL,
    mode               VARCHAR(20)   NOT NULL,
    reference_no       VARCHAR(100)  NULL,
    cheque_date        DATE          NULL,
    bank_name          VARCHAR(150)  NULL,
    status             VARCHAR(20)   NOT NULL,
    paid_on            DATE          NOT NULL,
    realized_on        DATE          NULL,
    remarks            VARCHAR(500)  NULL,
    collected_by       UUID          NULL,
    collected_by_name  VARCHAR(200)  NULL,
    status_changed_at  TIMESTAMPTZ   NULL,
    status_changed_by  UUID          NULL,
    status_reason      VARCHAR(500)  NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_payment PRIMARY KEY (id),
    CONSTRAINT uq_fee_payment_receipt UNIQUE (receipt_number),
    CONSTRAINT fk_fee_payment_ledger FOREIGN KEY (ledger_id) REFERENCES fee_ledger(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_payment_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT chk_fee_payment_amount CHECK (amount_paise > 0),
    CONSTRAINT chk_fee_payment_mode CHECK (mode IN ('CASH', 'UPI', 'CARD', 'CHEQUE', 'BANK_TRANSFER', 'DD')),
    CONSTRAINT chk_fee_payment_status CHECK (status IN ('SUCCESS', 'PENDING_CLEARANCE', 'BOUNCED', 'REVERSED')),
    CONSTRAINT chk_fee_payment_realized CHECK (status <> 'SUCCESS' OR realized_on IS NOT NULL)
);

CREATE INDEX idx_fee_payment_ledger ON fee_payment (ledger_id);
CREATE INDEX idx_fee_payment_realized ON fee_payment (realized_on) WHERE status = 'SUCCESS';
CREATE INDEX idx_fee_payment_paid_on ON fee_payment (paid_on);

CREATE TABLE fee_payment_allocation (
    id               UUID    NOT NULL DEFAULT gen_random_uuid(),
    payment_id       UUID    NOT NULL,
    ledger_line_id   UUID    NOT NULL,
    amount_paise     BIGINT  NOT NULL,

    CONSTRAINT pk_fee_payment_allocation PRIMARY KEY (id),
    CONSTRAINT uq_fee_payment_allocation UNIQUE (payment_id, ledger_line_id),
    CONSTRAINT fk_fee_alloc_payment FOREIGN KEY (payment_id) REFERENCES fee_payment(id) ON DELETE CASCADE,
    CONSTRAINT fk_fee_alloc_line FOREIGN KEY (ledger_line_id) REFERENCES fee_ledger_line(id) ON DELETE RESTRICT,
    CONSTRAINT chk_fee_alloc_amount CHECK (amount_paise > 0)
);

CREATE INDEX idx_fee_alloc_line ON fee_payment_allocation (ledger_line_id);

-- A concession asked for one student (sibling, merit, staff ward, EWS ...) and approved by someone else
CREATE TABLE fee_concession_request (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    request_number      VARCHAR(40)   NOT NULL,
    student_id          UUID          NOT NULL,
    academic_year_id    UUID          NOT NULL,
    concession_type_id  UUID          NOT NULL,
    kind                VARCHAR(20)   NOT NULL,
    percent             NUMERIC(5,2)  NULL,
    amount_paise        BIGINT        NULL,
    fee_head_id         UUID          NULL,
    reason              VARCHAR(1000) NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    applied_paise       BIGINT        NOT NULL DEFAULT 0,
    requested_by        UUID          NULL,
    requested_by_name   VARCHAR(200)  NULL,
    reviewed_by         UUID          NULL,
    reviewed_by_name    VARCHAR(200)  NULL,
    reviewed_at         TIMESTAMPTZ   NULL,
    review_remarks      VARCHAR(500)  NULL,
    revoked_by          UUID          NULL,
    revoked_at          TIMESTAMPTZ   NULL,
    revoke_reason       VARCHAR(500)  NULL,
    effective_from      DATE          NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_fee_concession_request PRIMARY KEY (id),
    CONSTRAINT uq_fee_concession_request_number UNIQUE (request_number),
    CONSTRAINT fk_fee_cr_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_cr_year FOREIGN KEY (academic_year_id) REFERENCES academic_year(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_cr_type FOREIGN KEY (concession_type_id) REFERENCES fee_concession_type(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_cr_head FOREIGN KEY (fee_head_id) REFERENCES fee_head(id) ON DELETE RESTRICT,
    CONSTRAINT chk_fee_cr_kind CHECK ((kind = 'PERCENT' AND percent > 0 AND percent <= 100 AND amount_paise IS NULL)
                                   OR (kind = 'FIXED' AND amount_paise > 0 AND percent IS NULL)),
    CONSTRAINT chk_fee_cr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED'))
);

-- One live request per student, year and concession type
CREATE UNIQUE INDEX uq_fee_cr_live ON fee_concession_request (student_id, academic_year_id, concession_type_id)
    WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX idx_fee_cr_year_status ON fee_concession_request (academic_year_id, status);

-- How much of an approved request each line received (so a revoke takes back exactly that)
CREATE TABLE fee_line_concession (
    ledger_line_id  UUID    NOT NULL,
    request_id      UUID    NOT NULL,
    amount_paise    BIGINT  NOT NULL,

    CONSTRAINT pk_fee_line_concession PRIMARY KEY (ledger_line_id, request_id),
    CONSTRAINT fk_fee_lc_line FOREIGN KEY (ledger_line_id) REFERENCES fee_ledger_line(id) ON DELETE CASCADE,
    CONSTRAINT fk_fee_lc_request FOREIGN KEY (request_id) REFERENCES fee_concession_request(id) ON DELETE CASCADE,
    CONSTRAINT chk_fee_lc_amount CHECK (amount_paise > 0)
);

CREATE INDEX idx_fee_lc_request ON fee_line_concession (request_id);

-- Reminders sent to a ledger's guardians (for "last reminded" on the defaulters list)
CREATE TABLE fee_reminder (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    ledger_id   UUID          NOT NULL,
    event_code  VARCHAR(40)   NOT NULL,
    channels    VARCHAR(100)  NOT NULL,
    queued      INT           NOT NULL DEFAULT 0,
    sent_by     UUID          NULL,
    sent_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_fee_reminder PRIMARY KEY (id),
    CONSTRAINT fk_fee_reminder_ledger FOREIGN KEY (ledger_id) REFERENCES fee_ledger(id) ON DELETE CASCADE
);

CREATE INDEX idx_fee_reminder_ledger ON fee_reminder (ledger_id, sent_at DESC);

-- Dashboard action items: concession requests waiting for approval can be dismissed too
ALTER TABLE dashboard_action_dismissal DROP CONSTRAINT chk_dashboard_action_dismissal_type;
ALTER TABLE dashboard_action_dismissal ADD CONSTRAINT chk_dashboard_action_dismissal_type CHECK (item_type IN (
    'LESSON_PLAN_REVIEW', 'MARKS_LOCK', 'REPORT_CARD_PUBLISH', 'REEVALUATION_REQUEST', 'FEE_CONCESSION'
));
