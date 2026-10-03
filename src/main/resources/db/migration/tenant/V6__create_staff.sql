-- ============================================================
-- TENANT V6: Staff & Teacher Management Foundation
-- Staff operational domain entity for the school tenant database
-- ============================================================

CREATE TABLE staff (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    staff_code          VARCHAR(50)  NOT NULL,
    tenant_user_id      UUID         NOT NULL,
    employee_code       VARCHAR(100) NULL,
    joining_date        DATE         NULL,
    employment_status   VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    staff_type          VARCHAR(30)  NOT NULL,
    designation         VARCHAR(150) NULL,
    department          VARCHAR(150) NULL,
    deleted_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID         NULL,
    updated_by          UUID         NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_staff PRIMARY KEY (id),
    CONSTRAINT uq_staff_code UNIQUE (staff_code),
    CONSTRAINT fk_staff_tenant_user FOREIGN KEY (tenant_user_id)
        REFERENCES tenant_user(id) ON DELETE RESTRICT,
    CONSTRAINT chk_staff_employment_status CHECK (
        employment_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'RESIGNED', 'TERMINATED', 'RETIRED', 'DELETED')
    ),
    CONSTRAINT chk_staff_type CHECK (
        staff_type IN ('TEACHER', 'NON_TEACHING', 'ADMINISTRATIVE', 'SUPPORT', 'OTHER')
    )
);

-- At most one non-deleted staff record per tenant_user_id (prevents dual-staff for same identity)
CREATE UNIQUE INDEX idx_staff_unique_active_user ON staff(tenant_user_id)
    WHERE (deleted_at IS NULL);

-- Unique employee code among non-deleted records (school-assigned code)
CREATE UNIQUE INDEX idx_staff_unique_active_employee_code ON staff(employee_code)
    WHERE (deleted_at IS NULL AND employee_code IS NOT NULL);

CREATE INDEX idx_staff_code              ON staff(staff_code);
CREATE INDEX idx_staff_tenant_user       ON staff(tenant_user_id);
CREATE INDEX idx_staff_employment_status ON staff(employment_status);
CREATE INDEX idx_staff_type              ON staff(staff_type);
CREATE INDEX idx_staff_department        ON staff(department);
CREATE INDEX idx_staff_designation       ON staff(designation);
CREATE INDEX idx_staff_joining_date      ON staff(joining_date);
CREATE INDEX idx_staff_deleted_at        ON staff(deleted_at);

COMMENT ON TABLE  staff                        IS 'Staff and teacher operational records in tenant database';
COMMENT ON COLUMN staff.staff_code             IS 'Stable Vidya-domain identifier for the staff member (e.g. STF-8A92K3LM). Immutable after creation.';
COMMENT ON COLUMN staff.tenant_user_id         IS 'References tenant_user operational identity. At most one non-deleted staff per user.';
COMMENT ON COLUMN staff.employee_code          IS 'School-assigned employee identifier (e.g. EMP-1024). Optional, unique among non-deleted records.';
COMMENT ON COLUMN staff.employment_status      IS 'Lifecycle employment status with controlled state machine transitions.';
COMMENT ON COLUMN staff.staff_type             IS 'Operational category: TEACHER, NON_TEACHING, ADMINISTRATIVE, SUPPORT, OTHER.';
COMMENT ON COLUMN staff.deleted_at             IS 'Soft deletion timestamp. NULL for active records. Historical records retained for audit integrity.';
