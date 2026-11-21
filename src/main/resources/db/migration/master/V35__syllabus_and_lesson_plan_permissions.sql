-- =====================================================================================
-- V35: Academic configuration — syllabus and lesson plans (/api/v1/academic-config).
--
-- SYLLABUS_READ             read syllabi and coverage
-- SYLLABUS_MANAGE           write syllabi (STAFF scope ALL: any; otherwise only subjects the user teaches)
-- SYLLABUS_PROGRESS_UPDATE  mark topics covered in a section the user teaches
-- LESSON_PLAN_READ          read lesson plans (STAFF scope ALL: all; otherwise own)
-- LESSON_PLAN_MANAGE        write, submit and deliver own lesson plans
-- LESSON_PLAN_REVIEW        approve or return submitted lesson plans
--
-- Class-teacher and subject-teacher mapping keeps using the existing
-- CLASS_TEACHER_ASSIGNMENT_* and TEACHER_SUBJECT_ASSIGNMENT_* permissions.
-- =====================================================================================

INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('SYLLABUS_READ',            'Syllabus Read',            'View syllabi and how much each section has covered',     'SUBJECTS', 'SCHOOL', NULL),
    ('SYLLABUS_MANAGE',          'Syllabus Manage',          'Create and edit syllabi (units and topics)',             'SUBJECTS', 'SCHOOL', NULL),
    ('SYLLABUS_PROGRESS_UPDATE', 'Syllabus Progress Update', 'Mark syllabus topics as covered in a section',           'SUBJECTS', 'SCHOOL', NULL),
    ('LESSON_PLAN_READ',         'Lesson Plan Read',         'View lesson plans',                                      'SUBJECTS', 'SCHOOL', NULL),
    ('LESSON_PLAN_MANAGE',       'Lesson Plan Manage',       'Write, submit and deliver lesson plans',                 'SUBJECTS', 'SCHOOL', NULL),
    ('LESSON_PLAN_REVIEW',       'Lesson Plan Review',       'Approve or return submitted lesson plans',               'SUBJECTS', 'SCHOOL', NULL)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('PLATFORM_ADMIN', 'SYLLABUS_READ'), ('PLATFORM_ADMIN', 'SYLLABUS_MANAGE'), ('PLATFORM_ADMIN', 'SYLLABUS_PROGRESS_UPDATE'),
    ('PLATFORM_ADMIN', 'LESSON_PLAN_READ'), ('PLATFORM_ADMIN', 'LESSON_PLAN_MANAGE'), ('PLATFORM_ADMIN', 'LESSON_PLAN_REVIEW'),
    ('SCHOOL_OWNER', 'SYLLABUS_READ'), ('SCHOOL_OWNER', 'SYLLABUS_MANAGE'), ('SCHOOL_OWNER', 'SYLLABUS_PROGRESS_UPDATE'),
    ('SCHOOL_OWNER', 'LESSON_PLAN_READ'), ('SCHOOL_OWNER', 'LESSON_PLAN_MANAGE'), ('SCHOOL_OWNER', 'LESSON_PLAN_REVIEW'),
    ('SCHOOL_ADMIN', 'SYLLABUS_READ'), ('SCHOOL_ADMIN', 'SYLLABUS_MANAGE'), ('SCHOOL_ADMIN', 'SYLLABUS_PROGRESS_UPDATE'),
    ('SCHOOL_ADMIN', 'LESSON_PLAN_READ'), ('SCHOOL_ADMIN', 'LESSON_PLAN_MANAGE'), ('SCHOOL_ADMIN', 'LESSON_PLAN_REVIEW'),
    ('PRINCIPAL', 'SYLLABUS_READ'), ('PRINCIPAL', 'SYLLABUS_MANAGE'), ('PRINCIPAL', 'SYLLABUS_PROGRESS_UPDATE'),
    ('PRINCIPAL', 'LESSON_PLAN_READ'), ('PRINCIPAL', 'LESSON_PLAN_REVIEW'),
    ('COORDINATOR', 'SYLLABUS_READ'), ('COORDINATOR', 'SYLLABUS_MANAGE'), ('COORDINATOR', 'SYLLABUS_PROGRESS_UPDATE'),
    ('COORDINATOR', 'LESSON_PLAN_READ'), ('COORDINATOR', 'LESSON_PLAN_REVIEW'),
    ('TEACHER', 'SYLLABUS_READ'), ('TEACHER', 'SYLLABUS_PROGRESS_UPDATE'),
    ('TEACHER', 'LESSON_PLAN_READ'), ('TEACHER', 'LESSON_PLAN_MANAGE'),
    ('SUBJECT_TEACHER', 'SYLLABUS_READ'), ('SUBJECT_TEACHER', 'SYLLABUS_PROGRESS_UPDATE'),
    ('SUBJECT_TEACHER', 'LESSON_PLAN_READ'), ('SUBJECT_TEACHER', 'LESSON_PLAN_MANAGE')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
