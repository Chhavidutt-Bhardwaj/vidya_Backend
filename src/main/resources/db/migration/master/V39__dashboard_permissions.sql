-- =====================================================================================
-- V39: School overview dashboard (tenantdata/dashboard, see DASHBOARD.md).
--
-- DASHBOARD_READ   open the school overview: KPIs, school snapshot, operations pulse, quick counts,
--                  attendance trend, enrolment by class, action items and the campus list.
--
-- Holding DASHBOARD_READ is not enough to see a number: each block also needs that module's read
-- permission and school-wide (ALL) data scope, otherwise the block is left out and listed in
-- `unavailable`. Teachers, parents and students do not get the overview (they have their own homes).
-- =====================================================================================

INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('DASHBOARD_READ', 'Dashboard Read', 'View the school overview dashboard and dismiss own action items', 'DASHBOARD', 'SCHOOL', NULL)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM access_role r
JOIN access_permission p ON p.permission_code = 'DASHBOARD_READ'
WHERE r.school_id IS NULL
  AND r.role_code IN ('PLATFORM_ADMIN', 'SCHOOL_OWNER', 'SCHOOL_ADMIN', 'PRINCIPAL', 'COORDINATOR')
ON CONFLICT DO NOTHING;
