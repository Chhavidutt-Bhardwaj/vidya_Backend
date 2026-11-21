# Academic Year Planning: Frontend Integration Guide

> Classes, sections, streams, subjects, curriculum and grading scales are configured as described in [`structure/ACADEMIC_STRUCTURE.md`](structure/ACADEMIC_STRUCTURE.md). This guide covers the yearly cycle.

How a school admin plans an academic year from the school dashboard: create the year, set it up, track progress, then move students into it at year end.

All endpoints are tenant APIs. The tenant is resolved from the logged-in user's token, so there is no school id in the path. They return the usual `ApiResponse` envelope (see `master/school/FRONTEND_INTEGRATION.md` §1.2).

---

## 1. The yearly cycle

```
 Next year:  setup (copy) → sections / subjects / teachers → holidays → timetable → promotion → start → close old year

           ┌──────────── first year only ────────────┐
 Classes ──┤ (sortOrder = promotion order)            │
 Subjects ─┘                                          │
                                                      ▼
 1. Create next year ──► 2. Finish setup ──► 3. Promote students ──► 4. Make current ──► 5. Close old year
  POST /academic-years     GET …/setup-status   POST …/{old}/promotion   (flag on 3, or      (flag on 3, or
  /setup (copy from         steps → screens      (preview first)          PATCH /{id}/current) PATCH /{id}/close)
  last year)
```

| Year status | Meaning | Allowed next |
|---|---|---|
| `DRAFT` | Being planned; invisible to teachers' day-to-day screens | `ACTIVE`, `CANCELLED`, `DELETED` |
| `ACTIVE` | Running (or ready to run) | `CLOSED`, `DELETED` |
| `CLOSED` | Finished; read-only history | `DELETED` |
| `CANCELLED` | Abandoned plan | `DELETED` |

- Exactly **one** year is `current` (`isCurrent`). Attendance, timetables and dashboards default to it. Setting a DRAFT year current also activates it.
- **Year dates may not overlap** another non-cancelled year. Create and update return 400 with the clashing year's code.
- A year with sections cannot be deleted. Cancel it instead.

---

## 2. Endpoints

| Purpose | Method & path | Permission |
|---|---|---|
| List years | `GET /api/v1/academic-years?status=&isCurrent=&page=&size=` | `ACADEMIC_YEAR_READ` |
| Current year | `GET /api/v1/academic-years/current` | `ACADEMIC_YEAR_READ` |
| **One-call setup** | `POST /api/v1/academic-years/setup` | `ACADEMIC_YEAR_CREATE` |
| **Setup checklist** | `GET /api/v1/academic-years/{id}/setup-status` | `ACADEMIC_YEAR_READ` |
| Plain create / edit | `POST /api/v1/academic-years`, `PUT /{id}` | `ACADEMIC_YEAR_CREATE` / `_UPDATE` |
| Activate / make current / close | `PATCH /{id}/activate`, `/{id}/current`, `/{id}/close` | `ACADEMIC_YEAR_UPDATE` |
| Bulk sections | `POST /api/v1/academic-years/{id}/sections/bulk` | `SECTION_CREATE` |
| Class subjects from curriculum | `POST /api/v1/academic-years/{id}/class-subjects/from-curriculum` | `CLASS_SUBJECT_ASSIGNMENT_CREATE` |
| Bulk class teachers | `POST /api/v1/academic-years/{id}/class-teachers/bulk` | `CLASS_TEACHER_ASSIGNMENT_CREATE` + staff scope ALL |
| Holiday suggestions | `GET /api/v1/academic-years/{id}/holidays/suggestions?fromAcademicYearId=` | `CALENDAR_READ` |
| Add holidays in bulk | `POST /api/v1/academic-years/{id}/holidays/bulk` | `CALENDAR_MANAGE` |
| Timetable carry-over | `POST /api/v1/academic-years/{id}/timetable/carry-over` | `TIMETABLE_CREATE` + timetable scope ALL |
| **Start the year** | `POST /api/v1/academic-years/{id}/start` | `ACADEMIC_YEAR_UPDATE` |
| **Promotion preview** | `POST /api/v1/academic-years/{oldId}/promotion/preview` | `STUDENT_PROMOTE` + student scope ALL |
| **Promotion** | `POST /api/v1/academic-years/{oldId}/promotion` | `STUDENT_PROMOTE` + student scope ALL |

Show the buttons with `can('ACADEMIC_YEAR_CREATE')`, `can('STUDENT_PROMOTE') && dataScope('STUDENT') === 'ALL'`, and so on (see `docs/security/DYNAMIC_ACCESS_CONTROL.md` §7). `STUDENT_PROMOTE` is seeded for school owner, school admin and principal.

---

## 3. Create a year: `POST /academic-years/setup`

One transaction: either everything below is created, or nothing is.

```jsonc
{
  "code": "AY2027-28",                 // * unique
  "name": "Academic Year 2027-2028",   // *
  "startDate": "2027-04-01",           // *
  "endDate": "2028-03-31",             // * after startDate, no overlap with other years
  "makeCurrent": false,                // usually false: switch after promotion

  "terms": [                           // optional; within the year, no overlap, weightage total ≤ 100
    { "name": "Term 1", "startDate": "2027-04-01", "endDate": "2027-09-30", "weightagePercentage": 40 },
    { "name": "Term 2", "startDate": "2027-10-01", "endDate": "2028-03-31", "weightagePercentage": 60 }
  ],
  "workingDays": {                     // optional; omitted days default Mon–Sat on, Sun off
    "saturday": true, "saturdayPattern": "SECOND_FOURTH"   // ALL | FIRST_THIRD_FIFTH | SECOND_FOURTH
  },

  "copyFrom": {                        // optional: start from last year's structure
    "academicYearId": "…previous year…",
    "sections": true,                  // default true  – same section codes per class
    "curricula": true,                 // default true  – curricula + their class subjects
    "classSubjects": true,             // default true  – subjects per class / section
    "terms": true,                     // default true  – shifted to the new dates (ignored if "terms" given)
    "workingDays": true,               // default true  – (ignored if "workingDays" given)
    "classTeachers": false,            // default false – same class teacher per section
    "subjectTeachers": false           // default false – same subject teachers
  }
}
```

Response `201`:

```jsonc
{
  "academicYear": { "id": "…", "code": "AY2027-28", "status": "DRAFT", "isCurrent": false, … },
  "created": { "sections": 24, "curricula": 1, "curriculumSubjects": 96, "classSubjects": 110, "terms": 2, "workingDays": 1 },
  "warnings": [
    "Term dates were shifted by 365 day(s) from AY2026-27; review them.",
    "Holidays, timetables and bell-schedule changes are not copied: add this year's holidays and build its timetable."
  ],
  "status": { …same as setup-status below… }
}
```

What is never copied: **holidays** (dates change every year), **timetables** (build one per year), **students** (see promotion), and anything inactive or deleted. Classes, subjects, rooms, bell schedules and grading scales are school-wide, so they don't need copying.

**First year ever:** omit `copyFrom`. Create classes and subjects first, or follow the checklist afterwards.

---

## 4. Setup checklist: `GET /academic-years/{id}/setup-status`

Drive a "Year setup" progress screen from it. The steps are already in the order the admin should work through them.

```jsonc
{
  "academicYearId": "…", "code": "AY2027-28", "status": "DRAFT", "current": false,
  "requiredDone": 4, "requiredTotal": 5, "percentComplete": 58, "readyToStart": false,
  "steps": [
    { "key": "CLASSES",          "label": "Classes (grades)",        "level": "REQUIRED",    "done": true,  "count": 14, "pending": 0, "hint": "", "api": "POST /api/v1/academic-classes" },
    { "key": "SECTIONS",         "label": "Sections for this year",  "level": "REQUIRED",    "done": true,  "count": 24, "pending": 0, … },
    { "key": "CLASS_TEACHERS",   "label": "Class teachers",          "level": "RECOMMENDED", "done": false, "count": 20, "pending": 4,
      "hint": "4 section(s) have no class teacher. Teachers only see their own sections.", … },
    { "key": "STUDENTS",         "label": "Students enrolled",       "level": "REQUIRED",    "done": false, "count": 0,  … }
  ]
}
```

| key | level | done when | screen |
|---|---|---|---|
| `CLASSES` | REQUIRED | ≥1 active class | Classes (`/academic-classes`). Set **sortOrder in promotion order**. |
| `SECTIONS` | REQUIRED | every active class has a section this year (`pending` = classes without one) | Sections (`/sections`) |
| `SUBJECTS` | REQUIRED | ≥1 active subject | Subjects (`/subjects`) |
| `CURRICULUM` | RECOMMENDED | ≥1 curriculum this year | Curricula (`/curricula`) |
| `CLASS_SUBJECTS` | REQUIRED | every class with sections has subjects | Class subjects (`/class-subject-assignments`) |
| `CLASS_TEACHERS` | RECOMMENDED | every section has an active class teacher | `/class-teacher-assignments` |
| `SUBJECT_TEACHERS` | RECOMMENDED | every class subject has a teacher | `/teacher-assignments` |
| `TERMS` | RECOMMENDED | ≥1 term | `/exams/terms` |
| `WORKING_DAYS` | RECOMMENDED | config saved (otherwise Mon–Sat is assumed) | `PUT /calendar/config?academicYearId=` |
| `HOLIDAYS` | OPTIONAL | ≥1 holiday | `/holidays` |
| `BELL_SCHEDULE` | RECOMMENDED | ≥1 active bell schedule | `/bell-schedules` |
| `TIMETABLE` | RECOMMENDED | a published / active timetable this year | `/timetables` |
| `STUDENTS` | REQUIRED | ≥1 active enrollment (`pending` = students without a section) | Promotion (§6) and admissions |

- `readyToStart` is true when every REQUIRED step is done. Use it to enable "Make this the current year".
- `percentComplete` counts REQUIRED and RECOMMENDED steps.
- Class teachers and subject teachers matter for more than tidiness: **teachers only see the students, attendance, marks and timetables of the sections they are assigned to** (data-level access). Show those two steps prominently.

---

## 5. Finishing the setup

Each checklist step links (`api`) to the call that completes it. The bulk helpers below cover the slow parts. Each is one transaction.

### 5.1 Sections, class subjects, class teachers

```jsonc
POST /academic-years/{id}/sections/bulk
{ "academicClassIds": [],            // empty = every active class
  "sectionCodes": ["A", "B"], "capacity": 40 }
→ { "created": 26, "skipped": 2, "details": { "classes": 14, "codes": ["A","B"] } }   // existing class+code pairs are skipped

POST /academic-years/{id}/class-subjects/from-curriculum
{ "curriculumId": null }             // null / no body = every DRAFT/ACTIVE curriculum of the year
→ { "created": 96, "skipped": 0 }    // one class-level subject per curriculum subject (compulsory flag and order kept)

POST /academic-years/{id}/class-teachers/bulk
{ "assignments": [ { "sectionId": "…", "staffId": "…" }, … ] }
→ { "created": 24, "skipped": 2 }    // sections that already have a class teacher are left as they are
```

Class teachers use the normal rules (active staff, one class teacher per section). If any row is invalid, nothing is saved and the error names the section. To change an existing class teacher, use `POST /class-teacher-assignments/{id}/replace`.

### 5.2 Holidays

1. `GET /academic-years/{id}/holidays/suggestions[?region=MH][&fromAcademicYearId=]` returns a list to tick, from three sources. A name already given by an earlier source is not repeated:
   - **NATIONAL**: Republic Day, Independence Day and Gandhi Jayanti on their fixed dates within the year.
   - **PROVIDER**: festivals and public holidays from the external holiday provider (see below), filtered to the school's state. Observances (Mother's Day, equinoxes) are dropped. Restricted holidays have `optional: true`; show them unticked.
   - **PREVIOUS_YEAR**: last year's holidays moved to the same date this year. Festivals have `reviewDate: true` because their dates change every year, so the admin must check them.
   - `alreadyAdded: true` marks names that are already saved this year.

   ```jsonc
   { "region": "IN-MH",                 // from the school's primary address, or ?region=
     "provider": "Calendarific", "providerStatus": "OK",   // OK | STALE | UNAVAILABLE | DISABLED
     "providerFetchedAt": "2027-02-01T10:00:00Z",
     "message": null,                    // why provider rows are missing, show as an info banner
     "attribution": "Holiday data by Calendarific (calendarific.com)",   // show under the list when set
     "suggestions": [ { "source": "PROVIDER", "name": "Maharashtra Day", "startDate": "2027-05-01", "optional": false, "reviewDate": true, … } ] }
   ```

   **Holiday provider (server config, master DB cache).**
   - `vidya.holidays.provider` (`VIDYA_HOLIDAY_PROVIDER`): `auto` (default: Calendarific when `CALENDARIFIC_API_KEY` is set, otherwise off), `calendarific`, `ics`, `none`.
   - **Calendarific**: one call per country and calendar year returns national, religious and state holidays; state filtering happens in Vidya. Get a key at calendarific.com. The free tier is small but enough, because of the cache.
   - **ics**: any public iCal feed in `VIDYA_HOLIDAY_ICS_URL`, e.g. Google's "Holidays in India" calendar. No state data, so everything counts as nationwide; feeds usually cover only last, this and next year.
   - Results are cached in `holiday_catalog_cache` (V34) per provider, country and year, shared by all schools, and refreshed after `VIDYA_HOLIDAY_CACHE_DAYS` (30). If the provider is down, older cached data is returned with `providerStatus: STALE`; with no cache, `UNAVAILABLE` and the other sources still work.
   - Provider dates are what the provider publishes, not the state's notified list, so they are always `reviewDate: true`. Nothing is saved without the admin.
2. The admin edits dates or names and adds state or school-specific days.
3. `POST /academic-years/{id}/holidays/bulk` saves the list:

```jsonc
{ "dryRun": false,
  "holidays": [
    { "name": "Independence Day", "holidayType": "PUBLIC_HOLIDAY", "startDate": "2027-08-15", "endDate": "2027-08-15" },
    { "name": "Summer Vacation",  "holidayType": "VACATION",       "startDate": "2027-05-15", "endDate": "2027-06-30" }
  ] }
```

`holidayType`: `PUBLIC_HOLIDAY` | `SCHOOL_HOLIDAY` | `VACATION` | `EXAM_BREAK`.

- Every row is checked: it must lie inside the year, and must not overlap an existing holiday or another row.
- **All or nothing.** If any row is invalid the response is **422** with `error` set on the bad rows, and nothing is saved.
- `note` flags a one-day holiday that already falls on a non-working day.
- Use `dryRun: true` to validate the list while the admin edits it.

### 5.3 Timetable

Either build the timetable from scratch with `/timetables` and the section grid, or start from last year's:

```jsonc
POST /academic-years/{id}/timetable/carry-over
{ "sourceTimetableId": "…last year's ACTIVE timetable…",
  "name": null,                      // default "<old name> AY2027-28"
  "bellScheduleId": null,            // default: the same schedule; another one is matched by period number
  "teachers": "FROM_ASSIGNMENTS" }   // or "NONE"
→ { "timetable": { "id": "…", "status": "DRAFT", … },
    "sourceSlots": 1080, "copied": 1032, "withTeacher": 990, "withoutTeacher": 42, "teacherClashes": 3,
    "skipped": { "SECTION_NOT_IN_NEW_YEAR": 48 },
    "nextSteps": ["42 slot(s) have no teacher: …", "Check conflicts: POST /api/v1/timetables/…/validate", "Publish: …"] }
```

How each slot is mapped:
- **Section:** the new year's section of the same class and code. Slots whose section doesn't exist this year are skipped and counted.
- **Class subject:** the new year's class subject.
- **Room:** the same room, if still active.
- **Teacher:** this year's subject-teacher assignment for that subject and section, preferring last year's teacher. A slot is left empty rather than double-booking a teacher in the same period (`teacherClashes`).

Copy subject teachers first (in setup `copyFrom.subjectTeachers` or `/teacher-assignments`) to get most slots filled. Then:
1. Fill the gaps in the grid.
2. `POST /timetables/{id}/validate`.
3. `POST /timetables/{id}/publish`.

### 5.4 Start the year: `POST /academic-years/{id}/start`

```jsonc
{ "makeCurrent": true, "timetableId": null, "force": false }
→ { "status": { …checklist… }, "activatedTimetableId": "…", "warnings": [] }
```

1. Refuses (400, listing the missing steps) unless every REQUIRED checklist step is done. `force: true` overrides this and returns a warning.
2. Activates a DRAFT year, then makes it the current year (default).
3. Activates the year's published timetable: the one given, or the only PUBLISHED one. Any other active timetable of that year is archived.

Do this on the first day of term, after promotion.

---

## 6. Year-end promotion

### 6.1 Rules

| Situation | What happens |
|---|---|
| Default | Student goes to the **next active class by `sortOrder`**, into the new year's section with the **same code** (5-A → 6-A). |
| Last class | **GRADUATE**: enrollment `COMPLETED`, student `GRADUATED`, no new enrollment. |
| Section missing or full | Enrolled **without a section** and counted in `withoutSection`. Assign a section later, or create the section and re-run. |
| Already enrolled in the target year | **SKIP**. Re-running is safe. |
| Student's class is inactive | **SKIP** (never guessed to graduate); use a class mapping or an override. |
| Old enrollment | `PROMOTED` (promote), `COMPLETED` (detain / graduate) or `WITHDRAWN` (leave), ending on `promotionDate` (default: old year's end date). |
| New enrollment | `ACTIVE` from the new year's start date. Compulsory subjects of the new class are assigned unless `autoAssignSubjects: false`. |

### 6.2 Request (same body for preview and run)

```jsonc
POST /api/v1/academic-years/{oldYearId}/promotion/preview
{
  "targetAcademicYearId": "…",          // * must start after the old year; not CLOSED/CANCELLED
  "promotionDate": "2027-03-31",        // default: old year's end date
  "keepRollNumbers": false,             // keep roll no. when free in the new section
  "autoAssignSubjects": true,
  "makeTargetCurrent": false,           // run only: switch current year afterwards
  "closeSourceYear": false,             // run only: close the old year afterwards
  "classMappings": [                    // override "next class" (null = that class graduates)
    { "fromAcademicClassId": "…UKG…", "toAcademicClassId": "…Class 1…" },
    { "fromAcademicClassId": "…Class 10…", "toAcademicClassId": null }
  ],
  "overrides": [                        // per student
    { "studentId": "…", "action": "DETAIN" },
    { "studentId": "…", "action": "LEAVE", "remarks": "Moved city" },
    { "studentId": "…", "action": "PROMOTE", "targetSectionId": "…6-C…" },
    { "studentId": "…", "action": "SKIP" }
  ]
}
```

`action`: `PROMOTE` | `DETAIN` (same class again) | `GRADUATE` | `LEAVE` | `SKIP`.

### 6.3 Response (same shape for preview and run)

```jsonc
{
  "dryRun": true, "totalStudents": 812,
  "byAction": { "PROMOTE": 760, "DETAIN": 3, "GRADUATE": 45, "LEAVE": 4 },
  "withoutSection": 12,
  "classes": [
    { "fromClass": "Class 5", "toClass": "Class 6", "students": 64, "promoted": 63, "detained": 1, "graduated": 0, "left": 0, "skipped": 0, "withoutSection": 0 },
    { "fromClass": "Class 10", "toClass": "Graduates", "students": 45, "graduated": 45, … }
  ],
  "students": [
    { "studentId": "…", "studentCode": "STU-…", "fromClass": "Class 5", "fromSection": "A", "rollNumber": "7",
      "action": "PROMOTE", "toClass": "Class 6", "toSection": "A", "newEnrollmentId": null, "note": null }
  ],
  "warnings": ["12 student(s) will be enrolled without a section. …"]
}
```

### 6.4 Suggested screen

1. **Pick years.** From = the current year; To = the next DRAFT/ACTIVE year. If none exists, link to §3. Show the To year's `setup-status`. Promotion needs its SECTIONS step done to place students in sections.
2. **Preview.** Call `/promotion/preview` and show the `classes` table plus `warnings`.
3. **Adjust.** Per class, expand `students` and let the admin mark Detain / Leave / Skip or choose a section. Keep the choices as `overrides` and re-run the preview after each change (it is read-only and fast).
4. **Run.** Ask for confirmation showing `byAction`, then POST `/promotion` with the same body. Optionally tick "Make 2027-28 current" and "Close 2026-27".
5. **After.** Show `withoutSection` students with a link to the enrollment screen, and refresh the new year's `setup-status`.

The run is **all or nothing**. If any student fails validation (e.g. an override's section belongs to another class), nothing is saved and the 400 message names the student.

---

## 7. TypeScript types

```ts
type AcademicYearStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'CANCELLED' | 'DELETED';
type StepLevel = 'REQUIRED' | 'RECOMMENDED' | 'OPTIONAL';
type PromotionAction = 'PROMOTE' | 'DETAIN' | 'GRADUATE' | 'LEAVE' | 'SKIP';

interface AcademicYearSetupRequest {
  code: string; name: string; startDate: string; endDate: string; makeCurrent?: boolean;
  terms?: { name: string; startDate: string; endDate: string; weightagePercentage?: number }[];
  workingDays?: Partial<Record<'monday'|'tuesday'|'wednesday'|'thursday'|'friday'|'saturday'|'sunday', boolean>>
    & { saturdayPattern?: 'ALL' | 'FIRST_THIRD_FIFTH' | 'SECOND_FOURTH' };
  copyFrom?: { academicYearId: string; sections?: boolean; curricula?: boolean; classSubjects?: boolean;
               terms?: boolean; workingDays?: boolean; classTeachers?: boolean; subjectTeachers?: boolean };
}

interface SetupStep { key: string; label: string; level: StepLevel; done: boolean; count: number; pending: number; hint: string; api: string; }
interface SetupStatus {
  academicYearId: string; code: string; name: string; startDate: string; endDate: string;
  status: AcademicYearStatus; current: boolean;
  requiredDone: number; requiredTotal: number; percentComplete: number; readyToStart: boolean;
  steps: SetupStep[];
}
interface AcademicYearSetupResponse {
  academicYear: AcademicYear; created: Record<string, number>; warnings: string[]; status: SetupStatus;
}

interface HolidaySuggestionsResponse { region: string | null; provider: string | null;
  providerStatus: 'OK' | 'STALE' | 'UNAVAILABLE' | 'DISABLED'; providerFetchedAt: string | null; message: string | null;
  attribution: string | null; suggestions: HolidaySuggestion[]; }
interface HolidaySuggestion { source: 'NATIONAL' | 'PROVIDER' | 'PREVIOUS_YEAR'; name: string; holidayType: HolidayType; startDate: string;
  endDate: string; startsOn: string; optional: boolean; reviewDate: boolean; alreadyAdded: boolean; description: string | null; }
type HolidayType = 'PUBLIC_HOLIDAY' | 'SCHOOL_HOLIDAY' | 'VACATION' | 'EXAM_BREAK';
interface HolidayPlanResponse { dryRun: boolean; valid: boolean; created: number; totalHolidayDays: number;
  holidays: { index: number; name: string; holidayType: HolidayType; startDate: string; endDate: string; startsOn: string;
              days: number; error: string | null; note: string | null }[]; }
interface TimetableCarryOverResponse { timetable: Timetable; sourceSlots: number; copied: number; withTeacher: number;
  withoutTeacher: number; teacherClashes: number; skipped: Record<string, number>; nextSteps: string[]; }
interface BulkResult { created: number; skipped: number; details: Record<string, unknown>; }
interface StartYearResponse { status: SetupStatus; activatedTimetableId: string | null; warnings: string[]; }

interface PromotionRequest {
  targetAcademicYearId: string; promotionDate?: string; keepRollNumbers?: boolean; autoAssignSubjects?: boolean;
  makeTargetCurrent?: boolean; closeSourceYear?: boolean;
  classMappings?: { fromAcademicClassId: string; toAcademicClassId: string | null }[];
  overrides?: { studentId: string; action: PromotionAction; targetAcademicClassId?: string; targetSectionId?: string; remarks?: string }[];
}
interface PromotionResult {
  dryRun: boolean; sourceAcademicYearId: string; targetAcademicYearId: string; totalStudents: number;
  byAction: Partial<Record<PromotionAction, number>>; withoutSection: number;
  classes: { fromClassId: string; fromClass: string; toClassId: string | null; toClass: string; students: number;
             promoted: number; detained: number; graduated: number; left: number; skipped: number; withoutSection: number }[];
  students: { studentId: string; studentCode: string; enrollmentId: string; fromClass: string; fromSection: string | null;
              rollNumber: string | null; action: PromotionAction; toClassId: string | null; toClass: string | null;
              toSectionId: string | null; toSection: string | null; newEnrollmentId: string | null; note: string | null }[];
  warnings: string[];
}
```

---

## 8. Dashboard screens → APIs

| Screen | Load | Actions |
|---|---|---|
| Academic years list | `GET /academic-years?page=0&size=20` (badge the `isCurrent` one) | New year → §3 form, Make current, Close |
| New year wizard | `GET /academic-years` (choose "copy from") | `POST /academic-years/setup` |
| Year setup progress | `GET /academic-years/{id}/setup-status` | Each step links to its screen (`api`); "Start year" when `readyToStart` |
| Sections wizard | classes list | `POST /academic-years/{id}/sections/bulk` |
| Class subjects | curricula of the year | `POST /academic-years/{id}/class-subjects/from-curriculum` |
| Class teachers grid | sections of the year + staff list | `POST /academic-years/{id}/class-teachers/bulk` |
| Holiday planner | `GET /academic-years/{id}/holidays/suggestions` | `POST /academic-years/{id}/holidays/bulk` (dryRun while editing) |
| Timetable | `GET /timetables?academicYearId=` (last year's) | `POST /academic-years/{id}/timetable/carry-over`, then validate / publish |
| Start year | setup-status | `POST /academic-years/{id}/start` |
| Year-end promotion | `GET …/{toId}/setup-status`, `POST …/{fromId}/promotion/preview` | `POST …/{fromId}/promotion` |
