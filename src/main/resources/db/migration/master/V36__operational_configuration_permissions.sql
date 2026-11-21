-- =====================================================================================
-- V36: Operational configuration (/api/v1/operations-config).
--
-- OPERATIONS_CONFIG_READ    see the readiness overview of fees, attendance, timetable, exams, transport, library, communication
-- FEE_CONFIGURE             fee heads, year settings, instalments, fee structures, concession types   (read: FEE_READ)
-- ATTENDANCE_CONFIGURE      attendance marking rules                                                  (read: ATTENDANCE_READ)
-- TRANSPORT_READ / _MANAGE  vehicles, routes and stops
-- LIBRARY_CONFIGURE         lending rules and book categories                                         (read: LIBRARY_READ)
-- COMMUNICATION_READ / _CONFIGURE  channels and message templates
--
-- Setup is separate from daily work on purpose: FEE_MANAGE (collect fees) and LIBRARY_MANAGE (issue books)
-- stay with the accounts office / librarian, while the school admin can do the setup.
-- Fee, transport and library permissions disappear when the school's plan lacks the feature.
-- =====================================================================================

INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('OPERATIONS_CONFIG_READ',  'Operations Setup Overview', 'See how far fees, attendance, timetable, exams, transport, library and communication are set up', 'GENERAL', 'SCHOOL', NULL),
    ('FEE_CONFIGURE',           'Fee Configure',             'Set up fee heads, instalments, fee structures, late fine and concession types',                  'FEES',          'SCHOOL', 'FEES'),
    ('ATTENDANCE_CONFIGURE',    'Attendance Configure',      'Set the attendance marking rules',                                                               'ATTENDANCE',    'SCHOOL', 'ATTENDANCE'),
    ('TRANSPORT_READ',          'Transport Read',            'View vehicles, routes and stops',                                                                'TRANSPORT',     'SCHOOL', 'TRANSPORT'),
    ('TRANSPORT_MANAGE',        'Transport Manage',          'Manage vehicles, routes and stops',                                                              'TRANSPORT',     'SCHOOL', 'TRANSPORT'),
    ('LIBRARY_CONFIGURE',       'Library Configure',         'Set lending rules and book categories',                                                          'LIBRARY',       'SCHOOL', 'LIBRARY'),
    ('COMMUNICATION_READ',      'Communication Read',        'View channels and message templates',                                                            'COMMUNICATION', 'SCHOOL', NULL),
    ('COMMUNICATION_CONFIGURE', 'Communication Configure',   'Turn channels on or off and edit message templates',                                             'COMMUNICATION', 'SCHOOL', NULL)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('PLATFORM_ADMIN', 'OPERATIONS_CONFIG_READ'), ('PLATFORM_ADMIN', 'FEE_CONFIGURE'), ('PLATFORM_ADMIN', 'ATTENDANCE_CONFIGURE'),
    ('PLATFORM_ADMIN', 'TRANSPORT_READ'), ('PLATFORM_ADMIN', 'TRANSPORT_MANAGE'), ('PLATFORM_ADMIN', 'LIBRARY_CONFIGURE'),
    ('PLATFORM_ADMIN', 'COMMUNICATION_READ'), ('PLATFORM_ADMIN', 'COMMUNICATION_CONFIGURE'),
    ('SCHOOL_OWNER', 'OPERATIONS_CONFIG_READ'), ('SCHOOL_OWNER', 'FEE_CONFIGURE'), ('SCHOOL_OWNER', 'ATTENDANCE_CONFIGURE'),
    ('SCHOOL_OWNER', 'TRANSPORT_READ'), ('SCHOOL_OWNER', 'TRANSPORT_MANAGE'), ('SCHOOL_OWNER', 'LIBRARY_CONFIGURE'),
    ('SCHOOL_OWNER', 'COMMUNICATION_READ'), ('SCHOOL_OWNER', 'COMMUNICATION_CONFIGURE'),
    ('SCHOOL_ADMIN', 'OPERATIONS_CONFIG_READ'), ('SCHOOL_ADMIN', 'FEE_CONFIGURE'), ('SCHOOL_ADMIN', 'ATTENDANCE_CONFIGURE'),
    ('SCHOOL_ADMIN', 'TRANSPORT_READ'), ('SCHOOL_ADMIN', 'TRANSPORT_MANAGE'), ('SCHOOL_ADMIN', 'LIBRARY_CONFIGURE'),
    ('SCHOOL_ADMIN', 'COMMUNICATION_READ'), ('SCHOOL_ADMIN', 'COMMUNICATION_CONFIGURE'),
    ('PRINCIPAL', 'OPERATIONS_CONFIG_READ'), ('PRINCIPAL', 'ATTENDANCE_CONFIGURE'), ('PRINCIPAL', 'TRANSPORT_READ'),
    ('PRINCIPAL', 'COMMUNICATION_READ'),
    ('ACCOUNTS', 'OPERATIONS_CONFIG_READ'), ('ACCOUNTS', 'FEE_CONFIGURE'), ('ACCOUNTS', 'TRANSPORT_READ')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
