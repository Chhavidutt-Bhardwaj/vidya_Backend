# Staff & teacher setup

Departments, designations, duties, teachers, non-teaching staff, their logins and roles, and what happens when someone leaves.
Staff endpoints are under `/api/v1/staff-setup`; logins are under `/api/v1/tenant/users/{id}/account` (the same API for every person of the school). The older `/api/v1/staff` CRUD still works.

```
Department  (Science, Transport …)                 ── head = the HOD duty
Designation (PGT, Accountant, Driver …)            ── staff type + default login role + default department
Duty type   (HOD, Exam Coordinator, Stage Coordinator, Timetable In-charge …)
            ── scope kind (school / department / stage / class / section / subject) + optional access role

Person (tenant_user + profile)  ──  one login (master account)  ──  account type + extra roles
   └─ Staff record: department, designation, reports-to, employment type, qualification, experience, max periods/week
        ├─ teaching subjects (subject × class range)     → subject-teacher suggestions
        ├─ duties (any number at once, each with scope and period) → access roles follow the duty
        ├─ class-teacher assignments   (existing API)
        └─ subject-teacher assignments (existing API)
```

**One login model.** Sign-in is `POST /api/v1/auth/login` for everyone. Creating, reading, enabling/disabling a login and setting roles is `/api/v1/tenant/users/{id}/account`, whatever the person is (staff, teacher, student, parent). Staff onboarding can create the login in the same call; underneath it is the same service.

**One person, many hats.** A teacher's account type is `TEACHER`. Being HOD of Science, coordinator of the middle stage and exam coordinator are *duties*; each duty can carry an access role (COORDINATOR, Exam Coordinator …) that is put on the login while the duty lasts and removed when it ends. The person keeps one login and one sign-in.

## 1. First-time setup: standard departments and designations

`POST /staff-setup/templates/indian-school` (`STAFF_CREATE`), body optional: `{ "departments": true, "designations": true, "dryRun": true }`

Creates any of these that don't exist yet (matching by code or name, so it's safe to run again).
- **18 departments:** Pre-Primary, Languages, Mathematics, Science, Social Science, Commerce, Computer Science, Physical Education & Sports, Arts, Counselling & Special Education, Library, Administration, Accounts & Fees, IT, Transport, Housekeeping & Maintenance, Security, Health.
- **29 designations**, each with a login role:

  | Designations | Staff type | Login role |
  |---|---|---|
  | Principal, Vice Principal, Headmistress | ADMINISTRATIVE | `PRINCIPAL` |
  | Administrative Officer | ADMINISTRATIVE | `SCHOOL_ADMIN` |
  | Academic Coordinator, Head of Department | TEACHER | `COORDINATOR` |
  | PGT, TGT, PRT, NTT, PE / Art / Special Educator | TEACHER | `TEACHER` |
  | Accountant | ADMINISTRATIVE | `ACCOUNTS` |
  | Counsellor, Librarian, Lab Assistant, Office Superintendent, Clerk, Receptionist, IT Admin, Transport In-charge | various | `SCHOOL_STAFF` |
  | Nurse, Driver, Bus Attendant, Ayah, Peon, Housekeeping, Security Guard | SUPPORT | no login by default |

**Duty types** come with every school (migration V22), so no template step is needed for them:

| Duty | For | One holder per scope | Role put on the login |
|---|---|---|---|
| Head of Department (`HOD`) | a department | yes | `COORDINATOR` |
| Academic Coordinator (`STAGE_COORDINATOR`) | a stage (pre-primary … senior secondary) | yes | `COORDINATOR` |
| Subject In-charge | a subject | yes | — |
| Laboratory In-charge | a subject | no | — |
| Examination Coordinator | school | no | `EXAM_COORDINATOR` * |
| Timetable In-charge | school | no | `TIMETABLE_INCHARGE` * |
| Attendance In-charge | school | no | `ATTENDANCE_INCHARGE` * |
| Admissions In-charge | school | no | `ADMISSION_INCHARGE` * |
| Library In-charge | school | no | `LIBRARY_INCHARGE` * |
| Discipline, House Master, Cultural & Activities, Sports, Transport In-charge | school | no | — |

\* created in the school with a standard permission set the first time the duty is given (e.g. Exam Coordinator = exams, marks, report cards, read all students). The school can edit those roles afterwards under `/schools/{id}/access/roles`, add its own duty types, and point a duty type at any of its roles.

| | Endpoint | Permission |
|---|---|---|
| List (with counts) | `GET /departments`, `GET /designations`, `GET /duty-types` | `STAFF_READ` |
| Create | `POST /departments` `{ code, name, category, headStaffId, description, sortOrder }` | `STAFF_CREATE` |
| | `POST /designations` `{ code, name, staffType, defaultRole, defaultDepartmentId, rank, description }` | |
| | `POST /duty-types` `{ code, name, category, scopeKind, singleHolder, accessRoleCode, description }` | |
| Update | `PUT /departments/{id}`, `PUT /designations/{id}`, `PUT /duty-types/{id}` | `STAFF_UPDATE` |
| Activate / deactivate | `PATCH …/{id}/status` `{ "status": "INACTIVE" }` | `STAFF_UPDATE` |
| Delete (only when unused) | `DELETE …/{id}` | `STAFF_DELETE` |

- `category` of a department is `ACADEMIC`, `ADMINISTRATIVE` or `SUPPORT`; of a duty type `ACADEMIC`, `EXAMINATION`, `ADMINISTRATION`, `STUDENT_WELFARE` or `OPERATIONS`.
- `defaultRole` of a designation is one of `SCHOOL_ADMIN`, `PRINCIPAL`, `COORDINATOR`, `TEACHER`, `SUBJECT_TEACHER`, `ACCOUNTS`, `SCHOOL_STAFF`, or null for no login.
- `scopeKind` of a duty type is `SCHOOL`, `DEPARTMENT`, `STAGE`, `CLASS`, `SECTION` or `SUBJECT`. Standard duty types keep their code and scope; everything else is editable.
- A department's `headStaffId` **is** the HOD duty: setting it gives (or hands over) the HOD duty; clearing it ends the duty.
- Renaming a department or designation also updates the old text fields on staff.

## 2. Adding staff

`POST /staff-setup/onboard` (`STAFF_CREATE` + staff scope ALL) creates the person, staff record, subjects, duties and login in one step.

```jsonc
{ "firstName": "Asha", "lastName": "Rao", "email": "asha.rao@school.in", "phone": "9876543210",
  "gender": "FEMALE", "dateOfBirth": "1990-04-12",
  "employeeCode": "EMP-104", "joiningDate": "2027-04-01",
  "designationId": "<PGT>",            // staffType and department default from it
  "departmentId": null, "staffType": null,
  "reportingToStaffId": "<HOD>", "employmentType": "PERMANENT",
  "qualification": "M.Sc. Physics, B.Ed.", "experienceYears": 6, "maxPeriodsPerWeek": 30,
  "subjects": [ { "subjectId": "<Physics>", "fromGradeLevel": 9, "toGradeLevel": 12, "primary": true } ],
  "duties":   [ { "dutyTypeCode": "HOD", "departmentId": "<Science>", "replace": true },
                { "dutyTypeCode": "EXAM_COORDINATOR" } ],
  "login": { "create": null, "role": null, "extraRoleIds": [] } }
→ { "staff": { "staffCode": "STF-…", "hasLogin": true, … },
    "login": { "userId": "…", "userCode": "USR-…", "email": "asha.rao@school.in", "role": "TEACHER",
               "created": true, "temporaryPassword": "Vk3m…7@", "extraRoles": [] },
    "warnings": [] }
```

- **Contact:** an email or a phone is required. Drivers and support staff can be added with only a phone.
- **Login defaults:** a login is created when an email is given and the designation has a login role. Without a designation, teachers get one and other staff don't. Override with `login.create` and `login.role`.
- **Duties:** validated with the rest of the row (HOD needs a department, a coordinator needs a stage …). Their access roles are put on the new login (needs `SCHOOL_ROLE_MANAGE`); without a login they wait until one is created.
- **Temporary password:** shown only in this response. No email is sent yet, so share it securely.
- **Email already has a login:** an unlinked login of this school of the right kind is reused (role unchanged, with a warning); a login of another school, the owner's, or one already linked is refused.
- **Permissions:** a login needs `USER_CREATE`; extra roles or duty roles need `SCHOOL_ROLE_MANAGE`; you can never give a role with permissions you don't hold yourself.
- **Rollback:** if anything fails after the login was created, the login is deleted again.

**Bulk:** `POST /staff-setup/onboard/bulk`, `{ "staff": [ …up to 500 rows… ], "dryRun": true }`. Every row is validated first (including duplicate emails or employee codes inside the file); if any row is invalid the response is **422** with `rows[].error` and nothing is saved.

## 3. Logins and roles — same API for everyone

`/api/v1/tenant/users/{tenantUserId}/account` — `tenantUserId` is the person id (`staff.tenantUserId`, a student's or guardian's user id).

| | Endpoint | Permission |
|---|---|---|
| Give a login | `POST …/account` `{ email?, role?, extraRoleIds? }` | `USER_CREATE` + USER scope ALL |
| See login and roles | `GET …/account` | `USER_READ` |
| Change roles | `PUT …/account/roles` `{ "primaryRole": "COORDINATOR", "extraRoleIds": [...] }` | `SCHOOL_ROLE_MANAGE` |
| Enable / disable | `PATCH …/account/status` `{ "active": false }` | `USER_UPDATE` |

- Account type defaults by person: designation role for staff, `TEACHER` for teachers, `SCHOOL_ADMIN` for admins, `STUDENT`, `PARENT`. A staff login can only be a staff type, a student login only `STUDENT`, and so on.
- `primaryRole` is the account type; `extraRoleIds` is the exact list of additional roles (global or the school's own, from `/schools/{id}/access/roles`). Omit either to leave it unchanged. Roles that came with a duty are managed by the duty; end the duty to take them off.
- Changing someone's role needs the rights of both the old and the new role.
- Changes reach the person on their next token refresh. Disabling signs the person out everywhere. The owner's account can't be changed here.
- Only active people (and active staff) get a login or can be re-enabled.

## 4. Duties — who does what

| | Endpoint | Permission |
|---|---|---|
| Who does what (every duty type with holders) | `GET /staff-setup/duties` | `STAFF_READ` |
| Everything one person is responsible for | `GET /staff-setup/staff/{staffId}/duties?includeEnded=false` | `STAFF_READ` (own or scope ALL) |
| Give a duty | `POST /staff-setup/staff/{staffId}/duties` | `STAFF_UPDATE` + scope ALL (+ `SCHOOL_ROLE_MANAGE` if the duty carries a role) |
| End a duty | `POST /staff-setup/duties/{dutyId}/end` `{ "endDate": "2027-03-31" }` | `STAFF_UPDATE` + scope ALL |

```jsonc
POST /staff-setup/staff/{id}/duties
{ "dutyTypeCode": "STAGE_COORDINATOR", "classStage": "MIDDLE", "academicYearId": "<2027-28>", "startDate": "2027-04-01",
  "notes": "Classes VI–VIII", "replace": false }
→ { "id": "…", "dutyName": "Academic Coordinator", "scopeKind": "STAGE", "scopeLabel": "Middle (VI–VIII)",
    "startDate": "2027-04-01", "endDate": null, "status": "ACTIVE", "accessRoleCode": "COORDINATOR", "roleGranted": true }
```

- Send the scope the duty type needs: `departmentId`, `classStage`, `academicClassId`, `sectionId` or `subjectId`. School-wide duties take none.
- **Single-holder duties** (HOD, stage coordinator, subject in-charge): if someone else holds it, the request fails and names them; `replace: true` ends their duty the day before and hands over.
- **Roles follow duties.** When a duty carries a role, it is put on the holder's login (idempotent; skipped if it is already their account type). When the duty ends, the role comes off unless another active duty still needs it. Staff without a login get the role as soon as a login is created.
- `GET /staff/{id}/duties` also lists the person's class-teacher (`classTeacherOf`) and subject-teacher (`teaches`) assignments, so one screen shows every hat they wear.

## 5. When someone leaves

`POST /staff-setup/staff/{staffId}/offboard` (`STAFF_STATUS_UPDATE` + scope ALL)

```jsonc
{ "status": "RESIGNED",            // RESIGNED | TERMINATED | RETIRED | SUSPENDED | INACTIVE
  "lastWorkingDate": "2027-10-31", "revokeLogin": true, "endAssignments": false, "reason": "Relocating" }
→ { "loginDisabled": true, "sessionsEnded": 2,
    "classTeacherAssignments": 1, "subjectAssignments": 4, "activeTimetableSlots": 26, "assignmentsEnded": false,
    "departmentsHeaded": ["Science"], "dutiesEnded": ["Head of Department (Science)", "Examination Coordinator"], "reportees": 3,
    "nextSteps": ["1 section(s) need a new class teacher: …", "Choose a new head for: Science", …] }
```

- **Duties** always end on the last working date; their roles come off the login.
- **Login** is disabled and signed out everywhere by default (needs `USER_UPDATE`); send `revokeLogin: false` to keep it.
- **Assignments** are reported by default; with `endAssignments: true` class-teacher and subject-teacher assignments are closed on the last working date. Timetable slots are never changed automatically.
- **Permanent exits** (resigned, terminated, retired) also deactivate the person record.
- Suspended or inactive staff come back with `PATCH /api/v1/staff/{id}/status` `{ "status": "ACTIVE" }`, then `PATCH /api/v1/tenant/users/{id}/account/status` `{ "active": true }`; give their duties back explicitly.

## 6. Teaching subjects and finding a teacher

- `PUT /staff-setup/staff/{staffId}/subjects` `{ "subjects": [ { subjectId, fromGradeLevel, toGradeLevel, primary } ] }` replaces the list; `GET` returns it.
- `GET /staff-setup/teachers-for-subject?subjectId=&academicClassId=` lists active teachers whose subjects cover that class's grade level: overloaded teachers last, then primary-subject teachers, then fewest periods per week.

## 7. Overview

`GET /staff-setup/overview` (`STAFF_READ` + scope ALL): active staff by type, status, department and designation; staff with a login; logins not linked to a person; and `issues`:

| Code | Level | Meaning |
|---|---|---|
| `LOGIN_ACTIVE_AFTER_EXIT` | ERROR | Someone who left or was suspended can still sign in |
| `NO_DESIGNATION` / `NO_DEPARTMENT` | WARNING | Active staff missing a designation or department |
| `TEACHER_WITHOUT_SUBJECTS` | WARNING | Teacher has no subjects, so suggestions skip them |
| `TEACHER_WITHOUT_LOGIN` | WARNING | Teacher can't take attendance or enter marks |
| `LOGIN_WITHOUT_STAFF` | WARNING | A staff login isn't linked to a person; link it by onboarding the person with the same email |
| `HEAD_NOT_ACTIVE` | WARNING | A department's head has left |
| `DEPARTMENT_WITHOUT_HEAD` | INFO | Academic department with staff but no HOD |
| `TEACHER_OVERLOADED` | WARNING | More periods in the active timetable than the weekly limit |

The academic setup checklist (`GET /api/v1/academic-years/{id}/setup-status`) now has a `STAFF` step: teachers exist and all have logins.

## 8. Suggested screens

| Screen | Read | Write |
|---|---|---|
| Staff dashboard / to-do | `GET /overview` | links below |
| First-run wizard | template with `dryRun` | template |
| Departments, designations, duty types | `GET /departments`, `/designations`, `/duty-types` | CRUD above |
| Who does what | `GET /duties` | `POST /staff/{id}/duties`, `POST /duties/{id}/end` |
| Add staff (form) | departments, designations, duty types, subjects, `/schools/{id}/access/roles` | `POST /onboard`; show the temporary password once |
| Import staff (sheet) | — | `POST /onboard/bulk` with `dryRun`, then for real |
| Staff profile: Account tab | `GET /tenant/users/{tenantUserId}/account` | `POST`, `PUT …/roles`, `PATCH …/status` |
| Staff profile: Duties tab | `GET /staff/{id}/duties` | assign / end |
| Staff profile: Subjects tab | `GET /staff/{id}/subjects` | `PUT /staff/{id}/subjects` |
| Exit form | — | `POST /staff/{id}/offboard`; show `nextSteps` |
| Assign subject teacher | `GET /teachers-for-subject` | `POST /api/v1/teacher-assignments` |

## 9. TypeScript types

```ts
type StaffType = 'TEACHER' | 'NON_TEACHING' | 'ADMINISTRATIVE' | 'SUPPORT' | 'OTHER';
type StaffRole = 'SCHOOL_ADMIN' | 'PRINCIPAL' | 'COORDINATOR' | 'TEACHER' | 'SUBJECT_TEACHER' | 'ACCOUNTS' | 'SCHOOL_STAFF';
type AccountRole = StaffRole | 'STUDENT' | 'PARENT';
type DutyScope = 'SCHOOL' | 'DEPARTMENT' | 'STAGE' | 'CLASS' | 'SECTION' | 'SUBJECT';
type ClassStage = 'PRE_PRIMARY' | 'PRIMARY' | 'MIDDLE' | 'SECONDARY' | 'SENIOR_SECONDARY';

interface Department { id: string; code: string; name: string; description: string | null;
  category: 'ACADEMIC' | 'ADMINISTRATIVE' | 'SUPPORT'; headStaffId: string | null; headName: string | null;
  sortOrder: number; status: 'ACTIVE' | 'INACTIVE'; staff: number; }
interface Designation { id: string; code: string; name: string; description: string | null; staffType: StaffType;
  defaultRole: StaffRole | null; defaultDepartmentId: string | null; rank: number; status: 'ACTIVE' | 'INACTIVE'; staff: number; }
interface DutyType { id: string; code: string; name: string; description: string | null;
  category: 'ACADEMIC' | 'EXAMINATION' | 'ADMINISTRATION' | 'STUDENT_WELFARE' | 'OPERATIONS'; scopeKind: DutyScope;
  singleHolder: boolean; accessRoleCode: string | null; system: boolean; status: 'ACTIVE' | 'INACTIVE'; activeHolders: number; }
interface DutyRequest { dutyTypeId?: string; dutyTypeCode?: string; departmentId?: string; classStage?: ClassStage;
  academicClassId?: string; sectionId?: string; subjectId?: string; academicYearId?: string; startDate?: string; notes?: string; replace?: boolean; }
interface Duty { id: string; staffId: string; staffName: string; dutyTypeId: string; dutyCode: string; dutyName: string;
  category: DutyType['category']; scopeKind: DutyScope; scopeLabel: string; departmentId: string | null; classStage: ClassStage | null;
  academicClassId: string | null; sectionId: string | null; subjectId: string | null; academicYearId: string | null;
  startDate: string; endDate: string | null; status: 'ACTIVE' | 'ENDED'; notes: string | null; accessRoleCode: string | null; roleGranted: boolean; }
interface TeachingAssignment { assignmentId: string; kind: 'CLASS_TEACHER' | 'SUBJECT_TEACHER'; label: string; academicYearId: string;
  academicClassId: string; sectionId: string | null; subjectId: string | null; startDate: string; }
interface StaffDuties { staffId: string; staffCode: string; name: string; designation: string | null; duties: Duty[];
  classTeacherOf: TeachingAssignment[]; teaches: TeachingAssignment[]; }
interface DutyGroup { dutyType: DutyType; holders: Duty[]; }

interface SubjectSkill { subjectId: string; fromGradeLevel?: number | null; toGradeLevel?: number | null; primary?: boolean; }
interface LoginInfo { userId: string; userCode: string; email: string; role: AccountRole; status: string;
  created: boolean; temporaryPassword: string | null; extraRoles: string[]; }
interface Account { tenantUserId: string; userCode: string; name: string; userType: 'ADMIN' | 'STAFF' | 'TEACHER' | 'STUDENT' | 'PARENT';
  staffId: string | null; hasLogin: boolean; login: LoginInfo | null; roles: UserRole[]; }
interface OnboardResult { staff: StaffDetail; login: LoginInfo | null; warnings: string[]; }
interface BulkOnboardResult { dryRun: boolean; valid: boolean; created: number; loginsCreated: number;
  rows: { index: number; name: string; email: string | null; error: string | null; staffCode: string | null; login: LoginInfo | null }[]; }
interface OffboardResult { status: string; lastWorkingDate: string; loginDisabled: boolean; sessionsEnded: number;
  classTeacherAssignments: number; subjectAssignments: number; activeTimetableSlots: number; assignmentsEnded: boolean;
  departmentsHeaded: string[]; dutiesEnded: string[]; reportees: number; nextSteps: string[]; }
interface TeacherSuggestion { staffId: string; staffCode: string; name: string; designation: string | null; primarySubject: boolean;
  subjectAssignments: number; periodsPerWeek: number; maxPeriodsPerWeek: number | null; overloaded: boolean; }
interface StaffOverview { activeStaff: number; byType: Record<StaffType, number>; byStatus: Record<string, number>;
  departments: { id: string; name: string; count: number }[]; designations: { id: string; name: string; count: number }[];
  withLogin: number; loginsNotLinked: number;
  issues: { level: 'ERROR' | 'WARNING' | 'INFO'; code: string; count: number; message: string; examples: string[] }[]; }
```

`StaffResponse` / `StaffDetailResponse` also include `departmentId`, `designationId`, `reportingToStaffId`, `employmentType` and `hasLogin`.

## 10. Database

- **Tenant V21:** `department`, `designation`, `staff_subject`; new `staff` columns (`department_id`, `designation_id`, `reporting_to_staff_id`, `employment_type`, `qualification`, `experience_years`, `max_periods_per_week`, `last_working_date`); `tenant_user.email` nullable with an email-or-phone check; free-text designations and departments converted to records.
- **Tenant V22:** `duty_type` (14 standard rows seeded) and `staff_duty`; existing department heads become HOD duties.
- No master migration and no new permissions. Duty roles such as `EXAM_COORDINATOR` are ordinary school-scoped `access_role` rows created on first use.
