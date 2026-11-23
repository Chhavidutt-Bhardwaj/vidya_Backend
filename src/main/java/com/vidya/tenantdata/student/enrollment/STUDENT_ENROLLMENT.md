# Student & parent enrollment

New admissions, promotion of existing students, student profiles and parent mapping — for every school.
Endpoints are under `/api/v1/student-setup`; promotion is `/api/v1/academic-years/{id}/promotion`; logins are `/api/v1/tenant/users/{id}/account` (the same API as staff). The older `/api/v1/students`, `/api/v1/admissions`, `/api/v1/enrollments` and `/students/{id}/guardians` APIs still work underneath.

```
Student record (admission number, status)  ── person (tenant_user STUDENT + profile)  ── optional login
   ├─ school details (blood group, house, previous school, TC in/out, leaving …)
   ├─ enrollment per academic year (class, section, roll number, status)
   │     └─ subjects
   └─ guardians (father, mother, …)  ──►  parent record (tenant_user PARENT)  ── one login
                                            └─ shared by all children of that parent (siblings)
```

**One parent, many children.** A guardian row belongs to one student. The *parent record* is the person: when a second child is admitted with the same email or phone, the guardian is linked to the existing parent record, and the parent's one login sees both children (`student_guardian.guardian_user_id` is what the PARENT data scope follows).

**One login model.** Parents and students sign in at `POST /api/v1/auth/login` like everyone else. Their logins are created here during admission (`login: true` on a guardian, `studentLogin: true`), or later through `/api/v1/tenant/users/{id}/account`.

## 1. Before the first admission

1. Academic year, classes and sections exist (see `ACADEMIC_SETUP.md`); `GET /api/v1/academic-years/{id}/setup-status` lists the steps.
2. Admission numbers. Optional — without configuration the year gets `<start year>/0001`, `/0002` …

```http
GET /api/v1/student-setup/admission-numbers
PUT /api/v1/student-setup/admission-numbers
{ "academicYearId": "…", "prefix": "GPS/26/", "nextNumber": 1, "padding": 4 }      → GPS/26/0001
{ "prefix": "GPS-", "nextNumber": 1201, "padding": 5 }                             → school-wide GPS-01201
```
A per-year sequence wins over the school-wide one. Numbers given by hand are always accepted when free.

## 2. New admission

`POST /api/v1/student-setup/admit` — needs `STUDENT_CREATE`; `USER_CREATE` as well when any login is requested.

```json
{
  "firstName": "Aarav", "lastName": "Sharma", "gender": "MALE", "dateOfBirth": "2016-08-15",
  "phone": null, "email": null,
  "addressLine1": "12 MG Road", "city": "Pune", "state": "MH", "postalCode": "411001",
  "admissionNumber": null,
  "admissionDate": "2026-04-01",
  "academicYearId": "…", "academicClassId": "…", "sectionId": "…", "rollNumber": "12",
  "autoAssignCompulsorySubjects": true, "subjectIds": [],
  "details": { "bloodGroup": "B+", "motherTongue": "Marathi", "house": "Red", "previousSchool": "Little Flowers", "previousClass": "IV", "tcNumberIn": "LF/2026/77" },
  "guardians": [
    { "name": "Ramesh Sharma", "relationship": "FATHER", "phone": "9876543210", "email": "ramesh@gmail.com", "occupation": "Engineer",
      "primary": true, "emergencyContact": true, "login": true },
    { "name": "Sunita Sharma", "relationship": "MOTHER", "phone": "9876500000" }
  ],
  "studentLogin": false
}
```

What happens, in one transaction:

| Step | Rule |
|---|---|
| Admission number | blank → next free number from the year's sequence; given → must be unused |
| Person | `tenant_user` STUDENT + profile; no real email → internal placeholder address (`student.<code>@school.vidya.internal`) |
| Placement | enrollment ACTIVE in class (section optional; roll number must be free; section capacity respected) |
| Subjects | compulsory subjects of the class auto-assigned unless `autoAssignCompulsorySubjects: false`; `subjectIds` adds electives |
| Guardians | each becomes a `student_guardian` row; first one is primary unless another says `primary: true` |
| Parent record | `parentUserId` → that parent; else same email / phone as an existing parent → that parent (warning says so); else a new PARENT record when email or phone is given. `linkExisting: false` / `createParent: false` switch these off |
| Parent login | `login: true` → login for the parent record (needs an email). Already has one → reused, nothing created |
| Student login | `studentLogin: true` → needs the student's own email |
| Checks | same name + date of birth as an existing student → warning (`use re-admission if this is the same child`); birth date must be > 2 years ago |

Response `AdmitResult`: `student`, `enrollment`, `guardians[]` (each with `parentUserId`, `parentHasLogin`, `parentLogin` with the temporary password **once**, `otherChildren`), `studentLogin`, `siblings`, `warnings`.

### Import a sheet

```http
POST /api/v1/student-setup/admit/bulk        { "students": [ …up to 500 AdmitStudentRequest… ], "dryRun": true }
```
Every row is validated first; the response lists each row with its `error` and `warnings` and the admission number it would get (`2026/0007 (next)`). `valid: false` → HTTP 422 and nothing saved. Send again with `dryRun: false` to admit all rows in one transaction (all or nothing). Duplicate admission numbers or emails inside the batch are errors too.

## 3. Existing students: promotion

Already built — `ACADEMIC_SETUP.md` §Promotion:

```http
POST /api/v1/academic-years/{oldYearId}/promotion/preview   { "targetAcademicYearId": "…", "keepRollNumbers": true, "overrides": [ { "studentId": "…", "action": "DETAIN" } ] }
POST /api/v1/academic-years/{oldYearId}/promotion
```
Actions per student: `PROMOTE` (default, next class / same section code), `DETAIN`, `GRADUATE` (last class), `LEAVE`, `SKIP`. The enrollment overview shows `pendingPromotion` and a `PENDING_PROMOTION` issue until it is done.

Mid-year class / section change: `PUT /api/v1/enrollments/{enrollmentId}/transfer` (existing API).

## 4. Student profile

```http
GET /api/v1/student-setup/students/{studentId}
```
One screen: `student`, `details` (school details incl. leaving info), `currentEnrollment` (class, section, roll, subjects), `enrollmentHistory`, `guardians[]` with parent record and login state, `siblings`, `attendance` (this year's present / absent / late / %), `tenantUserId`, `hasLogin`.
Parents and students with `STUDENT_SCOPE_OWN` can read their own children / themselves.

```http
PUT /api/v1/student-setup/students/{studentId}
{ "phone": "…", "addressLine1": "…", "details": { "bloodGroup": "O+", "house": "Blue", "photoUrl": "https://…" } }
```
Omitted fields stay as they are. Email of a student who already has a login is changed through the account API.

## 5. Parents

```http
GET  /api/v1/student-setup/parents?search=sharma&withLogin=false      → every parent record with its children and login state
GET  /api/v1/student-setup/students/{id}/guardians                     → guardians with parent record, login state, other children
GET  /api/v1/student-setup/students/{id}/siblings
POST /api/v1/student-setup/students/{id}/guardians/{gid}/parent        { "parentUserId": null, "login": true }
DELETE /api/v1/student-setup/students/{id}/guardians/{gid}/parent      → unlink (parent record and other children untouched)
```
`POST …/parent` links a guardian that was added without a parent record (older data, or `createParent: false`): to `parentUserId`, else to an existing parent with the same email / phone, else it creates one. `login: true` creates the parent's login — if the parent already has one, the same login simply gains the child.

Parent login lifecycle is the normal account API: `GET/POST/PATCH /api/v1/tenant/users/{parentUserId}/account`.

### Families — parents with two or three children

A family is everything connected through guardian links: two parents who share three children are **one** family with two parent records (two logins) and three children.

```http
GET /api/v1/student-setup/families?search=sharma
→ [ { "familyKey": "USR-…", "parents": [ ParentSummary, ParentSummary ], "children": [ SiblingRef × 3 ], "logins": 2 } ]
```

How siblings end up on the same parent record:

| How | When |
|---|---|
| Automatically on admission | the guardian's email or phone matches an existing parent → linked, login reused (`warnings` says so, `otherChildren` lists the siblings) |
| By choice | send `parentUserId` on the guardian (search `GET /parents` first); required when two parents share one phone |
| Afterwards | `POST /students/{id}/guardians/{gid}/parent` for guardian rows created without a parent record (`GUARDIAN_NOT_LINKED` in the overview) |

When a parent got duplicated (e.g. one child admitted with the father's email, the next with only his phone, spelled differently):

```http
POST /api/v1/student-setup/parents/{keepParentUserId}/merge      { "mergeParentUserId": "…" }
```
Guardian rows of all children move to the kept record; blank email / phone / address on the kept record are filled from the duplicate; the duplicate's login is moved over when the kept record has none, otherwise disabled; the duplicate record is deleted. A child that already had a guardian row for the kept parent keeps that one — the duplicate's row is unlinked and reported in `warnings`. Needs `STUDENT_GUARDIAN_UPDATE` (+ `USER_UPDATE` when a login is involved).

Leaving: when one child leaves, the parent login stays while another child is active (`parentLoginsKept`); it is disabled only when the last child leaves.

## 6. Leaving and coming back

```http
POST /api/v1/student-setup/students/{id}/leave
{ "type": "TRANSFERRED", "date": "2026-09-30", "reason": "Family moved", "tcNumber": "TC/2026/18", "disableLogins": true }
```
`type` is `TRANSFERRED`, `WITHDRAWN` or `GRADUATED`. Student status changes, open enrollments are closed (`WITHDRAWN`, or `COMPLETED` for graduates), the TC number and leaving details are stored on the profile, the student's login is disabled, and each parent's login is disabled **unless the parent still has another active child** (`parentLoginsKept` lists them). Needs `STUDENT_WITHDRAW` (+ `USER_UPDATE` to touch logins).

```http
POST /api/v1/student-setup/students/{id}/readmit
{ "academicYearId": "…", "academicClassId": "…", "sectionId": "…", "rollNumber": "7", "enableLogins": true }
```
For `WITHDRAWN` / `INACTIVE` students: same record and admission number, new enrollment, leaving info cleared, logins re-enabled on request. `TRANSFERRED` / `GRADUATED` students are admitted afresh (new record).

## 7. Overview

```http
GET /api/v1/student-setup/overview?academicYearId=…        (default: current year)
```
`activeStudents`, `newAdmissions`, `leftThisYear`, `withoutSection`, `byStatus`, `classes[]` → `sections[]` with `students / capacity / full`, `parents`, `parentsWithLogin`, `studentsWithLogin`, `pendingPromotion`, and `issues[]`:

| Level | Code | Fix |
|---|---|---|
| WARN | `PENDING_PROMOTION` | run the promotion from last year |
| WARN | `NO_SECTION` | `PUT /api/v1/enrollments/{id}/transfer` |
| WARN | `SECTION_OVER_CAPACITY` | move students or raise capacity |
| WARN | `NO_GUARDIAN` | `POST /api/v1/students/{id}/guardians` |
| INFO | `NO_PARENT_LOGIN` | link + `login: true` |
| INFO | `GUARDIAN_NOT_LINKED` | `POST …/guardians/{gid}/parent` |
| INFO | `NO_ROLL_NUMBER`, `NO_DATE_OF_BIRTH` | profile update |

The academic-year checklist (`GET /api/v1/academic-years/{id}/setup-status`) has the `STUDENTS` and `PARENT_LOGINS` steps.

## 8. Suggested screens

| Screen | Read | Write |
|---|---|---|
| Enrollment dashboard | `GET /overview` | links in `issues` |
| Admission form | classes, sections, subjects, `GET /admission-numbers` (shows the next number) | `POST /admit`; show the temporary password(s) once |
| Import students (sheet) | — | `POST /admit/bulk` with `dryRun`, then for real |
| Start of year | `POST …/promotion/preview` | `POST …/promotion` |
| Student profile | `GET /students/{id}` | `PUT /students/{id}` |
| Student profile: Parents tab | `GET /students/{id}/guardians` | `POST /api/v1/students/{id}/guardians`, `POST …/guardians/{gid}/parent` |
| Student profile: Account tab | `GET /tenant/users/{tenantUserId}/account` | `POST`, `PATCH …/status` |
| Parent directory / families | `GET /parents`, `GET /families` | `POST /parents/{id}/merge` for duplicates |
| Leaving / TC | — | `POST /students/{id}/leave`; show `nextSteps` |
| Re-admission | — | `POST /students/{id}/readmit` |

## 9. TypeScript types

```ts
type Relationship = 'FATHER' | 'MOTHER' | 'LEGAL_GUARDIAN' | 'GRANDPARENT' | 'SIBLING' | 'OTHER';
type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'TRANSFERRED' | 'GRADUATED' | 'WITHDRAWN' | 'DELETED';
type LeavingType = 'TRANSFERRED' | 'WITHDRAWN' | 'GRADUATED';

interface GuardianRequest { name: string; relationship: Relationship; phone?: string; email?: string; occupation?: string;
  annualIncome?: number; addressLine1?: string; addressLine2?: string; city?: string; state?: string; postalCode?: string;
  primary?: boolean; emergencyContact?: boolean; parentUserId?: string; linkExisting?: boolean; createParent?: boolean; login?: boolean; }
interface ProfileDetails { bloodGroup?: string; nationality?: string; motherTongue?: string; house?: string; previousSchool?: string;
  previousClass?: string; tcNumberIn?: string; medicalNotes?: string; photoUrl?: string; }
interface AdmitStudentRequest { firstName: string; lastName?: string; gender?: string; dateOfBirth?: string; email?: string; phone?: string;
  addressLine1?: string; addressLine2?: string; city?: string; state?: string; postalCode?: string;
  admissionNumber?: string; admissionDate?: string; academicYearId: string; academicClassId: string; sectionId?: string; rollNumber?: string;
  autoAssignCompulsorySubjects?: boolean; subjectIds?: string[]; remarks?: string; details?: ProfileDetails;
  guardians?: GuardianRequest[]; studentLogin?: boolean; }
interface LoginInfo { userId: string; userCode: string; email: string; role: string; status: string; created: boolean;
  temporaryPassword: string | null; extraRoles: string[]; }
interface SiblingRef { studentId: string; studentCode: string; name: string; admissionNumber: string; className: string | null;
  sectionName: string | null; status: StudentStatus; }
interface GuardianWithParent { guardian: StudentGuardianResponse; parentUserId: string | null; parentUserCode: string | null;
  parentHasLogin: boolean; parentLogin: LoginInfo | null; otherChildren: SiblingRef[]; }
interface AdmitResult { student: StudentDetailResponse; enrollment: EnrollmentResponse; guardians: GuardianWithParent[];
  studentLogin: LoginInfo | null; siblings: SiblingRef[]; warnings: string[]; }
interface BulkAdmitRow { index: number; name: string; admissionNumber: string; error: string | null; studentCode: string | null;
  studentId: string | null; warnings: string[]; }
interface BulkAdmitResult { dryRun: boolean; valid: boolean; admitted: number; parentLoginsCreated: number; rows: BulkAdmitRow[]; }
interface StudentFullProfile { student: StudentDetailResponse; details: ProfileDetailsResponse | null; currentEnrollment: CurrentEnrollmentResponse | null;
  enrollmentHistory: EnrollmentResponse[]; guardians: GuardianWithParent[]; siblings: SiblingRef[];
  attendance: { academicYearId: string; present: number; absent: number; late: number; total: number; percentage: number } | null;
  tenantUserId: string; hasLogin: boolean; }
interface Family { familyKey: string; parents: ParentSummary[]; children: SiblingRef[]; logins: number; }
interface MergeParentsResult { parent: ParentSummary; mergedParentUserId: string; guardianRowsMoved: number; loginMoved: boolean;
  duplicateLoginDisabled: boolean; warnings: string[]; }
interface ParentSummary { parentUserId: string; userCode: string; name: string; email: string | null; phone: string | null; hasLogin: boolean; children: SiblingRef[]; }
interface LeaveResult { status: StudentStatus; date: string; tcNumber: string | null; enrollmentsClosed: number; studentLoginDisabled: boolean;
  parentLoginsDisabled: string[]; parentLoginsKept: string[]; nextSteps: string[]; }
interface EnrollmentOverview { academicYearId: string; academicYear: string; activeStudents: number; newAdmissions: number; leftThisYear: number;
  withoutSection: number; byStatus: Record<StudentStatus, number>;
  classes: { classId: string; className: string; students: number; withoutSection: number;
             sections: { sectionId: string; section: string; students: number; capacity: number | null; full: boolean }[] }[];
  parents: number; parentsWithLogin: number; studentsWithLogin: number; pendingPromotion: number;
  issues: { level: 'WARN' | 'INFO'; code: string; count: number; message: string; examples: string[] }[]; }
```

## 10. Database

Tenant `V23__student_enrollment_setup.sql`: `student_profile_detail` (1:1 with `student`: blood group, nationality, mother tongue, house, previous school / class, TC in / out, medical notes, photo URL, leaving type / date / reason), `admission_sequence` (per year or school-wide: prefix, next number, padding), indexes on `student_guardian(lower(email))` and `tenant_user(phone)`.
Existing: `student` (V4), `student_academic_enrollment` / `student_subject_enrollment` / `student_guardian` (V10), `student_guardian.guardian_user_id` → parent `tenant_user` (V19).

## 11. Permissions

| Action | Authority |
|---|---|
| overview, profile read, siblings | `STUDENT_READ` (own scope: own children / self) |
| admission numbers, admit, bulk | `STUDENT_CREATE` (+ `USER_CREATE` when logins are requested) |
| profile update | `STUDENT_UPDATE` |
| parents directory, guardians | `STUDENT_GUARDIAN_READ` |
| link / unlink parent | `STUDENT_GUARDIAN_UPDATE` (+ `USER_CREATE` for a login) |
| families | `STUDENT_GUARDIAN_READ` |
| merge parents | `STUDENT_GUARDIAN_UPDATE` (+ `USER_UPDATE` when a login moves) |
| leave | `STUDENT_WITHDRAW` (+ `USER_UPDATE` to disable logins) |
| readmit | `STUDENT_ENROLL` (+ `USER_UPDATE` to re-enable logins) |
| promotion | `STUDENT_PROMOTE` |
