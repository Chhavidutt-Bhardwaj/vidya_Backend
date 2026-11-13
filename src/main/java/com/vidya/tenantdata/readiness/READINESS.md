# Validation & Readiness

One answer to "can this academic year go live?": configuration that does not add up, data that contradicts other data,
what is still missing, and work waiting for someone's approval. Read-only; nothing is changed.

`GET /api/v1/academic-years/{id}/readiness` · permission `ACADEMIC_YEAR_READ`

```jsonc
{
  "academicYearId": "…", "code": "AY2026-27", "name": "2026-27", "status": "DRAFT", "current": false,
  "verdict": "BLOCKED",              // READY | READY_WITH_WARNINGS | BLOCKED
  "readyToGoLive": false,            // no blockers (warnings may remain)
  "percentComplete": 72,             // from the setup checklist
  "summary": { "blockers": 2, "warnings": 5, "notes": 3, "pendingApprovals": 3 },
  "categories": [                    // always these four, in this order
    { "key": "CONFIGURATION", "label": "Configuration", "blockers": 1, "warnings": 1, "notes": 0, "checks": [
      { "code": "INSTALLMENTS_NOT_100", "category": "CONFIGURATION", "area": "FEES", "severity": "BLOCKER",
        "message": "Instalment percentages do not add up to 100%; fees cannot be split.", "count": 1,
        "samples": [{ "id": "…", "label": "Instalments add up to 90.00%" }],
        "api": "PUT /api/v1/operations-config/fees/installments" } ] },
    { "key": "CONFLICTS", … }, { "key": "MISSING_DATA", … }, { "key": "PENDING_APPROVALS", … }
  ],
  "notChecked": ["STUDENTS"],        // areas the user may not read, or not in the school's plan
  "checkedAt": "2026-11-13T10:00:00Z"
}
```

- `severity`: `BLOCKER` (the school cannot run on this), `WARNING` (fix before going live), `INFO`.
- Inside a category, checks are sorted blockers first. `count` is the number of rows found; `samples` shows up to five
  (link them with `id`). `api` is the endpoint that fixes it.
- Each check runs only if the user holds the permission to read that area; otherwise the area appears in `notChecked`.

## Checks

| Category | Code | Severity | Source |
|---|---|---|---|
| Configuration | `TERM_OUTSIDE_YEAR`, `TERM_WEIGHTAGE_NOT_100`, `HOLIDAY_OUTSIDE_YEAR`, `INSTALLMENT_AFTER_YEAR_END`, `FEE_STRUCTURE_INACTIVE_HEAD`, `TIMETABLE_BELL_SCHEDULE_INACTIVE` | WARNING | rule |
| | `INSTALLMENTS_NOT_100` | BLOCKER | rule |
| | Operations issues (`VEHICLE_PAPERS_EXPIRED`, `NO_CHANNELS`, `SMS_SENDER_ID_MISSING`…) | ERROR → BLOCKER, WARNING, INFO | `/operations-config/overview`, areas the school has started |
| Conflicts | `TIMETABLE_CLASHES` (one per timetable) | BLOCKER on a published/active timetable, WARNING on a draft | timetable clash validator |
| | `INACTIVE_TEACHER_ASSIGNED`, `SECTION_CLASS_MISMATCH`, `FEE_STRUCTURE_MIXED` | BLOCKER | rule |
| | `TEACHER_OVERLOADED`, `SECTION_OVER_CAPACITY`, `TERMS_OVERLAP`, `EXAM_OUTSIDE_TERM`, `EXAM_ON_HOLIDAY`, `EXAM_PAPERS_CLASH` | WARNING | rule |
| Missing data | `MISSING_<STEP>` for unfinished checklist steps | REQUIRED → BLOCKER, RECOMMENDED → WARNING | `/academic-years/{id}/setup-status` |
| | `<AREA>_NOT_SET_UP` (fees, transport, library…) | INFO | operations overview |
| | `STUDENTS_WITHOUT_ROLL_NUMBER` | INFO | rule |
| Pending approvals | `LESSON_PLANS_TO_REVIEW`, `CURRICULUM_DRAFT`, `SYLLABUS_DRAFT`, `TEACHER_ASSIGNMENT_DRAFT`, `FEE_STRUCTURE_DRAFT`, `TIMETABLE_NOT_PUBLISHED`, `MARKS_NOT_LOCKED` | WARNING | rule |
| | `EXAM_SCHEDULE_DRAFT`, `REPORT_CARDS_NOT_PUBLISHED`, `YEAR_NOT_STARTED` | INFO | rule |

Not checked because the database already prevents them: two current years, two active class teachers in one section,
two live enrolments of one student in a year.

## Code

`service/ReadinessRules.java` holds the single-query rules: each returns the offending rows as `(id, label)`. Add a rule
there; the service handles permissions, counting, samples and sorting. `service/ReadinessService.java` combines the
rules with the setup checklist, the operations overview and the timetable validator.
