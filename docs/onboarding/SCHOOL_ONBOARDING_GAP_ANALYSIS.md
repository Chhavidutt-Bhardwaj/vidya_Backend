# School Onboarding: Gap Analysis

Compares the [existing implementation](SCHOOL_ONBOARDING_EXISTING_IMPLEMENTATION.md) with the requested enterprise onboarding journey. Priorities: **P0** security and integrity · **P1** needed for the first real school to go live · **P2** needed for scale or enterprise customers · **P3** later. Complexity: S / M / L / XL.

## 1. Journey stages: requested vs existing

| # | Requested stage | Exists today | Gap | Pri | Cx |
|---|---|---|---|---|---|
| 1 | Lead / registration | `initiateOnboarding`, sources | Duplicate detection. Lead owner. Board mapping fix (OB-12). | P1 | S |
| 2 | Verification | boolean `verify` | Checklist (documents, affiliation number, contact verification). Approval record with actor. | P1 | M |
| 3 | Commercial approval | plan snapshot, payment, approve | Single approve path. Payment-or-waiver policy. Maker-checker. | P0 (fix) / P1 | M |
| 4 | Tenant provisioning | step engine | Per-tenant DB role, server-side host, compensation, V18 fix | P0 | M |
| 5 | Organization setup | — | Organization aggregate, types, PAN/GST, owner, branding | P1 | M |
| 6 | School setup | profile + sub-tables | UDISE, category, establishment, logo, timezone, locale, hours | P1 | S |
| 7 | Campus setup | `school_branch` (master only) | Campus as a first-class configured unit (decision D-2) | P1 | M–L |
| 8 | Board configuration | enums + tenant grading scales | Versioned board templates applied into tenant config | P1 (minimal) / P2 (full) | L |
| 9 | Academic year setup | tenant engine (no onboarding hook) | Onboarding task that calls the existing services. Readiness checks. | P1 | S |
| 10 | Academic structure | tenant engines | Same, plus bulk create (classes and sections from template). Streams, houses and departments later. | P1 / P2 | M |
| 11 | Users and roles | initial admin only | Invitations, activation, revocation, scoped roles | P1 | L |
| 12 | Fee configuration | **fees module missing** | Blocked on the fees module | P2 (after fees) | — |
| 13 | Attendance and timetable | bell schedules, periods, working days | Onboarding tasks over existing services. Attendance policies later. | P1 / P2 | S |
| 14 | Communication and integrations | — | Integration registry, encrypted credentials, test-connection | P2 | L |
| 15 | Data migration | — | Batch framework (upload → map → validate → dedupe → preview → approve → import → reconcile) | P1 (students, staff, classes) / P2 (rest) | XL |
| 16 | Training | — | Task type only (manual completion with evidence) | P1 | S |
| 17 | UAT | — | Task plus UAT approval | P1 | S |
| 18 | Go-live approval | — | Readiness engine plus approval | P1 | M |
| 19 | School activation | `activateSchool` (unguarded, ungated) | Gate on readiness. Authorized activator only. | P0 (fix) / P1 | S |
| 20 | Post-go-live monitoring | — | Hypercare period, health signals (logins, attendance marked, errors) | P3 | M |

## 2. Cross-cutting capabilities

| Capability | Existing | Missing | Business impact | Technical impact | Depends on | Recommendation | Pri | Cx |
|---|---|---|---|---|---|---|---|---|
| **Authorization on onboarding** | none | Permissions per action, ownership (community agent → own leads, school admin → own school) | Anyone can approve, activate or escalate | Blocks everything | — | `@PreAuthorize` with new `ONBOARDING_*` permissions plus `canAccessOnboarding` (already implemented, unused) | **P0** | S |
| **Task engine** | step enum + JSONB | Tasks with category, weight, dependencies, owner, role, status, blocker, approval, audit, parallelism | No owner accountability, no progress truth | Core of the new design | P0 | Template-driven: versioned `onboarding_task_template` → `onboarding_task` instances per onboarding. Dependencies as a DAG. Validators as pluggable Spring beans keyed by task code. | P1 | L |
| **Approval workflow** | single actor, not recorded | Maker-checker, policies, separation of duties, reasons | Unauthorized self-approval | — | task engine | `approval_policy` (task or approval type → required permission, min approvers, `maker_cannot_approve`) plus an `onboarding_approval` record | P1 | M |
| **Readiness engine** | progress snapshot | Rule evaluation, blockers, % by weight, next actions | Schools go live half-configured | Needs cross-DB reads (master plus tenant) | task engine | `ReadinessService` composing `ReadinessCheck` beans. Master checks read master. Tenant checks run under `TenantContext` for that tenant, read-only. Never in one transaction. | P1 | M |
| **Audit** | `onboarding_activity` with null actors | Actor, before/after, reason | Can't answer "who approved" | — | — | Keep the table. Add `actor_user_id` from `SecurityContext` and `reason`. Write on every task, approval and config change. | P0 (actor) / P1 | S |
| **Invitations** | password in response | Token (hashed, single-use, expiring), activation, resend, revoke | Credential leakage | Needs email delivery | mail config | `user_invitation` table in master. Delivery through the existing (unused) mail starter behind a `NotificationSender` interface. Log-only sender in dev. | P1 | M |
| **Tenant RBAC / scoped roles** | enum, most roles denied tenant access | Principal, Accountant, HOD… with org, school, campus, department or class scope | Can't invite anyone but admins and teachers | Platform-wide change | decision D-6 | See plan §3. Shared with the platform roadmap (audit G-2). | P1 | L |
| **Organization model** | none | Org with many schools | Chains and trusts unsupported | Master schema | decision D-2 | `organization` + `school.organization_id` (nullable for backfill) | P1 | M |
| **Board templates** | none | Versioned, customizable rule packs | Every school hand-configured | New master data + "apply" into tenant | org/school | `board_template` + `board_template_version` (JSONB payload validated against a schema). Applied by a tenant-side `BoardConfigurationApplier` that calls existing grading, subject and curriculum services. Snapshot of the applied version kept per school. | P2 (P1 for a minimal CBSE / State pack) | L |
| **Integration registry** | none | Per-school provider config, encrypted secrets, test, status | Can't onboard SMS or payments per school | Secrets handling | KMS decision D-4 | `school_integration` (provider, type, config JSONB, encrypted secret, status, last test result) | P2 | L |
| **Data migration** | none | Batch pipeline | Every real school has existing data | File storage, async jobs | file storage, scheduler | `migration_batch`, `migration_row` (raw, mapped, errors, duplicate-of, result), entity-specific importers reusing existing create services in two-phase mode | P1 (students, guardians, staff, classes, sections) | XL |
| **Conditional steps** | entitlements service (unused) | Applicability rules per task | Small schools forced through irrelevant steps | — | task engine | Task template `applicability` expression evaluated against modules, school type and board → `NOT_APPLICABLE` status (excluded from %) | P1 | S |
| **Concurrency** | `@Version` on onboarding | Per-task optimistic locking, conflict responses | Lost updates with multiple users | — | task engine | `@Version` on tasks. 409 on conflict. Task-level assignment avoids contention on the aggregate. | P1 | S |
| **Frontend** | not in repo | Wizard, dashboards | — | — | APIs | Backend exposes task-list, readiness and approval-queue APIs shaped for the wizard. UI work happens in the frontend repo. | P1 (API) | — |

## 3. Blocked or out of scope for this phase

| Item | Why |
|---|---|
| Fee configuration (§9) | The student fees module doesn't exist (audit MODULE_STATUS G). Building fee setup screens without the fee domain would duplicate work. The onboarding task `FEES_CONFIGURED` will be registered and marked not applicable until the module lands. |
| Opening balances / fee migration | Same |
| Biometric, GPS, WhatsApp, push providers | Provider selection is a business decision. The registry design accommodates them, and adapters come later. |
| UDISE+ / board portal integrations | No verified official APIs. Only data fields and export readiness. |
| Seven-state academic-year lifecycle | Would break the existing `AcademicYear` API and data (decision D-7) |

## 4. Prerequisites from the platform audit

These must land first, or alongside increment 1:

1. Tenant migration V18 fix: otherwise no onboarding can reach `PROVISIONED`.
2. Migration runner for existing tenants (audit P0-7): every onboarding increment adds tenant tables.
3. Remove the superuser password reset (audit S-01).
4. Payment webhook fail-closed (audit S-03): onboarding advances on `PaymentCompletedEvent`.
5. `@SpringBootTest` + Testcontainers harness (audit P0-9): required to test provisioning and authorization for real.
