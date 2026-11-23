# Academic configuration

Subjects, class-teacher assignments, subject-teacher mapping, syllabus and lesson plans — for one academic year.
New endpoints are under `/api/v1/academic-config`. Subjects and subjects-per-class were already built (`../structure/ACADEMIC_STRUCTURE.md` §4); the older single-row APIs (`/api/v1/class-teacher-assignments`, `/api/v1/teacher-assignments`) still work and write the same rows.

```
Subject catalogue ─► curriculum (class × subject × stream) ─► class subjects of the year (periods per week)
                                                                   │
Section ── class teacher (one per section)                         ├─ subject teacher (primary; whole class or one section)
                                                                   └─ syllabus (class × subject × year)
                                                                        └─ unit ─ topic ── coverage per section
                                                                                     └─ lesson plan (teacher, date, section)
                                                                                          DRAFT → SUBMITTED → APPROVED → DELIVERED
```

## 1. Order of work

| Step | API | Checklist step |
|---|---|---|
| 1. Subjects | `POST /api/v1/subjects`, or the K–12 template | `SUBJECTS` |
| 2. Subjects per class | curriculum grid `PUT /api/v1/academic-structure/curricula/{id}/subjects`, then `POST /api/v1/academic-years/{id}/class-subjects/from-curriculum` | `CURRICULUM`, `CLASS_SUBJECTS` |
| 3. Periods per week | `PUT /api/v1/class-subject-assignments/{id}` `{ "periodsPerWeek": 6 }` | — |
| 4. Class teachers | `PUT /academic-config/class-teachers` | `CLASS_TEACHERS` |
| 5. Subject teachers | `PUT /academic-config/subject-teachers` | `SUBJECT_TEACHERS` |
| 6. Syllabus | `PUT /academic-config/syllabi`, or `POST /academic-config/syllabi/copy` from last year | `SYLLABUS` (recommended) |
| 7. Lesson plans | teachers, during the year | `LESSON_PLANS` (optional) |

`GET /api/v1/academic-years/{id}/setup-status` shows these steps. `GET /academic-config/overview` shows the detail and what is missing.

## 2. Overview

`GET /academic-config/overview?academicYearId=` (default: current year) — needs `CLASS_TEACHER_ASSIGNMENT_READ` + `TEACHER_SUBJECT_ASSIGNMENT_READ` + staff scope ALL.

Counts (`sections`, `sectionsWithClassTeacher`, `subjectSlots`, `subjectSlotsWithTeacher`, `syllabiExpected`, `syllabi`, `syllabiPublished`, `lessonPlans`, `lessonPlansToReview`), then `classes[]` → `sections[]` (class teacher) → `subjects[]` (teacher, periods per week, syllabus status, topics completed in that section), and `issues[]`:

| Level | Code | Meaning |
|---|---|---|
| ERROR | `TEACHER_NOT_ACTIVE` | A class or subject teacher has left or is suspended |
| WARNING | `NO_CLASS_SUBJECTS` | Section whose class has no subjects this year |
| WARNING | `NO_CLASS_TEACHER` | Section without a class teacher |
| WARNING | `NO_SUBJECT_TEACHER` | Section × subject without a teacher |
| WARNING | `TEACHER_OVERLOADED` | More periods a week than the teacher's limit |
| INFO | `CLASS_TEACHER_OF_MANY` | One teacher is class teacher of several sections |
| INFO | `CLASS_TEACHER_NOT_TEACHING` | Class teacher teaches no subject in their own section |
| INFO | `NO_PERIODS_PER_WEEK` | Class subjects without periods per week (workload can't be checked) |
| INFO | `NO_SYLLABUS`, `SYLLABUS_NOT_PUBLISHED`, `SYLLABUS_WITHOUT_TOPICS` | Syllabus missing, still a draft, or without topics |
| INFO | `LESSON_PLANS_TO_REVIEW` | Submitted plans waiting for approval |

A subject's teacher in a section is the teacher assigned to that section, otherwise the teacher assigned to the whole class (`teacherForWholeClass: true`). A section-level class subject (a stream subject) replaces the class-level one.

`GET /academic-config/workload?academicYearId=` (`STAFF_READ` + scope ALL): every active teacher with `classTeacherOf`, `subjectAssignments`, `sections`, `plannedPeriods` (from periods per week of what they teach), `timetablePeriods` (active timetable), `maxPeriodsPerWeek`, `overloaded`, and `teaches` ("Mathematics – Class V A").

## 3. Class teachers

`PUT /academic-config/class-teachers` — `CLASS_TEACHER_ASSIGNMENT_CREATE` + `CLASS_TEACHER_ASSIGNMENT_UPDATE` + staff scope ALL.

```jsonc
{ "academicYearId": "…", "effectiveDate": "2026-09-01", "dryRun": true,
  "assignments": [ { "sectionId": "<V-A>", "staffId": "<Asha>" },     // assign or change
                   { "sectionId": "<V-B>", "staffId": null } ] }       // remove
→ { "dryRun": true, "valid": true, "assigned": 1, "changed": 0, "removed": 1, "unchanged": 0,
    "rows": [ { "index": 0, "label": "Class V A", "action": "ASSIGNED", "error": null, "warnings": [] }, … ] }
```

- One class teacher per section. Changing one completes the old assignment the day before `effectiveDate` (history is kept) and starts the new one on it. Default date: today, or the first day of a year that has not started.
- Only active staff of type TEACHER. A teacher with two sections gets a warning, not an error.
- All or nothing: any invalid row → HTTP 422, `valid: false`, `rows[].error`, nothing saved. Sections not listed are not touched.
- `POST /api/v1/academic-years/{id}/class-teachers/bulk` (assign only, skips sections that have one) still exists.

## 4. Subject teachers

`PUT /academic-config/subject-teachers` — `TEACHER_SUBJECT_ASSIGNMENT_CREATE` + `TEACHER_SUBJECT_ASSIGNMENT_UPDATE` + staff scope ALL.

```jsonc
{ "academicYearId": "…", "dryRun": false,
  "assignments": [
    { "academicClassId": "<V>",  "sectionId": null,     "subjectId": "<Maths>",   "teacherId": "<Asha>" },   // every section of V
    { "academicClassId": "<V>",  "sectionId": "<V-B>",  "subjectId": "<Maths>",   "teacherId": "<Ravi>" },   // …except V-B
    { "academicClassId": "<XI>", "sectionId": "<XI-A>", "subjectId": "<Physics>", "teacherId": null } ] }    // remove
```

- A row sets the **primary** teacher of that class × section × subject. Co-teachers (non-primary) are added with `POST /api/v1/teacher-assignments` `{ "isPrimary": false }` and are left alone here; a co-teacher named in a row becomes the primary.
- The subject must be a class subject of that class / section this year, the teacher active and of type TEACHER.
- Warnings (the row is still saved): the subject or class level is not among the teacher's teaching subjects (`/staff-setup/staff/{id}/subjects`; only when the teacher has any listed); the teacher would exceed `maxPeriodsPerWeek`.
- `dryRun: true` returns exactly what a real call would, including workload warnings, and saves nothing.
- Pick a teacher with `GET /api/v1/staff-setup/teachers-for-subject?subjectId=&academicClassId=`.

## 5. Syllabus

One syllabus per class × subject × year: units (chapters) with topics.

| | Endpoint | Permission |
|---|---|---|
| List (coverage per section) | `GET /academic-config/syllabi?academicYearId=&academicClassId=&subjectId=&status=` | `SYLLABUS_READ` |
| One, with units and topics | `GET /academic-config/syllabi/{id}?sectionId=` | `SYLLABUS_READ` |
| Create / replace | `PUT /academic-config/syllabi` | `SYLLABUS_MANAGE` |
| Publish / archive / back to draft | `PATCH /academic-config/syllabi/{id}/status` `{ "status": "PUBLISHED" }` | `SYLLABUS_MANAGE` |
| Delete | `DELETE /academic-config/syllabi/{id}` | `SYLLABUS_MANAGE` + staff scope ALL |
| Copy from another year | `POST /academic-config/syllabi/copy` `{ fromAcademicYearId, toAcademicYearId, academicClassIds? }` | `SYLLABUS_MANAGE` + staff scope ALL |
| Mark coverage | `PUT /academic-config/syllabi/{id}/progress` | `SYLLABUS_PROGRESS_UPDATE` |

```jsonc
PUT /academic-config/syllabi
{ "academicYearId": "…", "academicClassId": "<V>", "subjectId": "<Maths>",
  "title": null,                       // default "Mathematics – Class V"
  "description": "NCERT Math-Magic 5", "referenceBooks": "…", "status": "PUBLISHED",
  "units": [
    { "id": null, "title": "Numbers up to 10 lakh", "learningOutcomes": "Reads and writes 6-digit numbers",
      "examTermId": "<Term 1>", "plannedPeriods": 14, "plannedStart": "2026-04-06", "plannedEnd": "2026-04-30",
      "topics": [ { "id": null, "title": "Place value", "plannedPeriods": 4 }, { "title": "Comparing numbers", "plannedPeriods": 3 } ] } ] }
```

- The request is the whole syllabus. Units and topics are stored in the order sent (`unitNo` 1, 2 …).
- **Editing:** send the `id` of every unit and topic you keep — their coverage stays. Anything without an id is new; anything not sent is removed with its coverage. A topic can be moved to another unit by sending its id there.
- `status` omitted = unchanged (DRAFT for a new one). A syllabus needs at least one unit to be published.
- `plannedPeriods` of the syllabus = each unit's value, or the sum of its topics when the unit has none.
- Dates must be inside the year; the term must belong to the year.
- **Who:** with staff scope ALL any syllabus; otherwise only for a class × subject you teach (if the school gives teachers `SYLLABUS_MANAGE`).
- **Copy** creates drafts with units and topics, without terms, dates and coverage; class × subject pairs that already have a syllabus in the target year are skipped.

### Coverage

```jsonc
PUT /academic-config/syllabi/{id}/progress
{ "sectionId": "<V-A>", "topics": [ { "topicId": "…", "status": "COMPLETED", "completedOn": "2026-04-14" },
                                    { "topicId": "…", "status": "IN_PROGRESS", "remarks": "Ex 1.3 pending" } ] }
```

- `status`: `NOT_STARTED` (clears it), `IN_PROGRESS`, `COMPLETED` (`completedOn` defaults to today; not in the future).
- Teachers can mark only the subject and section they teach; staff scope ALL can mark anything.
- Responses carry `coverage[]` per section (`topics`, `completed`, `inProgress`, `percent`) and `coveragePercent` (average of the sections). With `?sectionId=` each topic has that section's `status`, `completedOn`, `remarks`; `sectionsCompleted` is always present.
- Delivering a lesson plan (§6) marks its topic completed automatically.

## 6. Lesson plans

| | Endpoint | Permission |
|---|---|---|
| List | `GET /academic-config/lesson-plans?academicYearId=&teacherId=&academicClassId=&sectionId=&subjectId=&status=&from=&to=&limit=` | `LESSON_PLAN_READ` |
| One | `GET /academic-config/lesson-plans/{id}` | `LESSON_PLAN_READ` |
| Write | `POST /academic-config/lesson-plans` | `LESSON_PLAN_MANAGE` |
| Change | `PUT /academic-config/lesson-plans/{id}` | `LESSON_PLAN_MANAGE` |
| Send for review | `POST /academic-config/lesson-plans/{id}/submit` | `LESSON_PLAN_MANAGE` |
| Approve / return | `POST /academic-config/lesson-plans/{id}/review` `{ "decision": "RETURN", "remarks": "Add an activity" }` | `LESSON_PLAN_REVIEW` |
| Taught | `POST /academic-config/lesson-plans/{id}/deliver` `{ deliveredOn?, reflection?, markTopicCompleted? }` | `LESSON_PLAN_MANAGE` |
| Delete | `DELETE /academic-config/lesson-plans/{id}` | `LESSON_PLAN_MANAGE` |

```jsonc
POST /academic-config/lesson-plans
{ "academicYearId": "…", "academicClassId": "<V>", "sectionId": null, "subjectId": "<Maths>",
  "teacherId": null,                    // default: the signed-in teacher
  "syllabusTopicId": "<Place value>",   // optional; or only syllabusUnitId
  "title": "Place value with an abacus", "planDate": "2026-04-08", "periods": 2,
  "objectives": "…", "teachingMethod": "…", "resources": "…", "homework": "…", "assessment": "…",
  "submit": true }
```

```
DRAFT ──submit──► SUBMITTED ──review APPROVE──► APPROVED ──deliver──► DELIVERED
   ▲                  │
   └── (edit) ◄── RETURNED ◄── review RETURN (remarks required)
DRAFT / SUBMITTED ──deliver──► DELIVERED          (schools that don't review plans)
```

- **For what:** a class × subject the teacher teaches this year (`sectionId` null = all their sections of the class). `planDate` must be inside the year. The topic or unit must belong to the syllabus of the same class × subject × year.
- **Who writes:** the teacher, for themself. With staff scope ALL a plan can be written or changed for any teacher (`teacherId`).
- **Editing:** while DRAFT, SUBMITTED or RETURNED. A returned plan stays RETURNED until it is submitted again (`submit: true` or `/submit`). APPROVED and DELIVERED plans are fixed; a teacher cannot delete them.
- **Who reviews:** `LESSON_PLAN_REVIEW` plus either staff scope ALL (principal, coordinators) or an active duty for the plan's subject (Subject In-charge), class or stage (Academic Coordinator) — see `STAFF_SETUP.md` §4. Nobody without scope ALL reviews their own plan.
- **Delivered:** `deliveredOn` defaults to today. Unless `markTopicCompleted: false`, the plan's topic becomes COMPLETED in the plan's section, or — for a whole-class plan — in every section of the class that teacher takes the subject in.
- **Who sees what:** staff scope ALL sees every plan. Others see their own, plus submitted / approved / delivered plans they may review through a duty.

## 7. Suggested screens

| Screen | Read | Write |
|---|---|---|
| Academic configuration dashboard / to-do | `GET /overview` | links in `issues` |
| Class teachers (section list) | overview `classes[].sections[]`, `/staff-setup/teachers-for-subject` or staff list | `PUT /class-teachers` (`dryRun` first) |
| Subject teachers (class × section × subject grid) | overview, `GET /workload` | `PUT /subject-teachers` (`dryRun` first) |
| Teacher workload | `GET /workload` | — |
| Syllabus list | `GET /syllabi` | `POST /syllabi/copy`, `PATCH /syllabi/{id}/status` |
| Syllabus editor (units, topics) | `GET /syllabi/{id}` | `PUT /syllabi` |
| Teacher: my syllabus coverage | `GET /syllabi/{id}?sectionId=` | `PUT /syllabi/{id}/progress` |
| Teacher: my lesson plans | `GET /lesson-plans?teacherId=` | `POST`, `PUT`, `/submit`, `/deliver` |
| Coordinator: plans to review | `GET /lesson-plans?status=SUBMITTED` | `POST /lesson-plans/{id}/review` |

## 8. TypeScript types

```ts
type SyllabusStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
type TopicProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
type LessonPlanStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'RETURNED' | 'DELIVERED';

interface ClassTeachersRequest { academicYearId: string; assignments: { sectionId: string; staffId: string | null }[]; effectiveDate?: string; dryRun?: boolean; }
interface SubjectTeachersRequest { academicYearId: string;
  assignments: { academicClassId: string; sectionId?: string | null; subjectId: string; teacherId: string | null }[]; effectiveDate?: string; dryRun?: boolean; }
interface AllocationResult { dryRun: boolean; valid: boolean; assigned: number; changed: number; removed: number; unchanged: number;
  rows: { index: number; label: string; action: 'ASSIGNED' | 'CHANGED' | 'REMOVED' | 'UNCHANGED' | null; error: string | null; warnings: string[] }[]; }

interface TeacherRef { staffId: string; staffCode: string; name: string; }
interface SubjectSlot { classSubjectId: string; subjectId: string; subjectCode: string; subjectName: string; compulsory: boolean;
  periodsPerWeek: number | null; sectionLevel: boolean; teacherAssignmentId: string | null; teacher: TeacherRef | null; teacherForWholeClass: boolean;
  syllabusId: string | null; syllabusStatus: SyllabusStatus | null; syllabusTopics: number; topicsCompleted: number; }
interface ConfigOverview { academicYearId: string; academicYear: string; sections: number; sectionsWithClassTeacher: number; subjectSlots: number;
  subjectSlotsWithTeacher: number; syllabiExpected: number; syllabi: number; syllabiPublished: number; lessonPlans: number; lessonPlansToReview: number;
  classes: { classId: string; code: string; name: string; gradeLevel: number | null;
             sections: { sectionId: string; code: string; name: string; classTeacherAssignmentId: string | null; classTeacher: TeacherRef | null; subjects: SubjectSlot[] }[] }[];
  issues: { level: 'ERROR' | 'WARNING' | 'INFO'; code: string; count: number; message: string; examples: string[] }[]; }
interface TeacherLoad { staffId: string; staffCode: string; name: string; designation: string | null; classTeacherOf: number; subjectAssignments: number;
  sections: number; plannedPeriods: number; timetablePeriods: number; maxPeriodsPerWeek: number | null; overloaded: boolean; teaches: string[]; }

interface TopicInput { id?: string | null; title: string; plannedPeriods?: number | null; }
interface UnitInput { id?: string | null; title: string; description?: string; learningOutcomes?: string; examTermId?: string | null;
  plannedPeriods?: number | null; plannedStart?: string | null; plannedEnd?: string | null; topics?: TopicInput[]; }
interface SyllabusRequest { academicYearId: string; academicClassId: string; subjectId: string; title?: string; description?: string;
  referenceBooks?: string; status?: SyllabusStatus; units: UnitInput[]; }
interface Topic { id: string; title: string; plannedPeriods: number | null; status: TopicProgressStatus | null; completedOn: string | null;
  remarks: string | null; sectionsCompleted: number; }
interface Unit { id: string; unitNo: number; title: string; description: string | null; learningOutcomes: string | null; examTermId: string | null;
  examTerm: string | null; plannedPeriods: number | null; plannedStart: string | null; plannedEnd: string | null; topics: Topic[]; }
interface SectionCoverage { sectionId: string; section: string; topics: number; completed: number; inProgress: number; percent: number; }
interface Syllabus { id: string; code: string; academicYearId: string; academicClassId: string; className: string; subjectId: string; subjectName: string;
  title: string; description: string | null; referenceBooks: string | null; status: SyllabusStatus; unitCount: number; topicCount: number;
  plannedPeriods: number; coveragePercent: number; coverage: SectionCoverage[]; units: Unit[] | null; }   // units null in the list
interface ProgressRequest { sectionId: string; topics: { topicId: string; status: TopicProgressStatus; completedOn?: string; remarks?: string }[]; }
interface CopyResult { copied: number; skipped: number; notes: string[]; }

interface LessonPlanRequest { academicYearId: string; academicClassId: string; sectionId?: string | null; subjectId: string; teacherId?: string | null;
  syllabusUnitId?: string | null; syllabusTopicId?: string | null; title: string; planDate: string; periods?: number; objectives?: string;
  teachingMethod?: string; resources?: string; homework?: string; assessment?: string; submit?: boolean; }
interface LessonPlan { id: string; code: string; academicYearId: string; academicClassId: string; className: string; sectionId: string | null;
  section: string | null; subjectId: string; subjectName: string; teacherId: string; teacherName: string; syllabusUnitId: string | null;
  syllabusUnit: string | null; syllabusTopicId: string | null; syllabusTopic: string | null; title: string; planDate: string; periods: number;
  objectives: string | null; teachingMethod: string | null; resources: string | null; homework: string | null; assessment: string | null;
  status: LessonPlanStatus; submittedAt: string | null; reviewedAt: string | null; reviewRemarks: string | null; deliveredOn: string | null;
  reflection: string | null; updatedAt: string; }
```

## 9. Database

- **Tenant V24** `V24__academic_configuration.sql`: `syllabus` (unique per year × class × subject), `syllabus_unit`, `syllabus_topic`, `syllabus_topic_progress` (unique per topic × section; no row = not started), `lesson_plan`. Removing a unit or topic removes its coverage and unlinks lesson plans.
- **Master V35** `V35__syllabus_and_lesson_plan_permissions.sql`: the six permissions below.
- Existing tables used as they are: `subject`, `curriculum_subject` (V7, V20), `class_subject_assignment`, `teacher_subject_assignment` (V8), `class_teacher_assignment` (V11), `staff_subject` (V21), `staff_duty` (V22).

## 10. Permissions

| Permission | Standard roles |
|---|---|
| `SYLLABUS_READ`, `SYLLABUS_PROGRESS_UPDATE` | Owner, School Admin, Principal, Coordinator, Teacher, Subject Teacher |
| `SYLLABUS_MANAGE` | Owner, School Admin, Principal, Coordinator |
| `LESSON_PLAN_READ` | Owner, School Admin, Principal, Coordinator, Teacher, Subject Teacher |
| `LESSON_PLAN_MANAGE` | Owner, School Admin, Teacher, Subject Teacher |
| `LESSON_PLAN_REVIEW` | Owner, School Admin, Principal, Coordinator |

Class-teacher and subject-teacher mapping uses the existing `CLASS_TEACHER_ASSIGNMENT_*` and `TEACHER_SUBJECT_ASSIGNMENT_*` permissions with staff scope ALL. Students and parents have no syllabus access yet.
