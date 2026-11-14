-- =====================================================================================
-- V38: Multi-board assessment engine (tenant V27) — schemes, competencies, formative
--      assessments, moderation / re-evaluation, question bank, papers, online exams,
--      OMR and analytics. See tenantdata/assessment/ASSESSMENT_ENGINE.md.
--
-- ASSESSMENT_SCHEME_READ / _MANAGE  board schemes (CBSE, ICSE, ISC, State, IB, IGCSE, custom) per class
-- COMPETENCY_READ / _MANAGE         competencies, learning outcomes, topic mapping
-- ASSESSMENT_READ / _MANAGE         formative assessments, competency ratings, co-scholastic grades
-- QUESTION_BANK_READ / _MANAGE      questions and blueprints; _REVIEW approves others' questions
-- QUESTION_PAPER_MANAGE             blueprints, generated / assembled papers, answer keys
-- ONLINE_EXAM_MANAGE / _TAKE        set and mark online exams / sit them (students)
-- OMR_MANAGE                        evaluate and post OMR sheets (posting also needs MARKS_ENTRY)
-- MARKS_MODERATE                    approve grace / moderation, apply moderation, unlock assessments
-- REEVALUATION_REQUEST / _MANAGE    ask for re-evaluation (families) / handle requests
-- ASSESSMENT_ANALYTICS_READ         subject, year-over-year, outcome and item analytics
--
-- Teachers act only on subjects and classes they teach (row scope as for marks).
-- =====================================================================================

INSERT INTO access_permission (permission_code, name, description, module_code, scope, feature_code) VALUES
    ('ASSESSMENT_SCHEME_READ',    'Assessment Scheme Read',    'View board assessment schemes',                                      'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('ASSESSMENT_SCHEME_MANAGE',  'Assessment Scheme Manage',  'Create, change and activate board assessment schemes',                'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('COMPETENCY_READ',           'Competency Read',           'View competencies and learning outcomes',                            'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('COMPETENCY_MANAGE',         'Competency Manage',         'Define competencies, learning outcomes and topic mapping',           'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('ASSESSMENT_READ',           'Assessment Read',           'View formative assessments, ratings and co-scholastic grades',       'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('ASSESSMENT_MANAGE',         'Assessment Manage',         'Run formative assessments and record ratings and grades',            'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('QUESTION_BANK_READ',        'Question Bank Read',        'View the question bank, blueprints and papers',                      'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('QUESTION_BANK_MANAGE',      'Question Bank Manage',      'Write and retire questions; run item analysis',                      'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('QUESTION_BANK_REVIEW',      'Question Bank Review',      'Approve or return questions written by others',                      'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('QUESTION_PAPER_MANAGE',     'Question Paper Manage',     'Design blueprints, generate and finalise papers, see answer keys',   'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('ONLINE_EXAM_MANAGE',        'Online Exam Manage',        'Set online exams, mark written answers, release and post results',  'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('ONLINE_EXAM_TAKE',          'Online Exam Take',          'Sit online exams of one''s class',                                   'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('OMR_MANAGE',                'OMR Manage',                'Evaluate OMR sheets and post their marks',                           'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('MARKS_MODERATE',            'Marks Moderate',            'Approve grace and moderation, apply moderation, unlock assessments', 'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('REEVALUATION_REQUEST',      'Re-evaluation Request',     'Ask for re-evaluation of a published result',                        'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('REEVALUATION_MANAGE',       'Re-evaluation Manage',      'Assign and decide re-evaluation requests',                           'EXAMINATION', 'SCHOOL', 'EXAMINATION'),
    ('ASSESSMENT_ANALYTICS_READ', 'Assessment Analytics Read', 'Subject, year-over-year, outcome and question analytics',            'EXAMINATION', 'SCHOOL', 'EXAMINATION')
ON CONFLICT (permission_code) DO NOTHING;

-- Marks / exams section (14): -9 VIEW, -8 EDIT, -6 APPROVE
INSERT INTO access_permission_matrix_cell (permission_id, section_code, action_code)
SELECT p.id, 14, c.action_code
FROM (VALUES
    ('ASSESSMENT_SCHEME_READ', -9), ('COMPETENCY_READ', -9), ('ASSESSMENT_READ', -9), ('QUESTION_BANK_READ', -9),
    ('ASSESSMENT_ANALYTICS_READ', -9), ('ONLINE_EXAM_TAKE', -9), ('REEVALUATION_REQUEST', -9),
    ('ASSESSMENT_SCHEME_MANAGE', -8), ('COMPETENCY_MANAGE', -8), ('ASSESSMENT_MANAGE', -8), ('QUESTION_BANK_MANAGE', -8),
    ('QUESTION_PAPER_MANAGE', -8), ('ONLINE_EXAM_MANAGE', -8), ('OMR_MANAGE', -8),
    ('QUESTION_BANK_REVIEW', -6), ('MARKS_MODERATE', -6), ('REEVALUATION_MANAGE', -6)
) AS c(permission_code, action_code)
JOIN access_permission p ON p.permission_code = c.permission_code
ON CONFLICT DO NOTHING;

-- School leadership: everything
INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM access_role r
CROSS JOIN access_permission p
WHERE r.school_id IS NULL
  AND r.role_code IN ('PLATFORM_ADMIN', 'SCHOOL_OWNER', 'SCHOOL_ADMIN', 'PRINCIPAL')
  AND p.permission_code IN ('ASSESSMENT_SCHEME_READ', 'ASSESSMENT_SCHEME_MANAGE', 'COMPETENCY_READ', 'COMPETENCY_MANAGE',
                            'ASSESSMENT_READ', 'ASSESSMENT_MANAGE', 'QUESTION_BANK_READ', 'QUESTION_BANK_MANAGE', 'QUESTION_BANK_REVIEW',
                            'QUESTION_PAPER_MANAGE', 'ONLINE_EXAM_MANAGE', 'OMR_MANAGE', 'MARKS_MODERATE',
                            'REEVALUATION_MANAGE', 'ASSESSMENT_ANALYTICS_READ')
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    -- coordinators: run the exam cell for their classes (no scheme changes)
    ('COORDINATOR', 'ASSESSMENT_SCHEME_READ'), ('COORDINATOR', 'COMPETENCY_READ'), ('COORDINATOR', 'COMPETENCY_MANAGE'),
    ('COORDINATOR', 'ASSESSMENT_READ'), ('COORDINATOR', 'ASSESSMENT_MANAGE'), ('COORDINATOR', 'QUESTION_BANK_READ'),
    ('COORDINATOR', 'QUESTION_BANK_MANAGE'), ('COORDINATOR', 'QUESTION_BANK_REVIEW'), ('COORDINATOR', 'QUESTION_PAPER_MANAGE'),
    ('COORDINATOR', 'ONLINE_EXAM_MANAGE'), ('COORDINATOR', 'OMR_MANAGE'), ('COORDINATOR', 'MARKS_MODERATE'),
    ('COORDINATOR', 'REEVALUATION_MANAGE'), ('COORDINATOR', 'ASSESSMENT_ANALYTICS_READ'),
    -- teachers: subjects and classes they teach; peer review of questions (never their own)
    ('TEACHER', 'ASSESSMENT_SCHEME_READ'), ('TEACHER', 'COMPETENCY_READ'), ('TEACHER', 'ASSESSMENT_READ'),
    ('TEACHER', 'ASSESSMENT_MANAGE'), ('TEACHER', 'QUESTION_BANK_READ'), ('TEACHER', 'QUESTION_BANK_MANAGE'),
    ('TEACHER', 'QUESTION_BANK_REVIEW'), ('TEACHER', 'QUESTION_PAPER_MANAGE'), ('TEACHER', 'ONLINE_EXAM_MANAGE'),
    ('TEACHER', 'OMR_MANAGE'), ('TEACHER', 'ASSESSMENT_ANALYTICS_READ'),
    ('SUBJECT_TEACHER', 'ASSESSMENT_SCHEME_READ'), ('SUBJECT_TEACHER', 'COMPETENCY_READ'), ('SUBJECT_TEACHER', 'ASSESSMENT_READ'),
    ('SUBJECT_TEACHER', 'ASSESSMENT_MANAGE'), ('SUBJECT_TEACHER', 'QUESTION_BANK_READ'), ('SUBJECT_TEACHER', 'QUESTION_BANK_MANAGE'),
    ('SUBJECT_TEACHER', 'QUESTION_PAPER_MANAGE'), ('SUBJECT_TEACHER', 'ONLINE_EXAM_MANAGE'), ('SUBJECT_TEACHER', 'OMR_MANAGE'),
    ('SUBJECT_TEACHER', 'ASSESSMENT_ANALYTICS_READ'),
    -- families: own children (published results only)
    ('PARENT', 'COMPETENCY_READ'), ('PARENT', 'ASSESSMENT_READ'), ('PARENT', 'REEVALUATION_REQUEST'),
    ('STUDENT', 'COMPETENCY_READ'), ('STUDENT', 'ASSESSMENT_READ'), ('STUDENT', 'REEVALUATION_REQUEST'), ('STUDENT', 'ONLINE_EXAM_TAKE')
) AS g(role_code, permission_code)
JOIN access_role r ON r.role_code = g.role_code AND r.school_id IS NULL
JOIN access_permission p ON p.permission_code = g.permission_code
ON CONFLICT DO NOTHING;
