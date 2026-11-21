-- =====================================================================================
-- V32: Data-level access for the remaining school modules.
--
-- V31 covered students, attendance and marks. Server-side record checks now also apply to
-- timetables / substitutions (TIMETABLE), staff and teacher assignments (STAFF), and school
-- user accounts (USER). As before, a school user is OWN unless they hold <DOMAIN>_SCOPE_ALL.
--
--   TIMETABLE OWN: own teacher grid, sections they teach, sections their children / they study in.
--   STAFF OWN:     own staff record and own class / subject assignments.
--   USER OWN:      own tenant user account.
--
-- Coordinators read staff lists (STAFF_READ) so they get STAFF_SCOPE_ALL; admins get USER_SCOPE_ALL.
-- See docs/security/DYNAMIC_ACCESS_CONTROL.md
-- =====================================================================================

INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('USER_SCOPE_ALL', 'User Scope: All', 'Works on every user account of the school', 'USERS', 'SCHOOL', NULL)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('PLATFORM_ADMIN', 'USER_SCOPE_ALL'),
    ('SCHOOL_OWNER',   'USER_SCOPE_ALL'),
    ('SCHOOL_ADMIN',   'USER_SCOPE_ALL'),

    ('COORDINATOR',    'STAFF_SCOPE_ALL')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
