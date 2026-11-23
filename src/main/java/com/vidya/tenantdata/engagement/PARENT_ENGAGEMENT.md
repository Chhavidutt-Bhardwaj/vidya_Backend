# Parent engagement — Student Daily Activity Report

Every school day each family gets one summary per child: attendance, homework set today and due tomorrow, lessons
delivered, class activities, announcements and teacher remarks. A parent with several children gets **one** message
per channel covering all of them. The same report is always available in the app.

```
 teachers / admin write                       read from existing modules
 ─────────────────────                        ──────────────────────────
 homework            (/api/v1/homework)        student_attendance
 class_activity      (/api/v1/class-activities)lesson_plan (DELIVERED that day)
 announcement        (/api/v1/announcements)   calendar (working day / holiday)
 student_daily_remark(/api/v1/student-remarks) student_guardian (contacts, parent login)
            │                                            │
            └──────────────► DailyActivityReportService ◄┘   GET /api/v1/parent/daily-report
                                       │                     GET /api/v1/daily-reports/students/{id}
         EngagementScheduler (every 5 min, per tenant)
                                       ▼
          DailyReportGenerator ── claim day (daily_report_run) ── compose per guardian ──► notification_outbox
                                                                                           │
          EngagementScheduler (every 1 min) ── NotificationOutboxService.dispatchDue ──────┘
                                       ├─ IN_APP   → inbox  (GET /api/v1/notifications)
                                       ├─ EMAIL    → Spring Mail (spring.mail.* + vidya.notifications.email.from)
                                       └─ SMS / WHATSAPP → plug in a NotificationSender bean (SKIPPED until then)
```

## 1. Setting it up (school admin)

1. Channels and quiet hours: `PUT /api/v1/operations-config/communication/settings` (existing).
2. Wording and channels of the message: event **`DAILY_ACTIVITY_REPORT`** in the communication templates
   (placeholders `{{date}}`, `{{children}}`, `{{summary}}`, `{{school_name}}`). Default channel: IN_APP.
3. `PUT /api/v1/daily-reports/settings` — `enabled`, `sendTime` (school-local, default 18:00), `skipNonWorkingDays`,
   and `include*` toggles for each part of the report.
4. Guardians reach the report through their **parent login** (`student_guardian.guardian_user_id`, see tenant V19)
   for in-app, and through `email` / `phone` on the guardian row for email / SMS / WhatsApp.

## 2. Daily work (teachers)

| What | API | Who |
|---|---|---|
| Homework | `POST /api/v1/homework` `{academicYearId, academicClassId, sectionId?, subjectId, title, description?, assignedOn?, dueOn}` | Subject teacher of that class/section (STUDENT scope ALL: any teacher) |
| Class activity | `POST /api/v1/class-activities` `{…, activityDate, activityType, title}` | Anyone teaching the section / class |
| Remarks | `POST /api/v1/student-remarks` `{remarkDate?, remarks:[{studentId, category, sentiment, remark, visibleToGuardians}]}` | Students the teacher teaches; all-or-nothing |
| Announcement | `POST /api/v1/announcements` `{title, body, audience: SCHOOL\|CLASS\|SECTION, priority, publish}` | SCHOOL needs STUDENT scope ALL |
| Preview | `GET /api/v1/daily-reports/sections/{sectionId}?date=` | Teacher of the section |

`visibleToGuardians: false` makes a remark staff-only: it never reaches the parent API, the student or any message.

**Announcements** notify the guardians of every student enrolled in the audience when published (event
`GENERAL_NOTICE`). `priority: EMERGENCY` uses event **`EMERGENCY_ALERT`**, is delivered first and ignores quiet hours.
Give that template SMS / WhatsApp so it reaches parents who aren't in the app.

## 3. Families

| API | Returns |
|---|---|
| `GET /api/v1/parent/children` | Linked children with current class / section |
| `GET /api/v1/parent/daily-report?date=` | Report for every child (default today) |
| `GET /api/v1/parent/children/{id}/daily-report?date=` | One child |
| `GET /api/v1/parent/children/{id}/homework?from=&to=` | Homework due in the window (default: next two weeks) |
| `GET /api/v1/parent/children/{id}/remarks` | Remarks visible to guardians |
| `GET /api/v1/parent/announcements?from=&to=` | School + children's class/section, unexpired, emergencies first |
| `GET /api/v1/notifications`, `POST /{id}/read`, `POST /read-all` | In-app inbox with unread count |

Row access uses `STUDENT_SCOPE_OWN`: a parent reaches only students whose guardian row is linked to their login.

## 4. Sending and delivery

* **Once per day per school.** `daily_report_run.report_date` is unique; the run is claimed with
  `INSERT … ON CONFLICT DO NOTHING`, so several servers can run the scheduler.
* Students with nothing to report (attendance not marked and no content) are not messaged.
* `POST /api/v1/daily-reports/runs {date?}` sends a day by hand (also on holidays or with auto-send off).
  Outbox keys (`DAILY_REPORT:<date>:<channel>:<address>`) prevent sending the same family twice.
* `GET /api/v1/daily-reports/runs` shows each day: COMPLETED / SKIPPED (reason) / FAILED (error), students, recipients, messages.
* Outbox: SMS / WhatsApp wait for quiet hours to end (except EMERGENCY). Failures are retried after 1, 5, 15 and 60 minutes,
  then FAILED. A channel that is switched off, or has no provider on the server, is SKIPPED with the reason.
  `GET /api/v1/daily-reports/outbox` lists recent messages for troubleshooting.

### Adding SMS / WhatsApp / push

Implement `NotificationSender` as a Spring bean for the channel (e.g. an SMS gateway or the WhatsApp Business API).
Throw `ChannelUnavailableException` when the provider is not configured (SKIPPED, no retry), and any other exception
for a temporary failure (retried). Push to mobile apps needs device-token registration and a `PUSH` channel. That is
not built yet; until then apps can poll `GET /api/v1/notifications`.

## 5. Configuration

```yaml
vidya:
  engagement:
    scheduler.enabled: true        # VIDYA_ENGAGEMENT_SCHEDULER
    dispatch-interval: PT1M
    dispatch-batch-size: 200
    daily-report-cron: "0 */5 * * * *"   # how often schools are checked against their send time
  notifications.email.from: ""     # VIDYA_NOTIFICATION_EMAIL_FROM (falls back to spring.mail.username)
```

## 6. Permissions (master V37)

`HOMEWORK_READ/_MANAGE`, `CLASS_ACTIVITY_READ/_MANAGE`, `STUDENT_REMARK_READ/_MANAGE`, `ANNOUNCEMENT_READ/_MANAGE`,
`DAILY_REPORT_READ/_MANAGE`, `NOTIFICATION_INBOX_READ`. Parents and students get the read permissions; teachers manage;
`DAILY_REPORT_MANAGE` is for school owner / admin / principal.

## 7. Known limits

* Homework is shown for the whole class / section; elective-subject enrollments are not filtered yet.
* A whole-class lesson plan marked delivered appears for every section of the class.
* `{{school_name}}` is the communication settings' *sender name* (the tenant DB doesn't hold the school's name).
