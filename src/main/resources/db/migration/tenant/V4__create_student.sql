-- ============================================================
-- TENANT V4: student
-- Student operational domain entity for the school tenant database
-- ============================================================

CREATE TABLE student (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    student_code        VARCHAR(50)  NOT NULL,
    tenant_user_id      UUID         NOT NULL,
    admission_number    VARCHAR(100) NOT NULL,
    admission_date      DATE         NOT NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_student PRIMARY KEY (id),
    CONSTRAINT uq_student_code UNIQUE (student_code),
    CONSTRAINT uq_student_admission_number UNIQUE (admission_number),
    CONSTRAINT uq_student_tenant_user_id UNIQUE (tenant_user_id),
    CONSTRAINT fk_student_tenant_user FOREIGN KEY (tenant_user_id)
        REFERENCES tenant_user(id) ON DELETE RESTRICT,
    CONSTRAINT chk_student_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'TRANSFERRED', 'GRADUATED', 'WITHDRAWN', 'DELETED'))
);

CREATE INDEX idx_student_code            ON student(student_code);
CREATE INDEX idx_student_admission_no    ON student(admission_number);
CREATE INDEX idx_student_tenant_user     ON student(tenant_user_id);
CREATE INDEX idx_student_status          ON student(status);
CREATE INDEX idx_student_admission_date  ON student(admission_date);
CREATE INDEX idx_student_deleted_at      ON student(deleted_at);

COMMENT ON TABLE  student                  IS 'Student operational record in tenant database';
COMMENT ON COLUMN student.student_code     IS 'Unique business identifier for student (e.g. STU-XYZ12345)';
COMMENT ON COLUMN student.admission_number IS 'School-assigned admission/registration number unique per tenant';
COMMENT ON COLUMN student.tenant_user_id   IS 'References tenant_user operational identity';
COMMENT ON COLUMN student.deleted_at       IS 'Soft deletion timestamp; NULL for active records';
