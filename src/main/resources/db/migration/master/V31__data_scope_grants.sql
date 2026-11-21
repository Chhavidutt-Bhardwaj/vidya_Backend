-- =====================================================================================
-- V31: Data-level (row) access control.
--
-- For STUDENT, ATTENDANCE and MARKS data, a school user works on either ALL records of the
-- school or only their OWN (self / linked children / students of the sections they teach).
-- ALL is granted by the <DOMAIN>_SCOPE_ALL permission; without it a school user gets OWN.
-- Platform and community accounts are always ALL (their school access is checked separately).
--
-- This migration adds MARKS_SCOPE_ALL and grants the *_SCOPE_ALL permissions to the roles
-- that oversee the whole school. Teachers, subject teachers, parents and students stay OWN.
-- See docs/security/DYNAMIC_ACCESS_CONTROL.md
-- =====================================================================================

INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('MARKS_SCOPE_ALL', 'Marks Scope: All', 'Works on marks of the whole school', 'EXAMINATION', 'SCHOOL', 'EXAMINATION')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO access_permission_matrix_cell (permission_id, section_code, action_code)
SELECT id, 14, -4 FROM access_permission WHERE permission_code = 'MARKS_SCOPE_ALL'
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('PLATFORM_ADMIN', 'MARKS_SCOPE_ALL'),

    ('SCHOOL_OWNER', 'MARKS_SCOPE_ALL'),
    ('SCHOOL_ADMIN', 'MARKS_SCOPE_ALL'),

    ('PRINCIPAL', 'ATTENDANCE_SCOPE_ALL'),
    ('PRINCIPAL', 'MARKS_SCOPE_ALL'),

    ('COORDINATOR', 'STUDENT_SCOPE_ALL'),
    ('COORDINATOR', 'ATTENDANCE_SCOPE_ALL'),
    ('COORDINATOR', 'MARKS_SCOPE_ALL'),
    ('COORDINATOR', 'TIMETABLE_SCOPE_ALL'),

    ('ACCOUNTS', 'STUDENT_SCOPE_ALL')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
