# School Onboarding: Implementation Plan

Builds on the [existing implementation](SCHOOL_ONBOARDING_EXISTING_IMPLEMENTATION.md) and closes the [gaps](SCHOOL_ONBOARDING_GAP_ANALYSIS.md). **Status: proposed, awaiting approval.** No source code has been changed.

## 1. Design principles

1. **One onboarding aggregate.** `SchoolOnboarding` stays the root. No second module, and no new "onboarding v2" tables for things that already exist.
2. **Two layers, one record.**
   - *Lifecycle* (existing `OnboardingStatus`): commercial and provisioning milestones, kept as is, plus two new states (below).
   - *Implementation* (new task engine): the 20 journey stages expressed as **task groups** of configurable tasks, which run in parallel where dependencies allow.
3. **Configure through existing engines.** Tasks never write academic data themselves. Academic years, classes, sections, subjects, holidays, bell schedules and rooms are created through the existing tenant services and APIs, and a task's *validator* checks that the configuration exists and is valid. No duplicate academic-year or calendar engine.
4. **Never span databases in one transaction.** Task, approval and audit records live in the **master** DB. Tenant state is read by readiness checks under `TenantContext`, in read-only tenant transactions.
5. **Server decides identity.** Actor, school, tenant and organization come from the authenticated principal and the stored onboarding, never from request bodies.
6. **Data-driven rules.** Task templates, weights, dependencies, applicability, approval policies and board templates are versioned data, not `if` statements.

## 2. Target lifecycle

```
DRAFT → SUBMITTED → VERIFIED → PLAN_SELECTED → PAYMENT_PENDING → PAYMENT_COMPLETED ─┐
                                      (or PAYMENT_WAIVED, by approval policy) ────────┤
                                                                         APPROVED ◄───┘
APPROVED → PROVISIONING → PROVISIONED → IMPLEMENTATION ⟲ (task engine) → GO_LIVE_APPROVED → ACTIVE → (hypercare tasks)
REJECTED / CANCELLED reachable only from pre-ACTIVE states. PROVISIONING_FAILED → retry.
```

- **New statuses:** `IMPLEMENTATION` and `GO_LIVE_APPROVED`. Optionally `PAYMENT_WAIVED`, pending decision D-9. Each needs a migration to extend the CHECK constraint.
- **Transition table:** a `VALID_TRANSITIONS` map on `OnboardingStatus` (the same pattern as `AcademicYearStatus`) replaces the scattered `if` checks and fixes OB-4, OB-5 and OB-10.
- **Activation:** `ACTIVE` is reachable only from `GO_LIVE_APPROVED`. That state requires a readiness evaluation with zero mandatory blockers plus a go-live approval.

## 3. Data model additions (master DB)

| Table | Purpose | Key columns |
|---|---|---|
| `organization` | Trust, society, company, group or single school | `code`, `legal_name`, `display_name`, `org_type`, `registration_no`, `pan` (encrypted), `gstin`, `owner_user_id`, `branding` JSONB, `status`, `version` |
| `school` (alter) | Link and profile gaps | `+organization_id`, `udise_code`, `category`, `establishment_year`, `logo_file_id`, `timezone` (default `Asia/Kolkata`), `locale`, `operating_hours` JSONB |
| `school_branch` (alter) | Campus | `+campus_code`, `+is_campus`. Final shape depends on decision D-2. |
| `onboarding_task_template` | Versioned catalogue | `template_version`, `stage`, `task_code`, `name`, `description`, `mandatory`, `weight`, `responsible_permission`, `depends_on` text[], `applicability` JSONB, `validator_key`, `approval_type` |
| `onboarding_task` | Instance per onboarding | `onboarding_id`, `task_code`, `stage`, `status` (NOT_STARTED / IN_PROGRESS / BLOCKED / PENDING_APPROVAL / COMPLETED / REJECTED / NOT_APPLICABLE / WAIVED), `assigned_user_id`, `assigned_permission`, `started_at`, `completed_at`, `due_date`, `blocker_reason`, `last_validation` JSONB, `version` · unique `(onboarding_id, task_code)` |
| `approval_policy` | Maker-checker rules | `approval_type`, `required_permission`, `min_approvals`, `maker_cannot_approve`, `version` |
| `onboarding_approval` | Decisions | `onboarding_id`, `task_id`, `approval_type`, `decision`, `decided_by`, `reason`, `decided_at` |
| `onboarding_activity` (alter) | Audit | `+actor_user_id` (from `SecurityContext`), `+task_id`, `+reason`. The existing `performed_by` is kept for compatibility. |
| `user_invitation` | Secure invites | `token_hash`, `email`, `school_id`, `organization_id`, `role_code`, `scope_type`, `scope_id`, `expires_at`, `status`, `invited_by`, `accepted_at`, `revoked_by` |
| `board_template`, `board_template_version` | Board rule packs (P2) | `board_code`, `version`, `payload` JSONB (subjects, grading, assessment, promotion, attendance policy), `status` |
| `school_board_config` | Applied snapshot (P2) | `school_id`, `template_version_id`, `overrides` JSONB, `applied_at`, `applied_by` |
| `migration_batch`, `migration_row` | Data migration (I6) | batch status pipeline, file ref, mapping JSONB, row raw/mapped/errors/duplicate_of/result, idempotency hash |
| `school_integration` | Integrations (I7) | `provider_type`, `provider`, `config` JSONB, `secret` (encrypted), `status`, `last_tested_at`, `last_test_result` |

There's no tenant-DB change for the task engine. Tenant changes appear only in I5 (applying board templates through existing services) and I6 (import staging).

## 4. Task catalogue (template v1, abbreviated)

| Stage | Example tasks (✱ = mandatory) | Validator (reads) | Approval |
|---|---|---|---|
| Registration | ✱`LEAD_CAPTURED`, ✱`DUPLICATE_CHECK_CLEARED` | master: duplicate rules | — |
| Verification | ✱`DOCS_VERIFIED`, ✱`AFFILIATION_VERIFIED` | `school_document`, `school_board_affiliation` | `PLATFORM_VERIFICATION` |
| Commercial | ✱`PLAN_SELECTED`, ✱`PAYMENT_SETTLED_OR_WAIVED` | subscription, payment | `COMMERCIAL` |
| Provisioning | ✱`TENANT_PROVISIONED` | `tenant_provisioning.completed` | — |
| Organization | `ORG_PROFILE_COMPLETE` (✱ if multi-school) | `organization` | `ORGANIZATION` |
| School | ✱`SCHOOL_PROFILE_COMPLETE`, ✱`PRIMARY_CONTACTS_SET` | school + sub-tables | `SCHOOL_CONFIG` |
| Campus | `CAMPUSES_CONFIGURED` | `school_branch` | — |
| Board | ✱`BOARD_SELECTED`, `BOARD_TEMPLATE_APPLIED` | affiliation, board config | `ACADEMIC` |
| Academic year | ✱`ACADEMIC_YEAR_CREATED_CURRENT`, ✱`WORKING_DAYS_CONFIGURED`, `HOLIDAYS_LOADED` | tenant: `AcademicYear`, `CalendarService` | — |
| Structure | ✱`CLASSES_SECTIONS_CREATED`, ✱`SUBJECTS_MAPPED`, `ROOMS_CONFIGURED` | tenant services | `ACADEMIC` |
| Users | ✱`SCHOOL_ADMIN_ACTIVATED`, `STAFF_INVITED` | invitations, users | — |
| Fees | `FEES_CONFIGURED` (applicability: module `FEES`; NOT_APPLICABLE until the fees module exists) | — | `FEE` |
| Attendance / timetable | `BELL_SCHEDULE_CONFIGURED`, `TIMETABLE_DRAFTED` | tenant bell schedule, timetable | — |
| Integrations | `INTEGRATIONS_TESTED` (applicability per subscribed module) | `school_integration` | — |
| Data migration | `STUDENTS_IMPORTED`, `STAFF_IMPORTED` (reconciled) | `migration_batch` | `MIGRATION` |
| Training | `ADMIN_TRAINING_DONE` (evidence) | manual | — |
| UAT | ✱`UAT_SIGNED_OFF` | manual | `UAT` |
| Go-live | ✱`BACKUP_VERIFIED`, ✱`GO_LIVE_APPROVED` | readiness = 0 blockers | `GO_LIVE` |
| Hypercare | `HYPERCARE_30D` | post-activation | — |

**Progress** = Σ weight(completed or waived) ÷ Σ weight(applicable), with mandatory tasks reported separately. `NOT_APPLICABLE` tasks are excluded from both numerator and denominator.

## 5. API additions (all under `/api/v1/onboarding/{id}`, permission-guarded)

| Method | Path | Purpose |
|---|---|---|
| GET | `/tasks` (filters: stage, status, assignedToMe) | Wizard / task board |
| PATCH | `/tasks/{taskCode}` | Assign, start, set blocker, add notes (`If-Match` version) |
| POST | `/tasks/{taskCode}/complete` | Runs the validator. 422 with validation details on failure. |
| POST | `/tasks/{taskCode}/waive` | Optional tasks only. Requires a reason and permission. |
| POST | `/approvals` · GET `/approvals` | Request or record a decision. Enforces policy and separation of duties. |
| GET | `/readiness` | Overall %, mandatory blockers, optional pending, failed validations, next actions |
| POST | `/go-live` | Transition to `GO_LIVE_APPROVED` when readiness is clean and the approval is granted |
| POST | `/activate` | **Replaces** the orchestration endpoint's behaviour (same path) with the gated version |
| POST | `/invitations` · DELETE `/invitations/{id}` · POST `/api/v1/invitations/accept` (public, token) | Users |
| GET | `/api/v1/onboarding/approvals/queue` | Approver dashboard |
| CRUD | `/api/v1/organizations` | Organization |

Existing endpoints remain. Changed behaviour is documented in an API changelog:
- **`/approve`, `/verify`, `/reject`:** also write approval records, and enforce authorization and the state machine.
- **`/admin`:** role is limited to `SCHOOL_OWNER` / `SCHOOL_ADMIN`. Existing users are never re-linked silently. Responses carry an invitation instead of a password.
- **`/activate`:** gated by readiness.
- **`/provision`:** ignores client-supplied host and port.

## 6. Increments

Each increment is one or more mergeable PRs, ends with `mvn verify` green, and includes real-DB and authorization tests.

| # | Increment | Contents | Depends on | Est. |
|---|---|---|---|---|
| **I0** | **Onboarding security and integrity fixes** (no architecture change) | Permissions `ONBOARDING_{VIEW,EDIT,VERIFY,APPROVE,PROVISION,ACTIVATE}` and `SCHOOL_PROFILE_*`, mapped in `PlatformRole`. `@PreAuthorize` plus `canAccessOnboarding` / `canAccessSchool` on all 18 + 19 endpoints. OB-1 (role whitelist, no silent relink or reactivation). OB-2 (`/activate` routed through the gated workflow; manual payment confirmation needs a separate platform permission). OB-6 (actor from `SecurityContext`). OB-4, OB-5, OB-10 (`VALID_TRANSITIONS`). OB-8 (host and port from server config). OB-12 (explicit board mapping). OB-13 (new master migration fixing the CHECK). Tenant V18 fix. A `@SpringBootTest` + Testcontainers onboarding test that provisions a real tenant DB. | — | 4–6 days |
| **I1** | Task engine, approvals, audit | Tables from §3 (tasks, templates, policies, approvals, activity columns). Template v1 seed. `OnboardingTaskService`, `TaskValidator` registry, `ApprovalService` (maker-checker). Task and approval APIs. Instantiate tasks at `PROVISIONED` (back-fill for existing onboardings). | I0 | 7–9 days |
| **I2** | Organization, school profile, campus | `organization` CRUD, `school.organization_id` (nullable, back-filled to one org per existing school), profile fields, campus per D-2 | I0, **D-2** | 4–6 days |
| **I3** | Readiness and go-live | `ReadinessService` + `ReadinessCheck` beans (master and tenant read-only), `IMPLEMENTATION` / `GO_LIVE_APPROVED` states, gated activation, readiness API | I1 | 4–5 days |
| **I4** | Invitations and scoped roles | `user_invitation`, token flow, `NotificationSender` (mail plus dev log), revocation, audit. Scoped role assignment per **D-6**. Fixes tenant denial of PRINCIPAL, ACCOUNTS, etc. | I1, **D-6**, **D-10** | 8–12 days |
| **I5** | Academic setup tasks and minimal board packs | Validators for academic year, working days, classes and sections, subjects, bell schedule, rooms using existing tenant services. Bulk "create classes and sections from template" helper calling existing services. Minimal CBSE and State board template packs (grading scale plus subject list). | I1, I3 | 6–8 days |
| **I6** | Data migration framework | Batch pipeline for classes, sections, staff, students and guardians. File storage abstraction (local and S3). Async job runner. Duplicate detection. Two-phase import through existing services. Reconciliation report. | I1, file storage, scheduler (audit G-6, G-7) | 12–15 days |
| **I7** | Integration registry | Encrypted credentials (D-4), test-connection contract, email provider first | I1, **D-4** | 6–8 days |
| I8 | Fee configuration tasks | After the fees module exists | fees module | — |
| I9 | Hypercare monitoring | Post-activation health checks | I3 | 3–4 days |

**I0–I3 make onboarding safe, accountable and gated. That's about 4–5 weeks of work and the minimum for a real school to go live.**

## 7. Testing strategy (per increment)

- **Unit:** state transitions (every allowed and forbidden edge), task DAG (cycles rejected, parallel tasks start independently), progress maths (weights, NOT_APPLICABLE, waived), approval policy (self-approval blocked), readiness aggregation.
- **Real infrastructure** (`@SpringBootTest` + Postgres + Redis Testcontainers):
  - Full journey to ACTIVE with a real tenant DB.
  - Provisioning failure then retry, with no duplicate tenant or DB.
  - Concurrent task updates give 409.
  - Two onboardings for two schools: a school-A admin gets 403 on every school-B onboarding endpoint.
  - A community agent sees only their own leads.
  - A role × endpoint matrix for all onboarding endpoints.
- **Regression:** the full existing suite stays green. Existing Mockito tests are updated where behaviour intentionally changes (documented in each PR).

## 8. Decisions needed before the dependent increments

| # | Decision | Needed for | Recommendation |
|---|---|---|---|
| **D-1** | Approve I0 as scoped | I0 | Approve. It's security-critical and has no design risk. |
| **D-2** | Campus model: (A) campus = master `school_branch` only, with tenant data unchanged; (B) add `campus_id` to tenant operational tables; (C) one tenant per campus | I2, and later fees and attendance | **A now, B later.** Model campuses in master and the onboarding tasks, and defer the tenant `campus_id` until a real multi-campus customer exists. Avoid C, which breaks cross-campus reporting. |
| **D-6** | Scoped roles: (A) role assignments in master (`platform_user` gets a `user_role_assignment` with scope); (B) tenant-side RBAC tables | I4, platform G-2 | **A.** Identity is already in master, the tenant filter already consults master, and it covers organization-level scope, which tenant DBs can't see. |
| **D-7** | Academic-year lifecycle: keep `DRAFT/ACTIVE/CLOSED`, or add the seven-state lifecycle from the brief | I5 | **Keep.** Express "configuration / ready" as onboarding tasks. Revisit at the year-rollover feature. |
| **D-8** | Orchestration `/activate` one-shot endpoint | I0 | Keep the path. Replace its behaviour with the gated workflow (no bypass). Remove the old service once clients migrate. |
| **D-9** | When may approval skip payment (trial, offline contract, partner-sponsored)? | I0, I1 | Explicit `PAYMENT_WAIVED` with an approval of type `COMMERCIAL`, recorded with a reason |
| **D-10** | Email provider for invitations (SMTP / SES / other) | I4 | SMTP through the existing mail starter first, behind an interface |
| **D-4** | Secret encryption key management | I7, PAN in org | Env-provided AES key now, KMS later |

## 9. Explicitly not done in this phase

Fee configuration (no fees domain yet) · biometric, GPS, WhatsApp and push adapters · government portal integrations · frontend screens (separate repo; this plan delivers the APIs they need) · a seven-state academic-year lifecycle (D-7).
