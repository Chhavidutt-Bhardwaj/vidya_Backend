-- =====================================================================================
-- V33: Year-end promotion.
--
-- STUDENT_PROMOTE allows promoting a whole academic year's students into the next year
-- (POST /api/v1/academic-years/{id}/promotion). The endpoint also needs STUDENT scope ALL.
-- =====================================================================================

INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('STUDENT_PROMOTE', 'Student Promote', 'Promote students into the next academic year', 'STUDENTS', 'SCHOOL', 'STUDENT_MANAGEMENT')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO access_permission_matrix_cell (permission_id, section_code, action_code)
SELECT id, 4, -6 FROM access_permission WHERE permission_code = 'STUDENT_PROMOTE'
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('PLATFORM_ADMIN', 'STUDENT_PROMOTE'),
    ('SCHOOL_OWNER', 'STUDENT_PROMOTE'),
    ('SCHOOL_ADMIN', 'STUDENT_PROMOTE'),
    ('PRINCIPAL', 'STUDENT_PROMOTE')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
