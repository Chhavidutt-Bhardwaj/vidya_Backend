# Implementation Roadmap

Ordered by risk and dependency, not by module list. Each phase ends with a green build that includes real-DB and real-security tests. **Nothing here has been started. P0 needs approval; P1 items marked "decision" need product or architecture sign-off before work begins.**

## P0: Stop the bleeding (security, integrity, build). About 1–1.5 weeks.

No architectural change. Small, independently mergeable PRs.

| # | Task | Fixes | Notes |
|---|---|---|---|
| P0-1 | Fix tenant `V18` FK `class_section` → `section` (in place; it has never applied anywhere) | DB-1, TD-T1 | First, because it un-breaks the build and provisioning. Confirm no environment has a hand-patched V18. |
| P0-2 | Remove the `SuperUserDataInitializer` password reset; restrict to the `dev` profile. Move demo users out of versioned migrations. Prod bootstrap admin from env with forced reset. | S-01 | **Existing environments:** rotate those accounts' passwords and remove demo users manually. Migrations V18 and V22 are already applied, so add a new migration rather than editing them. |
| P0-3 | Authorization on all 136 master endpoints: permission-based `@PreAuthorize` plus `canAccessSchool` / `canAccessOnboarding` ownership checks | S-02, API-1 | Behaviour change for clients: school users lose access to platform-only endpoints. Document it in the API changelog. |
| P0-4 | Default-deny guard: an ArchUnit test failing on any handler without an explicit authorization annotation | TD-T5 | Prevents regression of P0-3 |
| P0-5 | Payment fail-closed: mandatory signature, amount and currency check, idempotency on provider event ID, remove test-secret special cases. Mark gateways as sandbox-only until real API calls exist. | S-03 | |
| P0-6 | DTOs and masking for community detail endpoints. Encrypt bank, PAN and Aadhaar at rest (new migration plus backfill). | S-05, API-3 | Needs a key-management decision (env key for now, KMS later) |
| P0-7 | Tenant migration runner for existing tenants, with per-tenant version tracking | DB-13, G-3 | Must land before any further tenant migration ships |
| P0-8 | JWT filter rejects disabled or locked users. Remove the CORS `*` fallback. No default profile. Fail on dev secrets under `prod`. | S-06, S-07, S-08 | |
| P0-9 | Test harness: `@SpringBootTest` + Testcontainers (Postgres, Redis) + real JWT. Role × endpoint matrix, cross-tenant and webhook-forgery tests. Minimal CI (GitHub Actions running `mvn verify`). | S-13, TD-T2, TD-T4 | Turns all later work into verifiable work |

**Exit criteria:** `mvn verify` is green in CI, including the 9 integration classes and the new security matrix; a manual forged-webhook and cross-role check fails as expected.

## P1: Core school operations. About 6–10 weeks.

| # | Task | Gap | Depends |
|---|---|---|---|
| P1-0 | **Decision:** organization and campus model (G-8 option A or B) | G-8 | — |
| P1-1 | `CurrentActor` resolution (login → staff / student / guardian). Teacher scope on attendance and marks. Allow PRINCIPAL, ACCOUNTS, etc. into tenants. | G-1, S-04, S-09 | P0 |
| P1-2 | Tenant-configurable RBAC (roles, permissions, scoped assignments). Migrate `@PreAuthorize` to permission-only checks. | G-2, API-6 | P1-1 |
| P1-3 | Business audit trail plus tenant JPA auditing | G-5, DB-8, DB-12 | P1-1 |
| P1-4 | Student data fixes: shared guardians, partial unique indexes, optional email, single enrollment model, medical and emergency info | DB-2/4/5/6/9 | P0 |
| P1-5 | Admission pipeline (enquiry → application → approval → registration) | C | P1-4, P1-7 |
| P1-6 | Year-end promotion and academic-year rollover | C | P1-4 |
| P1-7 | File storage plus tenant document table | G-7 | P1-1 |
| P1-8 | Scheduler, ShedLock and outbox | G-6 | P0 |
| P1-9 | **Fees and finance (MVP):** heads, structures, assignments, concessions, invoices, payments (cash, cheque, online), receipts, reversals, defaulters | G | P1-3, P1-8, P1-0 |
| P1-10 | Secret-store `CredentialResolver` for tenant DBs | G-4 | P0 |
| P1-11 | Attendance completion: staff attendance, leave apply and approve, correction with audit | E | P1-1, P1-3 |

## P2: Academics, results, HR, operations. About 8–12 weeks.

Report cards (service, controller, PDF, publish and lock) · weightage engine (board-configurable) · result approval and controlled correction · bulk marks upload · homework and assignments · lesson plans · period-wise attendance · staff HR and payroll · documents and certificates with QR verification · transport · library · feature-entitlement enforcement (G-9).

## P3: Communication, compliance, analytics

Notices, circulars and acknowledgements · templates plus email, SMS and WhatsApp adapters · fee, attendance and exam notifications · PTM scheduling · inventory and assets · safety and wellbeing · school dashboards · compliance checklists and UDISE+ export files · org-level reporting.

## P4: AI and advanced

Read-only, permission-scoped, audited assistants first (natural-language reports, performance summaries, message drafting). Then anomaly detection (attendance) and forecasting (fee collection). Timetable generation. Every write path stays human-approved.

---

## Working agreement for each task (from the brief)

1. Read the affected code and tables. 2. Write a short plan in the PR. 3. Smallest complete workflow. 4. New Flyway migration (never edit an applied one). 5. Validation and authorization. 6. Unit plus real-DB plus security tests. 7. `mvn verify` green. 8. Update `IMPLEMENTATION_PROGRESS.md` and API docs.

`IMPLEMENTATION_PROGRESS.md` will be created when P0 starts. It would duplicate this audit if created now.

## Decisions needed from you

| # | Decision | Why it matters now |
|---|---|---|
| D-1 | Approve P0 as scoped above | All later work builds on it |
| D-2 | Organization and campus model (G-8 A vs B) | Determines whether fees and attendance tables carry `campus_id` from day one |
| D-3 | Keep platform go-to-market modules (community, hub, commissions) in this service, or split them out later | Community + hub are about 4.8k of about 56k production LOC (~8%). They're sales tooling, unrelated to school operations, and carry the worst PII exposure. |
| D-4 | Key management for PII encryption (env key vs cloud KMS) | Needed for P0-6 |
| D-5 | Canonical route prefix: `/api/v1/tenant/...` or `/api/v1/...` | Needed for the deprecation notice to frontend clients |
