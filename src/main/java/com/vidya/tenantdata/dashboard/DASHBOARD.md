# School overview dashboard

What a school admin, owner or principal sees right after signing in: who is on the rolls, today's attendance, what is
happening in the school today, and what is waiting for their approval. Read-only aggregates over modules that already
exist; nothing here owns data apart from "dismissed" action items.

Requirements: `APiReqDoc/01_OVERVIEW.md`. Base path `/api/v1/dashboard`. Tenant resolved from the token as usual.

## 1. Endpoints

| Endpoint | What | Extra permission (besides `DASHBOARD_READ`) |
|---|---|---|
| `GET /dashboard?academicYearId=&from=&to=` | Everything below in one call (first 5 action items) | per block |
| `GET /dashboard/kpis?academicYearId=&from=&to=` | Enrolment, attendance, admissions, fees | per block |
| `GET /dashboard/snapshot?academicYearId=` | Students, staff, classes / sections / subjects, timetable state | per block |
| `GET /dashboard/operations-pulse` | Today: attendance marking, substitutions, next exam, fleet | per tile |
| `GET /dashboard/quick-counts` | Badge numbers for today | per count |
| `GET /dashboard/attendance-trend?from=&to=` | Daily attendance rate (≤ 366 days) | `ATTENDANCE_READ` + attendance scope ALL |
| `GET /dashboard/enrollment-by-class?academicYearId=` | Roll, capacity and occupancy per class | `STUDENT_READ` + student scope ALL |
| `GET /dashboard/fee-trend?academicYearId=&from=&to=` | Fees collected per day, with running total (≤ 366 days) | `FEE_READ` + fee scope ALL |
| `GET /dashboard/fee-breakdown?academicYearId=` | Billed / paid / due per fee head and per class | `FEE_READ` + fee scope ALL |
| `GET /dashboard/auto-debit?month=YYYY-MM` | Mandates and this month's auto-debits (scheduled, at the bank, collected, bounced, refused) | `FEE_READ` + fee scope ALL |
| `GET /dashboard/export?academicYearId=&from=&to=` | The home screen as CSV (`section,metric,value`) | per block |
| `GET /dashboard/action-items?academicYearId=&type=&page=&size=` | Waiting for my approval, oldest first | per type |
| `POST /dashboard/action-items/{id}/dismiss` | Hide one item for me (does not approve / reject) | the item type's permission |
| `GET /dashboard/campuses` | School name, board, status and branches (master data, `master/school`) | — |

Defaults: `academicYearId` = current year; `to` = today; `from` = first day of `to`'s month. `from > to` → 400.
The pulse and quick counts are always about **today** (school timezone, `vidya.school.timezone`).

## 2. Access rule

`DASHBOARD_READ` (master V39) opens the page; it is granted to `PLATFORM_ADMIN`, `SCHOOL_OWNER`, `SCHOOL_ADMIN`,
`PRINCIPAL`, `COORDINATOR`. It never shows a number on its own: each block also needs its module's read permission and,
for person data, **school-wide scope** (`<DOMAIN>_SCOPE_ALL`), because the numbers are whole-school totals. A block the user
cannot see is `null` and listed in `unavailable`:

```json
"unavailable": [
  { "block": "fees",             "reason": "NO_PERMISSION",    "message": "Needs FEE_READ with school-wide fee scope." },
  { "block": "admissions.leads", "reason": "NOT_BUILT",        "message": "Enquiries and applications need the admissions pipeline (03_ADMISSIONS.md)." },
  { "block": "enrollment",       "reason": "NO_ACADEMIC_YEAR", "message": "Create and start an academic year first." }
]
```

| Block | Needs |
|---|---|
| enrollment, admissions, snapshot.students, enrollment-by-class | `STUDENT_READ` + `STUDENT_SCOPE_ALL` |
| attendance, pulse ATTENDANCE, attendance-trend, quick count of sections to mark | `ATTENDANCE_READ` + `ATTENDANCE_SCOPE_ALL` |
| snapshot.staff | `STAFF_READ` + `STAFF_SCOPE_ALL` |
| snapshot.academics | `ACADEMIC_YEAR_READ` |
| fees | `FEE_READ` + `FEE_SCOPE_ALL` |
| pulse PROXIES, substitutions today | `SUBSTITUTION_READ` |
| pulse NEXT_EXAM | `EXAM_READ` |
| pulse TRANSPORT | `TRANSPORT_READ` |
| autoDebit, pulse AUTO_DEBIT | `FEE_READ` + `FEE_SCOPE_ALL` |
| action items `FEE_DEBIT_ERROR`, `FEE_UNALLOCATED`, `FEE_MANDATE_STALLED` | `FEE_MANAGE` + `FEE_SCOPE_ALL` |
| upcoming holidays | `CALENDAR_READ` |
| action items `LESSON_PLAN_REVIEW` | `LESSON_PLAN_REVIEW` + `STAFF_SCOPE_ALL` |
| action items `MARKS_LOCK` | `MARKS_LOCK` + `MARKS_SCOPE_ALL` |
| action items `REPORT_CARD_PUBLISH` | `REPORT_CARD_PUBLISH` + `MARKS_SCOPE_ALL` |
| action items `REEVALUATION_REQUEST` | `REEVALUATION_MANAGE` |

## 3. How the numbers are computed

| Number | Rule |
|---|---|
| Active enrolment | Distinct students with an `ADMITTED` / `ACTIVE` enrolment in the year (soft-deleted students excluded) |
| Capacity | Sum of `section.capacity` of the year's active sections; `null` if none is set |
| Enrolment delta | % change vs the previous academic year's students (`ADMITTED`, `ACTIVE`, `PROMOTED`, `COMPLETED`) |
| Attendance rate | Same formula as the student attendance summary: (present + excused + ½ half-day + late × late weight) ÷ marks. Late weight comes from `attendance settings → lateCountsAs` |
| Attendance delta | Percentage **points** vs the mean daily rate of the previous 30 days that have marks |
| Sections marked | Sections with live students that have at least one mark on the date |
| Admissions | Students whose `admission_date` is in the window, vs the window of the same length just before |
| Next exam | Earliest paper (`exam_subject_config`) of a `PUBLISHED` / `IN_PROGRESS` exam of the year from today |
| Fees collected | Payments realized in the window (`SUCCESS`; cheques on their clearance date). Delta: % change vs the window of the same length just before |
| Fees target | Net payable (amount − concession) of ledger lines falling due in the window |
| Collection rate | Paid ÷ net payable of the lines due by `to` |
| Auto-debit | `fee_debit` of the month's cycle per status; in progress = `SUBMITTING` + `SUBMITTED`; bounce charges = bounce charge + GST of the month's debits; mandates = `fee_mandate` ACTIVE / PENDING now |
| Transport attention | Vehicle in maintenance, or insurance / fitness / permit / PUC expiring within 30 days |

## 4. Action items

```json
{ "id": "LESSON_PLAN_REVIEW:6f1c…", "type": "LESSON_PLAN_REVIEW",
  "title": "Lesson plan to review: Fractions", "description": "Class 6 A · Maths · 2026-11-24",
  "raisedBy": "Asha Rao", "raisedAt": "2026-11-20T10:00:00Z", "entityId": "6f1c…",
  "href": "/academics?tab=lesson-plans&id=6f1c…", "api": "POST /api/v1/academic-config/lesson-plans/6f1c…/review" }
```

- Kinds: `LESSON_PLAN_REVIEW` (submitted plans), `MARKS_LOCK` (papers with submitted marks), `REPORT_CARD_PUBLISH`
  (generated cards per term and class; key `termId:classId`), `REEVALUATION_REQUEST` (requested / under review),
  `FEE_CONCESSION` (pending concession requests; never shown to whoever asked, since they cannot approve it),
  `FEE_DEBIT_ERROR` (latest attempt of a debit refused by the gateway, ledger of the year), `FEE_UNALLOCATED` (link / order
  with `unallocated_paise > 0` for the year's ledgers: refund), `FEE_MANDATE_STALLED` (mandate `PENDING` for over 3 days, any year).
- Approve through `api` in the owning module; the item then disappears on its own.
- Dismiss stores `(user, type, key)` in tenant table `dashboard_action_dismissal` (tenant V28; types extended in V29 and V31). Idempotent. The item stays
  pending for everyone else.
- At most 500 items per kind are read; paging happens after merging the kinds.

## 5. Not built yet (reported as `NOT_BUILT`)

Admissions funnel and leads (need the
admissions pipeline), live trips (need transport trips), APAAR / UDISE+ sync (not stored), multi-campus comparison
(students and staff have no branch in the school database), PDF / xlsx export (CSV is built). See `APiReqDoc/01_OVERVIEW.md` for status.

## 6. Code

| Piece | File |
|---|---|
| SQL (aggregates, dismissal) | `repository/DashboardQueries.java` |
| Rules, access, arithmetic | `service/DashboardService.java` (fee figures from `tenantdata/fee/service/FeeReportService`) |
| HTTP | `controller/DashboardController.java`, campuses: `master/school/controller/SchoolCampusController.java` |
| Export (CSV) | `DashboardService.exportCsv` |
| Tests | `DashboardServiceTest`, `DashboardQueriesPostgresIntegrationTest`, `SchoolCampusServiceTest` |
