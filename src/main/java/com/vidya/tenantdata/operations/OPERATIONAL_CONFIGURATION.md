# Operational Configuration

Fees, attendance, timetable, examinations, transport, library and communication: the settings a school fills in
before day-to-day work starts. Base path: `/api/v1/operations-config`.

This module is **setup only**. It does not charge students or collect payments, issue library books, allot
students to buses, or send messages. Those are separate modules that read this setup.

| Area | Set up here | Already existed (other endpoints) |
|---|---|---|
| Fees | Fee heads, year settings (late fine, receipts), instalment plan, fee structure per class, concession types | — |
| Attendance | Marking rules | Daily marking `/api/v1/attendance`, working days and holidays `/api/v1/calendar` |
| Timetable | — (readiness only) | `/api/v1/bell-schedules`, `/api/v1/rooms`, `/api/v1/timetables` |
| Examinations | — (readiness only) | `/api/v1/exams/terms`, `/grading-scales`, `/schedules`, `/marks` |
| Transport | Vehicles, routes, stops | — |
| Library | Lending rules, book categories | — |
| Communication | Channels, sender, quiet hours, message templates | — |

## 1. Order of work

1. `GET /overview` — see what is missing.
2. Fees: `POST /fees/heads/standard` → `PUT /fees/settings` → `POST /fees/installments/generate` → `PUT /fees/structures` → make them `ACTIVE`.
3. Attendance: `PUT /attendance/settings`.
4. Transport (if the school runs it): vehicles, then routes with stops.
5. Library: `PUT /library/settings`, categories.
6. Communication: `PUT /communication/settings`, then review templates.

Timetable and examinations are set up on their own endpoints; the overview reports how far they are.

## 2. Overview

`GET /overview?academicYearId=` (default: current year) · permission `OPERATIONS_CONFIG_READ`

```json
{
  "academicYearId": "…", "academicYearName": "2026-27",
  "areas": [{
    "key": "FEES", "label": "Fees", "available": true, "state": "IN_PROGRESS",
    "counts": { "feeHeads": 9, "activeStructures": 10, "draftStructures": 0, "classesWithoutStructure": 2, "installments": 4, "concessionTypes": 1 },
    "issues": [{ "code": "CLASSES_WITHOUT_FEE_STRUCTURE", "severity": "WARNING",
                 "message": "2 class(es) have no active fee structure this year.", "api": "PUT /api/v1/operations-config/fees/structures" }]
  }]
}
```

- `key`: `FEES`, `ATTENDANCE`, `TIMETABLE`, `EXAMINATIONS`, `TRANSPORT`, `LIBRARY`, `COMMUNICATION` (always in this order).
- `state`: `NOT_STARTED` → `IN_PROGRESS` → `READY` (only `INFO` notes left). `NOT_AVAILABLE` with `available: false` when the
  user cannot read the area: the school's plan lacks the feature, or it is not the user's job. Hide or grey out that card.
- `severity`: `ERROR` (blocks use), `WARNING` (fix before going live), `INFO`.

| Area | Issue codes |
|---|---|
| Fees | `NO_FEE_HEADS`, `CLASSES_WITHOUT_FEE_STRUCTURE`, `FEE_STRUCTURE_DRAFT`, `NO_INSTALLMENTS`, `FEE_SETTINGS_DEFAULT` |
| Attendance | `NO_WORKING_DAYS`, `SECTIONS_WITHOUT_CLASS_TEACHER`, `ATTENDANCE_RULES_DEFAULT` |
| Timetable | `NO_BELL_SCHEDULE`, `NO_TIMETABLE`, `NO_ROOMS` |
| Examinations | `CLASSES_WITHOUT_GRADING_SCALE`, `NO_TERMS`, `NO_EXAM_SCHEDULE` |
| Transport | `NO_TRANSPORT`, `VEHICLE_PAPERS_EXPIRED`, `VEHICLE_PAPERS_EXPIRING`, `NO_ROUTES`, `ROUTE_WITHOUT_VEHICLE`, `ROUTE_WITHOUT_STOPS`, `VEHICLE_WITHOUT_DRIVER`, `STOPS_WITHOUT_FEE` |
| Library | `NO_BOOK_CATEGORIES`, `LIBRARY_RULES_DEFAULT` |
| Communication | `NO_CHANNELS`, `SMS_SENDER_ID_MISSING`, `NO_SENDER_NAME`, `TEMPLATE_CHANNEL_OFF`, `COMMUNICATION_DEFAULT` |

## 3. Fees

Read: `FEE_READ` · write: `FEE_CONFIGURE`

| Method | Path | What |
|---|---|---|
| GET | `/fees/heads` | Fee heads, with `structuresUsing` |
| POST | `/fees/heads` | Add a head (`code` is made from the name when left out) |
| POST | `/fees/heads/standard` | Add the usual nine heads, skipping existing ones |
| PUT / DELETE | `/fees/heads/{id}` | Change / delete |
| GET / PUT | `/fees/settings?academicYearId=` | Year settings |
| GET / PUT | `/fees/installments?academicYearId=` | Instalment plan (PUT replaces it) |
| POST | `/fees/installments/generate?academicYearId=` | Equal-share plan from the settings |
| GET | `/fees/structures?academicYearId=&classId=` | Structures with totals and schedule |
| PUT | `/fees/structures` | Save one definition for one or more classes |
| PATCH | `/fees/structures/{id}/status` | `DRAFT` / `ACTIVE` / `ARCHIVED` |
| DELETE | `/fees/structures/{id}` | Delete |
| POST | `/fees/structures/copy` | Carry a year's structures into another year |
| GET / POST / PUT / DELETE | `/fees/concessions[/{id}]` | Concession types |

Rules:

- **Head frequency** says how many times a year a structure's amount is charged: `ONE_TIME` and `ANNUAL` once,
  `HALF_YEARLY` 2, `QUARTERLY` 4, `MONTHLY` 12. A `MONTHLY` head of 2000 is 24000 a year.
- A head's frequency cannot change, and the head cannot be deleted, while a fee structure uses it. Set it `INACTIVE` instead.
- **Instalments**: due dates must rise and none may fall after the academic year ends; percents must add up to exactly 100.
  `generate` builds 1, 2, 4 or 12 equal instalments due on `dueDay`; the last one absorbs the rounding.
- **Fee structure**: one per class × year × `appliesTo`. A class has either one structure for `ALL` students, or
  separate ones for `NEW_ADMISSION` and `EXISTING` — never both kinds. `PUT` with several `classIds` creates or
  replaces one structure per class. New structures are `DRAFT` unless `status` is sent.
- **Totals**: `annualTotal` covers compulsory heads, `optionalTotal` the optional ones (transport, hostel…).
- **Schedule** (per structure): recurring compulsory fees are split by each instalment's percent; `ONE_TIME` heads
  fall due with instalment 1. Empty until the year has instalments.
- **Late fine**: `NONE`, `FIXED`, `PER_DAY` or `PERCENT` (of the overdue amount, max 100), after `graceDays`, capped by `lateFineCap`.
- **Copy**: structures the target year already has are skipped; archived ones are not copied; inactive heads are
  left out (see `notes`). Copies are `DRAFT`. `increasePercent` −50…200, `roundTo` e.g. 50.
- Closed or cancelled academic years cannot be changed.

```json
PUT /fees/structures
{ "academicYearId": "…", "classIds": ["…", "…"], "appliesTo": "ALL", "status": "ACTIVE",
  "items": [{ "feeHeadId": "…", "amount": 2000 }, { "feeHeadId": "…", "amount": 5000 }] }
```

## 4. Attendance

`GET /attendance/settings` (`ATTENDANCE_READ`) · `PUT /attendance/settings` (`ATTENDANCE_CONFIGURE`)

| Field | Meaning | Default |
|---|---|---|
| `backdateDays` | How many days back a teacher may mark or correct. `null` = no limit, `0` = same day only | `null` |
| `lateCountsAs` | How `LATE` counts in the percentage: `PRESENT`, `HALF_DAY`, `ABSENT` | `PRESENT` |
| `minimumPercent` | Minimum attendance the school requires | 75 |
| `markingDeadline` | Time by which attendance should be marked (for reminders) | none |
| `lateAfter` | Arrival time after which a student is late (shown on the marking screen) | none |
| `notifyGuardianOnAbsence` | Whether guardians are told about an absence | false |

Enforced today, in `/api/v1/attendance`:

- **Back-date limit**: marking, bulk marking and corrections older than the limit fail with `ATTENDANCE_BACKDATE_LIMIT`.
  Users with `ATTENDANCE_SCOPE_ALL` (and school admin / owner) are not limited.
- **Summary** (`GET /attendance/students/{id}/summary`): `LATE` is weighted as configured; the response adds
  `minimumRequiredPercentage` and `belowMinimum`.

`markingDeadline`, `lateAfter` and `notifyGuardianOnAbsence` are stored for the UI and for notifications; nothing acts on them yet.

## 5. Transport

Read: `TRANSPORT_READ` · write: `TRANSPORT_MANAGE`

| Method | Path | What |
|---|---|---|
| GET | `/transport/vehicles` | Vehicles with crew, routes and `papers` |
| POST, PUT `/{id}`, DELETE `/{id}` | `/transport/vehicles` | Add / change / delete |
| GET | `/transport/routes` | Routes with stops |
| PUT | `/transport/routes` | Create (no `id`) or replace a route with its stops |
| DELETE | `/transport/routes/{id}` | Delete |

Rules:

- Registration numbers are unique ignoring case, spaces and dashes.
- The driver is a staff member (`driverStaffId`, must be active) or an outside driver (`driverName`, `driverPhone`).
  One staff member drives one vehicle; driver and attendant must differ.
- `papers` lists insurance, fitness, permit and pollution certificates that have expired or expire within 30 days.
- A vehicle on a route cannot be deleted; an `INACTIVE` vehicle cannot be put on a route.
- Route `PUT` saves the stops in the order sent (`stopNo` 1, 2, 3…). Send a stop's `id` to keep it; a stop left out is
  removed. Pickup times must not go backwards along the route. Stop names are unique within a route.
- `monthlyFee` on a stop is what students boarding there pay.

## 6. Library

`GET/PUT /library/settings`, `GET/POST /library/categories`, `PUT/DELETE /library/categories/{id}` · read `LIBRARY_READ`, write `LIBRARY_CONFIGURE`

Settings: `studentMaxBooks` (2), `studentLoanDays` (14), `staffMaxBooks` (5), `staffLoanDays` (30), `maxRenewals` (1),
`finePerDay` (0), `fineGraceDays` (0), `maxFine` (none), `lostBookChargePercent` (100), `allowReservation` (true).

## 7. Communication

Read: `COMMUNICATION_READ` · write: `COMMUNICATION_CONFIGURE`

| Method | Path | What |
|---|---|---|
| GET / PUT | `/communication/settings` | Channels on/off, `senderName`, `replyToEmail`, `smsSenderId` (3–11 letters or digits), quiet hours, language |
| GET | `/communication/templates` | One template per event |
| PUT | `/communication/templates/{event}` | The school's wording, channels, on/off |
| POST | `/communication/templates/{event}/reset` | Back to the built-in wording |

- Events: `STUDENT_ABSENT`, `ATTENDANCE_SHORTFALL`, `FEE_DUE_REMINDER`, `FEE_OVERDUE`, `FEE_PAYMENT_RECEIVED`,
  `EXAM_SCHEDULE_PUBLISHED`, `RESULT_PUBLISHED`, `TIMETABLE_CHANGED`, `HOLIDAY_ANNOUNCED`, `TRANSPORT_DELAY`,
  `LIBRARY_BOOK_OVERDUE`, `GENERAL_NOTICE`.
- Channels: `IN_APP`, `EMAIL`, `SMS`, `WHATSAPP`. Default: in-app only.
- Every event has built-in wording (`customised: false`) on the in-app channel until the school saves its own.
- Wording uses `{{placeholders}}`. Each template lists the ones it may use; any other is refused with `UNKNOWN_PLACEHOLDER`.
  `{{school_name}}` and `{{today}}` work everywhere.
- `channelsOff` lists channels a template uses that are switched off in the settings.
- Quiet hours need both times and may cross midnight (21:00 → 07:00).

**Nothing is sent yet.** There is no delivery engine, SMS or WhatsApp provider in the backend; this stores what should
be sent and how.

## 8. Settings that have defaults

Fee settings, attendance rules, lending rules and communication settings always answer `GET`: with the defaults and
`saved: false` until the school saves them. `PUT` replaces the whole object.

## 9. Year setup checklist

`GET /api/v1/academic-years/{id}/setup-status` gains five optional steps (they do not count towards the percentage):
`FEES`, `ATTENDANCE_RULES`, `TRANSPORT`, `LIBRARY`, `COMMUNICATION`.

## 10. Permissions

| Permission | Feature | Owner / Admin | Principal | Accounts |
|---|---|---|---|---|
| `OPERATIONS_CONFIG_READ` | — | ✓ | ✓ | ✓ |
| `FEE_CONFIGURE` | FEES | ✓ | | ✓ |
| `ATTENDANCE_CONFIGURE` | ATTENDANCE | ✓ | ✓ | |
| `TRANSPORT_READ` | TRANSPORT | ✓ | ✓ | ✓ |
| `TRANSPORT_MANAGE` | TRANSPORT | ✓ | | |
| `LIBRARY_CONFIGURE` | LIBRARY | ✓ | | |
| `COMMUNICATION_READ` | — | ✓ | ✓ | |
| `COMMUNICATION_CONFIGURE` | — | ✓ | | |

Reads of fees, attendance and library use the existing `FEE_READ`, `ATTENDANCE_READ`, `LIBRARY_READ`.
Permissions with a feature disappear when the school's plan lacks it. Setup is kept apart from daily work:
`FEE_MANAGE` (collect) and `LIBRARY_MANAGE` (issue) are unchanged. Users must sign in again to pick up new permissions.

## 11. Database

Tenant `V25__operational_configuration.sql`: `fee_head`, `fee_settings` (one row per year), `fee_installment`,
`fee_structure`, `fee_structure_item`, `fee_concession_type`, `attendance_settings`, `transport_vehicle`,
`transport_route`, `transport_stop`, `library_settings`, `library_category`, `communication_settings`,
`notification_template` (one row per customised event). The three `*_settings` tables without a year hold a single row (`id = 1`).

Master `V36__operational_configuration_permissions.sql`: the eight permissions above and their role grants.
