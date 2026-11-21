# School Onboarding: Existing Implementation Assessment

Revision `9c6d509` · 2026-11-07 · No source code was changed for this assessment.
Companion documents: [Gap analysis](SCHOOL_ONBOARDING_GAP_ANALYSIS.md) · [Implementation plan](SCHOOL_ONBOARDING_IMPLEMENTATION_PLAN.md) · platform-wide audit in [`docs/audit/`](../audit/PROJECT_OVERVIEW.md).

## 1. Summary

Onboarding today is a **commercial and provisioning pipeline** that runs:

> lead → verify → plan → pay → approve → create tenant DB → create admin → activate

It lives entirely in the master DB, and it ends at the moment the school's (empty) tenant database exists. Nothing in it configures the school *inside* that database: no academic year, structure, users, fees or calendar. Nothing models tasks, owners, approvals, readiness or go-live.

About 4,600 lines of onboarding code exist. Roughly a third of it is reusable as-is, a third needs fixing, and a third is duplicated across two competing pipelines.

**It must not be exposed in its current form.** Every onboarding endpoint requires only a login, and four defects allow privilege escalation, approval bypass or payment bypass (§5).

## 2. Code inventory

| Layer | Component | Location | LOC |
|---|---|---|---|
| Entity | `SchoolOnboarding` (status, current step, `completed_steps text[]`, rejection reason, timestamps, `@Version` via `AuditableEntity`) | `master/onboarding/entity` | 119 |
| Entity | `OnboardingStepData` (one JSONB blob per `OnboardingStep`, `is_completed`) | same | 84 |
| Entity | `OnboardingActivity` (action, prev/new status, performer, JSONB metadata) | same | 87 |
| Enums | `OnboardingStatus` (16), `OnboardingStep` (13), `OnboardingSource` (5), `PerformerType` | same | |
| Service | `OnboardingServiceImpl`: initiate, save step, submit, verify, approve, reject, list | `master/onboarding/service` | 444 |
| Service | `SchoolOnboardingWorkflowServiceImpl`: select plan, initiate payment, payment event listener, approve, provision, create admin, activate, retry, progress | `master/workflow/service` | 781 |
| Service | `OnboardingOrchestrationServiceImpl`: one-shot "subscribe + pay + provision + activate" | `master/onboarding/orchestration` | 197 |
| Service | `SchoolAdminProvisioningServiceImpl`: initial admin user | `master/workflow/admin` | 177 |
| Engine | `TenantProvisioningService`, `TenantDatabaseCreator`, `TenantMigrationService`, `TenantInitializationService`: step-tracked, resumable DB creation and migration | `tenant/provisioning` | 723 |
| Entity | `Tenant`, `TenantProvisioning` (step, attempt count, last completed step, failure reason) | `master/tenant`, `master/provisioning` | 300 |
| School profile | `School`, `SchoolBranch`, `SchoolAddress`, `SchoolContactDetail`, `SchoolBoardAffiliation`, `SchoolDocument` + `SchoolDetailController` (19 endpoints incl. `complete-profile`) | `master/school` | 3.6k |
| Commercial | `SchoolSubscription`, `SubscriptionEntitlementService` (`isModuleEnabled`, `isFeatureEnabled`, overrides), `PaymentService` | `master/subscription`, `master/payment` | |
| RM / hub | `SchoolRelationshipController`: assign RM, auto-assign nearest hub | `master/school`, `master/hub` | |
| Tables | `school_onboarding`, `onboarding_step_data`, `onboarding_activity` (V8–V10, V19, V26), `tenant`, `tenant_provisioning` (V6–V7) | master DB | |
| Tests | 80 tests across 12 classes, **all Mockito / standalone MockMvc**. `SchoolOnboardingPipelineIntegrationTest` has 1 test and uses mocks. No test provisions a real tenant DB (it would fail at tenant V18). | `src/test/.../master/{onboarding,workflow,school}`, `tenant/provisioning` | |
| Frontend | **No frontend in this repository** and none on this machine. Contract notes: `docs/frontend-api/15_school_onboarding_and_sub_details_lifecycle.md`, `16_…entitlements.md`. These describe an `UNDER_REVIEW` state and a "post-approval setup token" that **do not exist in code**. | | |

### API surface (18 endpoints, all "authenticated only")

| Controller | Endpoints |
|---|---|
| `OnboardingController` `/api/v1/onboarding` | `POST /` initiate · `GET /{id}` · `GET /code/{code}` · `GET /{id}/detail` · `PUT /{id}/steps` · `POST /{id}/submit` · `POST /{id}/verify` · `POST /{id}/approve` · `POST /{id}/reject` · `GET /` (paged, filters) |
| `SchoolOnboardingWorkflowController` `/api/v1/onboarding/{id}` | `POST /select-plan` · `POST /payment/initiate` · `POST /provision` · `POST /admin` · `POST /retry` · `GET /workflow-status` |
| `OnboardingOrchestrationController` | `POST /api/v1/onboarding/{id}/activate` |
| `SchoolDetailController` `/api/v1/schools/{id}` | contacts, addresses, boards, documents (verify/reject), `complete-profile` (19 total) |

## 3. State machine as actually implemented

```
initiate ──► DRAFT ──submit──► SUBMITTED ──verify(true)──► VERIFIED
  (COMMUNITY/REFERRAL/WEBSITE start at SUBMITTED)     verify(false) ► VERIFICATION_PENDING ─► (verify again)
VERIFIED ─select-plan─► PLAN_SELECTED ─payment/initiate─► PAYMENT_PENDING ─event─► PAYMENT_COMPLETED
{VERIFIED | PLAN_SELECTED | PAYMENT_COMPLETED | APPROVAL_PENDING} ─approve─► APPROVED      ◄─ payment is optional
APPROVED ─provision─► PROVISIONING ─► PROVISIONED  |  PROVISIONING_FAILED ─retry/provision─► …
(any non-ACTIVE with a tenant row) ─activate─► ACTIVE                                       ◄─ no status precondition
(any status, including ACTIVE) ─reject─► REJECTED                                           ◄─ no precondition

Parallel path: (any except ACTIVE/REJECTED/CANCELLED/FAILED, incl. DRAFT) ─/activate (orchestration)─► ACTIVE
```

Unused enum values: `APPROVAL_PENDING` (never set), `CANCELLED` (no endpoint), `FAILED` (never set). Steps `BASIC_INFORMATION … DOCUMENTS` are advanced by the ordinal order of the enum, with no validation of their content.

## 4. Capability classification

Legend: **REUSE** as is · **EXTEND** add to it · **ADD** new · **FIX** defect to correct · **DEFER** later phase

| Capability (brief §) | What exists | Class | Notes |
|---|---|---|---|
| Lead / registration (§2) | `initiateOnboarding`, sources, community attribution | **EXTEND + FIX** | Add duplicate detection (email, phone, name+city, affiliation no.). Fix board mapping. |
| Verification (§2) | `verifyOnboarding` (boolean + notes) | **EXTEND** | Becomes an approval of type `PLATFORM_VERIFICATION` with checklist and documents |
| Commercial approval (§2) | plan selection, subscription snapshot, payment, two `approve` methods | **REUSE** (snapshot, subscription) / **FIX** (approve paths, payment bypass) | Collapse to one approve. Make payment optional only via an explicit policy (e.g. trial or offline contract). |
| Tenant provisioning (§2, §16) | `TenantProvisioningService` step engine, retry | **REUSE** engine / **FIX** wrapper | Engine is sound. Fix shared DB role, client-chosen host, missing compensation, and the V18 migration. |
| Organization (§3) | none. `community` is a sales-partner network, `School.legalName` / `registrationNumber` only | **ADD** | New `organization` aggregate in master. School gets `organization_id`. |
| School profile (§4) | `School` + address/contact/board/document sub-tables, `complete-profile` | **REUSE + EXTEND** | Add UDISE, category, establishment year, logo ref, timezone, locale, operating hours. |
| Campus (§4) | `school_branch` (master only, address fields, main flag) | **EXTEND** | Promote to campus. Whether tenant data carries `campus_id` is decision D-2. |
| Board configuration (§5) | `SchoolBoard` / `BoardAffiliationType` enums (mismatched), `grading_scale` in tenant | **ADD** (versioned board templates) / **FIX** (enum mismatch) / **REUSE** (grading scales as apply target) | Templates are data, not code |
| Academic year (§6) | Tenant `AcademicYear` (DRAFT/ACTIVE/CLOSED/CANCELLED/DELETED, single-current DB-enforced), `CalendarService`, `HolidayService`, `WorkingDayConfig` | **REUSE** | The PLANNING → … → ARCHIVED lifecycle named in the brief **does not exist** (see note below) |
| Academic structure (§7) | Tenant classes, sections (capacity), subjects, curricula, class-subject, teacher-subject, rooms | **REUSE** | Streams, subject groups, departments, houses missing (**ADD**, P2) |
| Users and invitations (§8) | `provisionInitialAdmin` only (password in response) | **FIX + ADD** | Invitation tokens, activation, revocation. Depends on tenant RBAC. |
| RBAC (§8) | `PlatformRole` enum. Community role system (configurable). | **EXTEND** (decision D-6) | Most school roles are denied tenant access today |
| Fee configuration (§9) | none (fees module missing entirely) | **DEFER** | Onboarding task registered as "not applicable" until the fees module exists |
| Attendance / timetable config (§10) | Tenant bell schedules, periods, rooms, working days. Attendance is daily only. | **REUSE** for timings and periods / **DEFER** policies | |
| Integrations (§11) | Payment gateway properties (global, not per school) | **ADD** (registry + encrypted credentials) / **DEFER** providers | |
| Data migration (§12) | none | **ADD** (framework) / **DEFER** (fees, opening balances) | Needs file storage |
| Task engine (§13) | `OnboardingStep` enum + JSONB step data + `completed_steps` | **ADD** | Step data is kept as draft storage for the wizard |
| Approval workflow (§14) | single-actor verify/approve, actor not recorded | **ADD** | Maker-checker, separation of duties |
| Readiness engine (§15) | `getWorkflowProgress` (status snapshot) | **ADD** (reuse progress DTO data) | |
| Go-live and activation (§15) | `activateSchool`, orchestration `/activate` | **FIX** | Gate on readiness. Remove the orchestration bypass. |
| Post-go-live monitoring (§2) | none | **DEFER** | |
| Audit (§16) | `onboarding_activity` (actor always null), `audit_log` (security events) | **REUSE table + FIX** | Record the authenticated actor, never a client-supplied one |
| Entitlement-driven conditional steps (§17) | `SubscriptionEntitlementService.isModuleEnabled` (unused) | **REUSE** | |
| Wizard / dashboards (§17) | no frontend here | **DEFER to frontend repo** | Backend provides the APIs and contracts |

**Note on the academic-year lifecycle.** The brief says to "preserve the existing lifecycle PLANNING → CONFIGURATION → READY → ACTIVE → CLOSING → CLOSED → ARCHIVED". The implemented lifecycle is `DRAFT → ACTIVE → CLOSED` (plus `CANCELLED`, `DELETED`), enforced by `AcademicYearStatus.VALID_TRANSITIONS` and a DB CHECK (tenant V5). Introducing the seven-state lifecycle would be a breaking change to an existing entity and API, not a preservation. The plan proposes keeping it unchanged and expressing "configuration / ready" as onboarding-task state instead (decision D-7).

## 5. Defects found

| ID | Sev | Defect | Location |
|---|---|---|---|
| OB-1 | **Critical** | **Privilege escalation through initial admin creation.** `POST /onboarding/{id}/admin` (login-only) accepts any `PlatformRole` in the body, including `PLATFORM_SUPER_ADMIN`. If the email already exists, it **re-links that user to the school, overwrites their role and reactivates them**, even if suspended. | `SchoolAdminProvisioningServiceImpl.java:57-69` |
| OB-2 | **Critical** | **One-call bypass of verification, approval and payment.** `POST /onboarding/{id}/activate` takes DRAFT → ACTIVE. It self-confirms MANUAL payments (`confirmedBy` from the request) and activates even when an online payment was only *initiated*. Its idempotency key includes `currentTimeMillis()`, so each call creates a new payment. | `OnboardingOrchestrationServiceImpl.java:95,120-123` |
| OB-3 | **Critical** | All 18 onboarding endpoints and 19 school-detail endpoints are authorization-free (audit S-02). Any school user can approve, provision or activate any onboarding, or verify their own documents. | controllers above |
| OB-4 | High | **Activation has no precondition.** It doesn't require `PROVISIONED`, and passes when no provisioning record exists (`provisioning != null && …`). No payment, approval or readiness check. | `SchoolOnboardingWorkflowServiceImpl.java:533-560` |
| OB-5 | High | **Approval does not require payment.** Allowed from `VERIFIED` and `PLAN_SELECTED`, with no policy explaining when that's legitimate. | `…WorkflowServiceImpl.java` approve; `OnboardingServiceImpl.java:339` |
| OB-6 | High | **Approver identity comes from the client** (`request.getApprovedBy()`). Every other activity has `performedBy = null`. The audit trail can't say who did anything. | `…WorkflowServiceImpl.java:376`; `OnboardingServiceImpl` throughout |
| OB-7 | High | **Every tenant DB uses one shared Postgres role.** Credential refs are hardcoded to `TENANT_DB_USER` / `TENANT_DB_PASS`. The role is created once and then granted ALL on every tenant DB, so there's no database-level isolation between schools. | `…WorkflowServiceImpl.java:429`; `OnboardingOrchestrationServiceImpl.java:138` |
| OB-8 | High | **Client chooses the database host and port** for provisioning (`request.getDatabaseHost()`). Combined with OB-3, any user can point provisioning at an arbitrary host. | `…WorkflowServiceImpl.java:415-416` |
| OB-9 | High | **Provisioning is broken.** Tenant migration V18 fails (`class_section`), so every real provisioning ends in `PROVISIONING_FAILED`. | `tenant/V18…sql:11` |
| OB-10 | Medium | `reject` has no state precondition. An ACTIVE school's onboarding can be "rejected", leaving school, tenant and onboarding inconsistent. | `OnboardingServiceImpl.java:364` |
| OB-11 | Medium | Two `approve` implementations with different allowed source states (`OnboardingServiceImpl` vs `…WorkflowServiceImpl`). The workflow one (and `activateSchool`) isn't exposed by any controller. `activateSchool` is reachable only through `POST /retry`. | both |
| OB-12 | Medium | Board mapping: `SchoolBoard.STATE` / `IGCSE` vs `BoardAffiliationType.STATE_BOARD` / `CAMBRIDGE_IGCSE`, mapped by `valueOf(name())`. State-board and IGCSE schools are silently stored as `OTHER`. | `OnboardingServiceImpl.java:168-170` |
| OB-13 | Medium | **The `school.board` CHECK accepts anything.** `CHECK (board IN (…, NULL))` evaluates to NULL (pass) for unlisted values. | master `V26…sql:15` |
| OB-14 | Medium | Initial admin password is generated, then **returned in the API response** and stored nowhere else. There's no invitation, no forced reset and no expiry. | `SchoolAdminProvisioningServiceImpl.java:78,171` |
| OB-15 | Medium | No duplicate-school detection on initiate. The same school can be onboarded many times (one per field agent). | `OnboardingServiceImpl.java:101` |
| OB-16 | Medium | Step data is unvalidated JSON, and `markCompleted` is trusted from the client. `advanceCurrentStep` uses enum ordinal order, so completing `DOCUMENTS` jumps to `PLAN_SELECTION` regardless of what was actually done. | `OnboardingServiceImpl#saveStepData` |
| OB-17 | Medium | No compensation when provisioning fails after creating the DB. A tenant row plus a half-built database remain. Retry handles this, but there's no cleanup or cancel path. | `TenantProvisioningService` |
| OB-18 | Low | "Latest subscription" is `findBySchoolId(...).get(0)` with no ordering. | `…WorkflowServiceImpl.java` initiatePayment, progress |
| OB-19 | Low | `school_document.file_url` is client-supplied, so the verification step trusts arbitrary URLs (no storage service). | master V26 |
| OB-20 | Low | `TenantProvisioningService` protected `@Transactional` methods are self-invoked, so their annotations have no effect (repository saves still commit individually). | `TenantProvisioningService.java` |
| OB-21 | Info | `TenantInitializationService` only inserts `tenant_metadata`. A new school's DB contains no roles, settings or defaults. | `tenant/provisioning` |

## 6. What is good and must be preserved

- `TenantProvisioningService`: step-tracked (`DATABASE_CREATING → MIGRATION_RUNNING → INITIALIZATION_RUNNING`), resumable from the last completed step, idempotent DB and user creation, attempt counting, failure reason recorded.
- Optimistic locking on `SchoolOnboarding` (`@Version` through `AuditableEntity`).
- Subscription snapshot at plan selection (plan code and version frozen).
- Payment-completed handling as an idempotent event listener.
- Normalized school sub-tables (contacts, addresses, multi-board affiliations, documents with verification status).
- `SubscriptionEntitlementService` with per-school overrides, ready to drive conditional steps.
- Tenant-side academic engines (academic year, calendar, holidays, working days, classes, sections, subjects, rooms, bell schedules) are mature and should be the *targets* of onboarding configuration, not re-implemented.
