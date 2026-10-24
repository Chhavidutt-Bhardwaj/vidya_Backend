-- V18: Grading Engine & Report Card Generation (Step 26)
-- Creates report card generation, subject-level result aggregation, and teacher/principal remarks.

-- 1. Student Report Card Master
CREATE TABLE student_report_card (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_card_code VARCHAR(50) NOT NULL,
    exam_term_id UUID NOT NULL REFERENCES exam_term(id) ON DELETE RESTRICT,
    academic_year_id UUID NOT NULL REFERENCES academic_year(id) ON DELETE RESTRICT,
    academic_class_id UUID NOT NULL REFERENCES academic_class(id) ON DELETE RESTRICT,
    section_id UUID REFERENCES class_section(id) ON DELETE SET NULL,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE RESTRICT,
    total_max_marks NUMERIC(8,2) NOT NULL DEFAULT 0.00,
    total_marks_obtained NUMERIC(8,2) NOT NULL DEFAULT 0.00,
    percentage NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    overall_grade VARCHAR(10),
    cgpa NUMERIC(4,2),
    section_rank INTEGER,
    class_rank INTEGER,
    attendance_working_days INTEGER NOT NULL DEFAULT 0,
    attendance_present_days INTEGER NOT NULL DEFAULT 0,
    attendance_percentage NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    result_status VARCHAR(30) NOT NULL DEFAULT 'PASSED',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_report_card_result_status CHECK (result_status IN ('PASSED', 'FAILED', 'COMPARTMENT', 'WITHHELD')),
    CONSTRAINT chk_report_card_status CHECK (status IN ('DRAFT', 'GENERATED', 'PUBLISHED', 'REVOKED'))
);

CREATE UNIQUE INDEX idx_report_card_code ON student_report_card(report_card_code);
CREATE UNIQUE INDEX idx_report_card_term_student ON student_report_card(exam_term_id, student_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_report_card_class_section ON student_report_card(academic_class_id, section_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_report_card_student ON student_report_card(student_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_report_card_status ON student_report_card(status) WHERE deleted_at IS NULL;

-- 2. Report Card Subject Result
CREATE TABLE report_card_subject_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_card_id UUID NOT NULL REFERENCES student_report_card(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id) ON DELETE RESTRICT,
    subject_name VARCHAR(150) NOT NULL,
    subject_code VARCHAR(50) NOT NULL,
    max_marks NUMERIC(6,2) NOT NULL,
    pass_marks NUMERIC(6,2) NOT NULL,
    theory_marks NUMERIC(6,2),
    practical_marks NUMERIC(6,2),
    marks_obtained NUMERIC(6,2),
    percentage NUMERIC(5,2),
    grade VARCHAR(10),
    grade_point NUMERIC(4,2),
    is_passing BOOLEAN NOT NULL DEFAULT TRUE,
    is_absent BOOLEAN NOT NULL DEFAULT FALSE,
    is_exempt BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rc_subj_report_card ON report_card_subject_result(report_card_id);

-- 3. Report Card Remarks (Class Teacher, Principal, Counselor)
CREATE TABLE report_card_remark (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_card_id UUID NOT NULL REFERENCES student_report_card(id) ON DELETE CASCADE,
    author_staff_id UUID REFERENCES staff(id) ON DELETE SET NULL,
    author_role VARCHAR(50) NOT NULL DEFAULT 'CLASS_TEACHER',
    remark TEXT NOT NULL,
    conduct_rating VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rc_remark_author_role CHECK (author_role IN ('CLASS_TEACHER', 'PRINCIPAL', 'COUNSELOR')),
    CONSTRAINT chk_rc_remark_conduct CHECK (conduct_rating IS NULL OR conduct_rating IN ('EXCELLENT', 'VERY_GOOD', 'GOOD', 'SATISFACTORY', 'NEEDS_IMPROVEMENT'))
);

CREATE UNIQUE INDEX idx_report_card_remark_role ON report_card_remark(report_card_id, author_role);
