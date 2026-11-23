-- =====================================================================================
-- V37: Parent engagement — homework, class activities, teacher remarks, announcements,
--      the Student Daily Activity Report and the personal notification inbox.
--
-- HOMEWORK_READ / _MANAGE         read homework / set homework for subjects the user teaches (STAFF scope ALL: any)
-- CLASS_ACTIVITY_READ / _MANAGE   read / record what a class or section did on a day
-- STUDENT_REMARK_READ / _MANAGE   read / write daily remarks about a student (parents see only visible ones)
-- ANNOUNCEMENT_READ / _MANAGE     read / write and publish notices (school-wide needs STUDENT scope ALL)
-- DAILY_REPORT_READ               see a student's daily report (parents: own children)
-- DAILY_REPORT_MANAGE             report settings, run history, send a day's report by hand
-- NOTIFICATION_INBOX_READ         read one's own in-app notifications
--
-- Row access keeps using STUDENT_SCOPE_ALL / _OWN: parents reach only linked children,
-- teachers only the sections and classes they teach.
-- =====================================================================================

INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('HOMEWORK_READ',           'Homework Read',           'View homework set for a class or section',                    'ACADEMICS',     'SCHOOL', NULL),
    ('HOMEWORK_MANAGE',         'Homework Manage',         'Set, change and cancel homework for subjects taught',          'ACADEMICS',     'SCHOOL', NULL),
    ('CLASS_ACTIVITY_READ',     'Class Activity Read',     'View what a class or section did on a day',                    'ACADEMICS',     'SCHOOL', NULL),
    ('CLASS_ACTIVITY_MANAGE',   'Class Activity Manage',   'Record class activities (lab, sports, trip, event ...)',        'ACADEMICS',     'SCHOOL', NULL),
    ('STUDENT_REMARK_READ',     'Student Remark Read',     'View daily teacher remarks about a student',                   'STUDENTS',      'SCHOOL', NULL),
    ('STUDENT_REMARK_MANAGE',   'Student Remark Manage',   'Write daily remarks about students taught',                    'STUDENTS',      'SCHOOL', NULL),
    ('ANNOUNCEMENT_READ',       'Announcement Read',       'View school, class and section announcements',                 'COMMUNICATION', 'SCHOOL', NULL),
    ('ANNOUNCEMENT_MANAGE',     'Announcement Manage',     'Write, publish and withdraw announcements',                    'COMMUNICATION', 'SCHOOL', NULL),
    ('DAILY_REPORT_READ',       'Daily Report Read',       'View the Student Daily Activity Report',                       'COMMUNICATION', 'SCHOOL', NULL),
    ('DAILY_REPORT_MANAGE',     'Daily Report Manage',     'Configure the daily report, see its runs and send it by hand', 'COMMUNICATION', 'SCHOOL', NULL),
    ('NOTIFICATION_INBOX_READ', 'Notification Inbox Read', 'Read one''s own in-app notifications',                         'COMMUNICATION', 'SCHOOL', NULL)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    -- full access
    ('PLATFORM_ADMIN', 'HOMEWORK_READ'), ('PLATFORM_ADMIN', 'HOMEWORK_MANAGE'), ('PLATFORM_ADMIN', 'CLASS_ACTIVITY_READ'),
    ('PLATFORM_ADMIN', 'CLASS_ACTIVITY_MANAGE'), ('PLATFORM_ADMIN', 'STUDENT_REMARK_READ'), ('PLATFORM_ADMIN', 'STUDENT_REMARK_MANAGE'),
    ('PLATFORM_ADMIN', 'ANNOUNCEMENT_READ'), ('PLATFORM_ADMIN', 'ANNOUNCEMENT_MANAGE'), ('PLATFORM_ADMIN', 'DAILY_REPORT_READ'),
    ('PLATFORM_ADMIN', 'DAILY_REPORT_MANAGE'), ('PLATFORM_ADMIN', 'NOTIFICATION_INBOX_READ'),
    ('SCHOOL_OWNER', 'HOMEWORK_READ'), ('SCHOOL_OWNER', 'HOMEWORK_MANAGE'), ('SCHOOL_OWNER', 'CLASS_ACTIVITY_READ'),
    ('SCHOOL_OWNER', 'CLASS_ACTIVITY_MANAGE'), ('SCHOOL_OWNER', 'STUDENT_REMARK_READ'), ('SCHOOL_OWNER', 'STUDENT_REMARK_MANAGE'),
    ('SCHOOL_OWNER', 'ANNOUNCEMENT_READ'), ('SCHOOL_OWNER', 'ANNOUNCEMENT_MANAGE'), ('SCHOOL_OWNER', 'DAILY_REPORT_READ'),
    ('SCHOOL_OWNER', 'DAILY_REPORT_MANAGE'), ('SCHOOL_OWNER', 'NOTIFICATION_INBOX_READ'),
    ('SCHOOL_ADMIN', 'HOMEWORK_READ'), ('SCHOOL_ADMIN', 'HOMEWORK_MANAGE'), ('SCHOOL_ADMIN', 'CLASS_ACTIVITY_READ'),
    ('SCHOOL_ADMIN', 'CLASS_ACTIVITY_MANAGE'), ('SCHOOL_ADMIN', 'STUDENT_REMARK_READ'), ('SCHOOL_ADMIN', 'STUDENT_REMARK_MANAGE'),
    ('SCHOOL_ADMIN', 'ANNOUNCEMENT_READ'), ('SCHOOL_ADMIN', 'ANNOUNCEMENT_MANAGE'), ('SCHOOL_ADMIN', 'DAILY_REPORT_READ'),
    ('SCHOOL_ADMIN', 'DAILY_REPORT_MANAGE'), ('SCHOOL_ADMIN', 'NOTIFICATION_INBOX_READ'),
    ('PRINCIPAL', 'HOMEWORK_READ'), ('PRINCIPAL', 'HOMEWORK_MANAGE'), ('PRINCIPAL', 'CLASS_ACTIVITY_READ'),
    ('PRINCIPAL', 'CLASS_ACTIVITY_MANAGE'), ('PRINCIPAL', 'STUDENT_REMARK_READ'), ('PRINCIPAL', 'STUDENT_REMARK_MANAGE'),
    ('PRINCIPAL', 'ANNOUNCEMENT_READ'), ('PRINCIPAL', 'ANNOUNCEMENT_MANAGE'), ('PRINCIPAL', 'DAILY_REPORT_READ'),
    ('PRINCIPAL', 'DAILY_REPORT_MANAGE'), ('PRINCIPAL', 'NOTIFICATION_INBOX_READ'),
    -- coordinators and teachers: their own classes (row scope), no report settings
    ('COORDINATOR', 'HOMEWORK_READ'), ('COORDINATOR', 'HOMEWORK_MANAGE'), ('COORDINATOR', 'CLASS_ACTIVITY_READ'),
    ('COORDINATOR', 'CLASS_ACTIVITY_MANAGE'), ('COORDINATOR', 'STUDENT_REMARK_READ'), ('COORDINATOR', 'STUDENT_REMARK_MANAGE'),
    ('COORDINATOR', 'ANNOUNCEMENT_READ'), ('COORDINATOR', 'ANNOUNCEMENT_MANAGE'), ('COORDINATOR', 'DAILY_REPORT_READ'),
    ('COORDINATOR', 'NOTIFICATION_INBOX_READ'),
    ('TEACHER', 'HOMEWORK_READ'), ('TEACHER', 'HOMEWORK_MANAGE'), ('TEACHER', 'CLASS_ACTIVITY_READ'),
    ('TEACHER', 'CLASS_ACTIVITY_MANAGE'), ('TEACHER', 'STUDENT_REMARK_READ'), ('TEACHER', 'STUDENT_REMARK_MANAGE'),
    ('TEACHER', 'ANNOUNCEMENT_READ'), ('TEACHER', 'ANNOUNCEMENT_MANAGE'), ('TEACHER', 'DAILY_REPORT_READ'),
    ('TEACHER', 'NOTIFICATION_INBOX_READ'),
    ('SUBJECT_TEACHER', 'HOMEWORK_READ'), ('SUBJECT_TEACHER', 'HOMEWORK_MANAGE'), ('SUBJECT_TEACHER', 'CLASS_ACTIVITY_READ'),
    ('SUBJECT_TEACHER', 'CLASS_ACTIVITY_MANAGE'), ('SUBJECT_TEACHER', 'STUDENT_REMARK_READ'), ('SUBJECT_TEACHER', 'STUDENT_REMARK_MANAGE'),
    ('SUBJECT_TEACHER', 'ANNOUNCEMENT_READ'), ('SUBJECT_TEACHER', 'DAILY_REPORT_READ'), ('SUBJECT_TEACHER', 'NOTIFICATION_INBOX_READ'),
    ('ACCOUNTS', 'ANNOUNCEMENT_READ'), ('ACCOUNTS', 'NOTIFICATION_INBOX_READ'),
    -- families: read-only, own children (STUDENT_SCOPE_OWN)
    ('PARENT', 'HOMEWORK_READ'), ('PARENT', 'CLASS_ACTIVITY_READ'), ('PARENT', 'STUDENT_REMARK_READ'),
    ('PARENT', 'ANNOUNCEMENT_READ'), ('PARENT', 'DAILY_REPORT_READ'), ('PARENT', 'NOTIFICATION_INBOX_READ'),
    ('STUDENT', 'HOMEWORK_READ'), ('STUDENT', 'CLASS_ACTIVITY_READ'), ('STUDENT', 'ANNOUNCEMENT_READ'),
    ('STUDENT', 'DAILY_REPORT_READ'), ('STUDENT', 'NOTIFICATION_INBOX_READ')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
