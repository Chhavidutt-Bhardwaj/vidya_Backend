# Module Status Inventory

Classification, per the brief:
**COMPLETE**: workflow implemented *and* tested · **PARTIAL**: some workflow, incomplete · **PLACEHOLDER**: schema, DTOs or APIs without business logic · **MISSING**: nothing meaningful · **NEEDS REFACTORING**: works but has security, architecture or performance problems.

> Because no test exercises the real security chain, and every real-DB test is currently erroring (tenant V18), **no module qualifies as COMPLETE under the brief's definition**. The best rating given is "PARTIAL (strong)": logic implemented and unit-tested, but not verified end-to-end.

## Existing modules, as built

| Module | Package | Status | What exists | What's wrong or missing |
|---|---|---|---|---|
| Authentication | `security` | PARTIAL (strong) | Login, refresh rotation with reuse detection, logout, lockout, `/me` | S-01 backdoor, S-06 disabled-user tokens. No password reset, MFA or invitation flow. |
| Platform RBAC | `security` | NEEDS REFACTORING | 15-role enum → 154 static permissions | Not configurable. Most school roles can't enter tenants (S-09). Master endpoints unguarded (S-02). |
| Tenant resolution and isolation | `tenant` | PARTIAL (strong) | Header plus user-derived tenant, registry with Redis cache, membership check, fail-closed routing | Per-request master queries. Env-var credentials per tenant. **No migration of existing tenants** (DB-13). |
| Tenant provisioning | `tenant/provisioning`, `master/workflow` | NEEDS REFACTORING | Create DB/user, Flyway, init, provisioning state record, retry endpoint | **Broken by V18** (DB-1). Credentials need manual env setup. Unauthorized trigger (S-02). |
| School registry and branches | `master/school` | NEEDS REFACTORING | School CRUD, status, branches, address, contacts, board affiliations, documents metadata | No authorization. Branches don't propagate into tenant data. |
| School onboarding | `master/onboarding`, `master/workflow` | NEEDS REFACTORING | Multi-step state machine, activity log, journey, orchestration (payment → provision → activate) | No authorization. Orchestration isn't transactional or saga-based. Depends on the forged-payment path (S-03). |
| Subscription plans and entitlements | `master/subscription` | PARTIAL | Plans, tiers, features, modular catalogue, quotes, custom plans, school subscriptions, per-school overrides | **Entitlements are not enforced** on tenant APIs. No authorization. No renewal or expiry job. |
| Platform billing / payments | `master/payment` | PLACEHOLDER | Transaction model, gateway abstraction, webhook endpoints, refund model | Gateways are simulated (no provider calls). Verification can be bypassed (S-03). No reconciliation. |
| Community / field-agent network | `master/community` | NEEDS REFACTORING | Communities, members, configurable roles and permissions, personal/bank/payroll/commission details, commission ledger, login history | Raw entities in responses, plaintext PII (S-05), no authorization. This is sales/partner management, not school-group management. |
| Hubs and school relationship managers | `master/hub`, `master/school` | PARTIAL | Hub CRUD, RM assignment, escalation | No authorization |
| Support desk | `master/support` | PARTIAL | Queries, comments (internal/external), escalation log | No authorization. Service not transactional. |
| Platform analytics | `master/analytics` | PARTIAL | KPIs, revenue trend, onboarding funnel | Full-table loads. No authorization. |
| Platform audit | `master/audit`, `security/service/SecurityAuditService` | PARTIAL | Security event log (access denied, login) | No business events. Nothing tenant-side. |
| Academic year | `tenantdata/academic` | PARTIAL (strong) | CRUD, activate, close, set current (single-current enforced by service and a partial unique index, tenant V5) | No year rollover or promotion |
| Classes and sections | `tenantdata/academic` | PARTIAL (strong) | CRUD, capacity, class teacher | No campus dimension, streams or electives as first-class entities |
| Tenant users | `tenantdata/user` | PARTIAL | `tenant_user` plus profile CRUD | Not linked to logins in practice. Email mandatory (DB-5). |
| Student master | `tenantdata/student` | PARTIAL | CRUD, code generation, status state machine, profile | No medical, previous-school or document data. Full-unique admission number (DB-4). |
| Guardians | `tenantdata/student` | PARTIAL | Per-student guardian CRUD, primary and emergency flags | Not shared across siblings, no login (DB-2) |
| Admission | `tenantdata/student` | PARTIAL (thin) | Single `POST` that creates user, student and enrollment | No enquiry, application, documents, verification, approval or admission fee |
| Enrollment and section allocation | `tenantdata/student`, `tenantdata/academic` | NEEDS REFACTORING | Academic assignment plus enrollment plus subject enrollment | Two overlapping models (DB-6, API-5) |
| Staff | `tenantdata/staff` | PARTIAL (thin) | Staff CRUD, type, employment status, designation, department as strings | No contracts, qualifications, documents, salary or leave. No login link. |
| Subjects and curriculum | `tenantdata/subject` | PARTIAL (strong) | Subjects, curricula, curriculum-subject mapping, student subject enrollment | No learning outcomes or competencies |
| Teacher and class-teacher assignment | `tenantdata/assignment` | PARTIAL (strong) | Teacher↔subject↔section↔year, class-subject, single active class teacher with history | Not used for authorization (S-04) |
| Timetable | `tenantdata/timetable` | PARTIAL (strong) | Rooms, bell schedules, periods, timetables, entries, clash detection, teacher/room/section grids, day views, substitutions | Manual entry only (no generation). Real-DB tests currently not running. |
| Academic calendar | `tenantdata/calendar` | PARTIAL (strong) | Working-day config, holidays, working-day counts | Not integrated with events, exams or PTMs |
| Student attendance | `tenantdata/attendance` | PARTIAL (strong) | Daily mark, bulk upsert, status, history, summaries, holiday validation with override | Daily only (no period-wise). Teacher scope stubbed. No parent notification. No correction-approval workflow. |
| Examinations and marks | `tenantdata/examination` | PARTIAL (strong) | Grading scales and tiers, exam terms, schedules, subject paper config, bulk marks, absent/exempt, grade from scale, lock | No weightage aggregation across terms, result approval, controlled unlock/correction or bulk upload |
| Report cards | `tenantdata/reportcard` | PLACEHOLDER | Tables (broken migration), entities, enums, DTOs, mapper, repositories, service *interface* | No service implementation, no controller, no PDF |
| Web pages | `web` | PARTIAL | Landing page, `/schools/{slug}` portal page, SEO files | Out of scope for a backend. Consider moving to the frontend. |

## Target product modules (brief Phase 3)

| # | Module | Status | Notes |
|---|---|---|---|
| A | Platform and tenant management | PARTIAL / NEEDS REFACTORING | Tenancy, onboarding and plans exist. No organization or school-group entity, no campus, no branding, entitlements unenforced. |
| B | School setup | PARTIAL | Profile, board affiliation, academic year, calendar, working days, holidays, rooms, classes, sections, subjects and grading exist. Missing: school timings as config (bell schedules partly cover this), buildings, streams and electives model, per-school settings store (timezone is global). |
| C | Student admission and management | PARTIAL (thin) | Registration and status only. Missing: enquiry → application → verification → approval pipeline, documents, ID cards, TC, withdrawal, **promotion**, alumni. |
| D | Academic management | PARTIAL | Curriculum, assignments, timetable and substitutions exist. Missing: lesson plans, homework, assignments, projects, outcomes, timetable generation. |
| E | Attendance | PARTIAL | Student daily only. Missing: period-wise, staff and teacher attendance, leave applications and approval, late/early, notifications, biometric readiness. |
| F | Examination and results | PARTIAL | Marks and grades exist. Missing: weightage, result approval and locking workflow, correction audit, report cards, PDF, QR verification, analytics. |
| G | Fees and finance | **MISSING** | Only platform billing exists. Nothing for student fees. |
| H | Staff and HR | PARTIAL (thin) | Staff record only. Payroll, leave, contracts and documents are **missing**. |
| I | Transport | **MISSING** | |
| J | Library | **MISSING** | |
| K | Inventory and assets | **MISSING** | |
| L | Communication | **MISSING** | No notices, templates, SMS, email or push. The mail starter is unused. |
| M | Safety and wellbeing | **MISSING** | Only one emergency contact on the user profile |
| N | Documents and certificates | **MISSING** | No file storage at all |
| O | Reports and analytics | PARTIAL (platform only) | Platform KPIs only. Attendance summaries exist per student. No school dashboards. |
| P | Compliance (UDISE+, board) | **MISSING** | Board affiliation metadata only |
| — | Parent / student access | **MISSING** | Roles exist, but they're denied tenant access and have no data linkage |
| — | AI features | **MISSING** | Correctly deferred until the core is stable |
