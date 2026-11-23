-- =====================================================================================
-- V30: Move the frontend "permissionsMatrix" (section code -> action codes) into the DB.
--
-- Before: UserPermissionMatrixServiceImpl hardcoded a matrix per account type, separately
-- from the real permissions, so the UI and the API could disagree.
-- After:  each permission can light up one or more matrix cells. A user's matrix is
-- the cells of the permissions they effectively hold (roles + overrides + feature gating).
--
-- Section codes:  4 Students, 11 Staff, 12 Attendance, 13 Fees, 14 Marks/Exams,
--                 15 Timetable, 16 Chat, 17 Library, 20 Community
-- Action codes:  -9 VIEW, -8 EDIT, -7 DELETE, -6 APPROVE, -5 SCOPED (own records), -4 FULL (all records)
--
-- See docs/security/DYNAMIC_ACCESS_CONTROL.md
-- =====================================================================================

CREATE TABLE access_permission_matrix_cell (
    permission_id UUID    NOT NULL,
    section_code  INTEGER NOT NULL,
    action_code   INTEGER NOT NULL,

    CONSTRAINT pk_access_permission_matrix_cell PRIMARY KEY (permission_id, section_code, action_code),
    CONSTRAINT fk_access_permission_matrix_cell_permission
        FOREIGN KEY (permission_id) REFERENCES access_permission(id) ON DELETE CASCADE,
    CONSTRAINT chk_access_permission_matrix_cell_section CHECK (section_code BETWEEN 1 AND 999),
    CONSTRAINT chk_access_permission_matrix_cell_action  CHECK (action_code BETWEEN -9 AND -4)
);

COMMENT ON TABLE access_permission_matrix_cell IS
    'Frontend permissionsMatrix cells shown when a user holds the permission (section_code -> action_code).';

-- -------------------------------------------------------------------------------------
-- 1. Permissions for sections that had matrix entries but no backend permissions yet.
--    FEE_* / LIBRARY_* / CHAT_* are ready for those modules' endpoints.
--    *_SCOPE_ALL / *_SCOPE_OWN say whether the holder works on all records of the school
--    or only their own (class, child, self). They replace the old FULL / SCOPED actions.
--    No endpoint enforces the scope markers yet.
-- -------------------------------------------------------------------------------------
INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('FEE_READ',             'Fee Read',              'View fee structures, dues and receipts',               'FEES',       'SCHOOL', 'FEES'),
    ('FEE_MANAGE',           'Fee Manage',            'Create fee heads, collect fees, issue receipts',       'FEES',       'SCHOOL', 'FEES'),
    ('FEE_PAY',              'Fee Pay',               'Pay fees online (parents)',                            'FEES',       'SCHOOL', 'FEES'),
    ('FEE_APPROVE',          'Fee Approve',           'Approve concessions, waivers and refunds',             'FEES',       'SCHOOL', 'FEES'),
    ('FEE_SCOPE_ALL',        'Fee Scope: All',        'Works on fees of the whole school',                    'FEES',       'SCHOOL', 'FEES'),
    ('LIBRARY_READ',         'Library Read',          'Browse catalog and issue history',                     'LIBRARY',    'SCHOOL', 'LIBRARY'),
    ('LIBRARY_MANAGE',       'Library Manage',        'Manage catalog, issue and return books',               'LIBRARY',    'SCHOOL', 'LIBRARY'),
    ('CHAT_READ',            'Chat Read',             'Read school messages',                                 'CHAT',       'SCHOOL', NULL),
    ('CHAT_SEND',            'Chat Send',             'Send school messages',                                 'CHAT',       'SCHOOL', NULL),
    ('CHAT_SCOPE_OWN',       'Chat Scope: Own',       'Only own class / child conversations',                 'CHAT',       'SCHOOL', NULL),
    ('STUDENT_SCOPE_ALL',    'Student Scope: All',    'Works on every student of the school',                 'STUDENTS',   'SCHOOL', 'STUDENT_MANAGEMENT'),
    ('STUDENT_SCOPE_OWN',    'Student Scope: Own',    'Only own class / own child / self',                    'STUDENTS',   'SCHOOL', 'STUDENT_MANAGEMENT'),
    ('STAFF_SCOPE_ALL',      'Staff Scope: All',      'Works on every staff member of the school',            'STAFF',      'SCHOOL', 'STAFF_MANAGEMENT'),
    ('ATTENDANCE_SCOPE_ALL', 'Attendance Scope: All', 'Works on attendance of the whole school',              'ATTENDANCE', 'SCHOOL', 'ATTENDANCE'),
    ('ATTENDANCE_SCOPE_OWN', 'Attendance Scope: Own', 'Only own class / own child / self',                    'ATTENDANCE', 'SCHOOL', 'ATTENDANCE'),
    ('MARKS_SCOPE_OWN',      'Marks Scope: Own',      'Only own class / subject',                             'EXAMINATION','SCHOOL', 'EXAMINATION'),
    ('TIMETABLE_SCOPE_ALL',  'Timetable Scope: All',  'Works on the whole school timetable',                  'TIMETABLE',  'SCHOOL', 'TIMETABLE'),
    ('TIMETABLE_SCOPE_OWN',  'Timetable Scope: Own',  'Only own class / own timetable',                       'TIMETABLE',  'SCHOOL', 'TIMETABLE')
ON CONFLICT (permission_code) DO NOTHING;

-- -------------------------------------------------------------------------------------
-- 2. Matrix cells
-- -------------------------------------------------------------------------------------
INSERT INTO access_permission_matrix_cell (permission_id, section_code, action_code)
SELECT p.id, m.section_code, m.action_code
FROM (VALUES
    -- Students (4)
    ('STUDENT_READ', 4, -9), ('STUDENT_GUARDIAN_READ', 4, -9), ('STUDENT_ACADEMIC_ASSIGNMENT_READ', 4, -9),
    ('STUDENT_CREATE', 4, -8), ('STUDENT_UPDATE', 4, -8), ('STUDENT_ENROLL', 4, -8),
    ('STUDENT_GUARDIAN_CREATE', 4, -8), ('STUDENT_GUARDIAN_UPDATE', 4, -8),
    ('STUDENT_ACADEMIC_ASSIGNMENT_CREATE', 4, -8), ('STUDENT_ACADEMIC_ASSIGNMENT_UPDATE', 4, -8),
    ('STUDENT_DELETE', 4, -7), ('STUDENT_WITHDRAW', 4, -7), ('STUDENT_GUARDIAN_DELETE', 4, -7),
    ('STUDENT_TRANSFER', 4, -6),
    ('STUDENT_SCOPE_OWN', 4, -5), ('STUDENT_SCOPE_ALL', 4, -4),
    -- Staff (11)
    ('STAFF_READ', 11, -9), ('STAFF_CREATE', 11, -8), ('STAFF_UPDATE', 11, -8),
    ('STAFF_DELETE', 11, -7), ('STAFF_STATUS_UPDATE', 11, -6), ('STAFF_SCOPE_ALL', 11, -4),
    -- Attendance (12)
    ('ATTENDANCE_READ', 12, -9),
    ('ATTENDANCE_CREATE', 12, -8), ('ATTENDANCE_UPDATE', 12, -8), ('ATTENDANCE_BULK_MARK', 12, -8),
    ('ATTENDANCE_DELETE', 12, -7),
    ('ATTENDANCE_STATUS_UPDATE', 12, -6), ('ATTENDANCE_OVERRIDE_NON_WORKING', 12, -6),
    ('ATTENDANCE_SCOPE_OWN', 12, -5), ('ATTENDANCE_SCOPE_ALL', 12, -4),
    -- Fees (13)
    ('FEE_READ', 13, -9), ('FEE_MANAGE', 13, -8), ('FEE_PAY', 13, -8), ('FEE_APPROVE', 13, -6), ('FEE_SCOPE_ALL', 13, -4),
    -- Marks / exams (14)
    ('MARKS_READ', 14, -9), ('EXAM_READ', 14, -9), ('REPORT_CARD_READ', 14, -9),
    ('MARKS_ENTRY', 14, -8), ('EXAM_CREATE', 14, -8), ('EXAM_UPDATE', 14, -8),
    ('REPORT_CARD_GENERATE', 14, -8), ('REPORT_CARD_REMARK_MANAGE', 14, -8),
    ('EXAM_DELETE', 14, -7), ('REPORT_CARD_REVOKE', 14, -7),
    ('MARKS_LOCK', 14, -6), ('EXAM_PUBLISH', 14, -6), ('REPORT_CARD_PUBLISH', 14, -6),
    ('MARKS_SCOPE_OWN', 14, -5),
    -- Timetable (15)
    ('TIMETABLE_READ', 15, -9), ('TIMETABLE_ENTRY_READ', 15, -9),
    ('TIMETABLE_CREATE', 15, -8), ('TIMETABLE_UPDATE', 15, -8),
    ('TIMETABLE_ENTRY_CREATE', 15, -8), ('TIMETABLE_ENTRY_UPDATE', 15, -8),
    ('TIMETABLE_DELETE', 15, -7), ('TIMETABLE_ENTRY_DELETE', 15, -7),
    ('TIMETABLE_PUBLISH', 15, -6), ('TIMETABLE_VALIDATE', 15, -6),
    ('TIMETABLE_SCOPE_OWN', 15, -5), ('TIMETABLE_SCOPE_ALL', 15, -4),
    -- Chat (16)
    ('CHAT_READ', 16, -9), ('CHAT_SEND', 16, -8), ('CHAT_SCOPE_OWN', 16, -5),
    -- Library (17)
    ('LIBRARY_READ', 17, -9), ('LIBRARY_MANAGE', 17, -8),
    -- Community (20)
    ('COMMUNITY_READ', 20, -9), ('COMMUNITY_CREATE', 20, -8), ('COMMUNITY_UPDATE', 20, -8),
    ('COMMUNITY_DELETE', 20, -7), ('COMMUNITY_BANK_DETAILS_APPROVE', 20, -6)
) AS m(permission_code, section_code, action_code)
JOIN access_permission p ON p.permission_code = m.permission_code
ON CONFLICT DO NOTHING;

-- -------------------------------------------------------------------------------------
-- 3. Grant the new permissions as the old hardcoded matrix did
--    (PLATFORM_SUPER_ADMIN already has everything through grants_all_permissions).
-- -------------------------------------------------------------------------------------
INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('PLATFORM_ADMIN', 'FEE_READ'), ('PLATFORM_ADMIN', 'FEE_MANAGE'), ('PLATFORM_ADMIN', 'FEE_APPROVE'),
    ('PLATFORM_ADMIN', 'FEE_SCOPE_ALL'), ('PLATFORM_ADMIN', 'LIBRARY_READ'), ('PLATFORM_ADMIN', 'LIBRARY_MANAGE'),
    ('PLATFORM_ADMIN', 'CHAT_READ'), ('PLATFORM_ADMIN', 'STUDENT_SCOPE_ALL'), ('PLATFORM_ADMIN', 'STAFF_SCOPE_ALL'),
    ('PLATFORM_ADMIN', 'ATTENDANCE_SCOPE_ALL'), ('PLATFORM_ADMIN', 'TIMETABLE_SCOPE_ALL'),

    ('SCHOOL_OWNER', 'FEE_READ'), ('SCHOOL_OWNER', 'LIBRARY_READ'), ('SCHOOL_OWNER', 'CHAT_READ'),
    ('SCHOOL_OWNER', 'STUDENT_SCOPE_ALL'), ('SCHOOL_OWNER', 'STAFF_SCOPE_ALL'),
    ('SCHOOL_OWNER', 'ATTENDANCE_SCOPE_ALL'), ('SCHOOL_OWNER', 'TIMETABLE_SCOPE_ALL'),

    ('SCHOOL_ADMIN', 'FEE_READ'), ('SCHOOL_ADMIN', 'LIBRARY_READ'), ('SCHOOL_ADMIN', 'CHAT_READ'),
    ('SCHOOL_ADMIN', 'STUDENT_SCOPE_ALL'), ('SCHOOL_ADMIN', 'STAFF_SCOPE_ALL'),
    ('SCHOOL_ADMIN', 'ATTENDANCE_SCOPE_ALL'), ('SCHOOL_ADMIN', 'TIMETABLE_SCOPE_ALL'),

    ('PRINCIPAL', 'FEE_READ'), ('PRINCIPAL', 'FEE_APPROVE'), ('PRINCIPAL', 'LIBRARY_READ'), ('PRINCIPAL', 'CHAT_READ'),
    ('PRINCIPAL', 'STUDENT_SCOPE_ALL'), ('PRINCIPAL', 'STAFF_SCOPE_ALL'), ('PRINCIPAL', 'TIMETABLE_SCOPE_ALL'),

    ('COORDINATOR', 'CHAT_READ'),

    ('TEACHER', 'CHAT_READ'), ('TEACHER', 'CHAT_SEND'), ('TEACHER', 'CHAT_SCOPE_OWN'),
    ('TEACHER', 'STUDENT_SCOPE_OWN'), ('TEACHER', 'ATTENDANCE_SCOPE_OWN'),
    ('TEACHER', 'MARKS_SCOPE_OWN'), ('TEACHER', 'TIMETABLE_SCOPE_OWN'),

    ('SUBJECT_TEACHER', 'STUDENT_SCOPE_OWN'), ('SUBJECT_TEACHER', 'ATTENDANCE_SCOPE_OWN'),
    ('SUBJECT_TEACHER', 'MARKS_SCOPE_OWN'), ('SUBJECT_TEACHER', 'TIMETABLE_SCOPE_OWN'),

    ('ACCOUNTS', 'FEE_READ'), ('ACCOUNTS', 'FEE_MANAGE'), ('ACCOUNTS', 'FEE_SCOPE_ALL'),
    ('ACCOUNTS', 'LIBRARY_READ'), ('ACCOUNTS', 'LIBRARY_MANAGE'),

    ('PARENT', 'FEE_READ'), ('PARENT', 'FEE_PAY'), ('PARENT', 'CHAT_READ'), ('PARENT', 'CHAT_SEND'),
    ('PARENT', 'CHAT_SCOPE_OWN'), ('PARENT', 'STUDENT_SCOPE_OWN'), ('PARENT', 'ATTENDANCE_SCOPE_OWN'),

    ('STUDENT', 'LIBRARY_READ'), ('STUDENT', 'STUDENT_SCOPE_OWN'), ('STUDENT', 'ATTENDANCE_SCOPE_OWN')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
