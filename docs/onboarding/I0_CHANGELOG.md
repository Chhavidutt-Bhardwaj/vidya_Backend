# Onboarding I0 — Security & Integrity Fixes: Changelog and Migration Notes

Branch: `feature/onboarding-i0-security` · Scope: Increment I0 only (no task engine, no organization/campus management).

This document lists every behavioural, API and schema change in I0 so frontend and integration owners
can migrate. **Breaking changes are marked ⚠️.**

---

## 1. Summary

| # | Objective | Status | Where |
|---|-----------|--------|-------|
| 1 | Privilege escalation in initial admin creation | Fixed | `SchoolAdminProvisioningServiceImpl` — role whitelist `SCHOOL_OWNER`/`SCHOOL_ADMIN` |
| 2 | Silent reassignment / reactivation of existing users | Fixed | Same — existing accounts are never modified; conflict → 409 |
| 3 | Verification / payment / approval bypasses | Fixed | `OnboardingStatus` transition table; `/activate` rewritten; gateways fail closed |
| 4 | Authorization on all onboarding endpoints | Done | `@PreAuthorize` on 4 onboarding controllers + school + payment controllers |
| 5 | School- and tenant-level ownership validation | Done | `canAccessOnboarding` / `canAccessSchool` on every `{id}` endpoint; `@PostAuthorize` on code lookups |
| 6 | Actor identity only from SecurityContext | Done | `OnboardingActor`; `approvedBy` / `confirmedBy` ignored |
| 7 | Centralized onboarding state transitions | Done | `OnboardingStatus.validateTransitionTo` (single transition table) |
| 8 | Payment approval & waiver policies | Done | Approve requires `PAYMENT_COMPLETED` or `PAYMENT_WAIVED`; new `/payment/waive` |
| 9 | No client-controlled DB host/port | Done | `vidya.provisioning.tenant-db.{host,port}`; request fields rejected (400) |
| 10 | Board enum mapping | Fixed | `SchoolBoard.toAffiliationType()` (STATE→STATE_BOARD, IGCSE→CAMBRIDGE_IGCSE) |
| 11 | Board DB CHECK constraint | Fixed | Master `V28` |
| 12 | Tenant migration V18 | Fixed | `section(id)` instead of non-existent `class_section(id)` |
| 13 | Failure handling & provisioning retry | Fixed | start-or-resume helper, stale-version fixes, 409 on optimistic-lock conflicts |
| 14 | Audit activity recording | Fixed | Every activity records the authenticated actor and correct performer type |
| 15 | Real DB integration tests | Done | `OnboardingSecurityIntegrationTest` (Postgres + Redis Testcontainers, full Spring context) |

---

## 2. ⚠️ Breaking API changes

### 2.1 Authentication/authorization now enforced

All endpoints below previously accepted **any authenticated user** (or relied only on the global
"authenticated" rule). They now require specific permissions **and** ownership of the target.

| Endpoint | Rule |
|----------|------|
| `POST /api/v1/onboarding` | `ONBOARDING_CREATE`; community users must attribute their **own** `communityId`/`communityMemberId` |
| `GET /api/v1/onboarding` | `ONBOARDING_READ` and (platform admin **or** `communityId` param = a community the caller belongs to) |
| `GET /api/v1/onboarding/{id}`, `/{id}/detail`, `/{id}/workflow-status` | `ONBOARDING_READ` + onboarding ownership |
| `GET /api/v1/onboarding/code/{code}` | `ONBOARDING_READ` + ownership of the returned record (`@PostAuthorize`) |
| `PUT /{id}/steps`, `POST /{id}/submit`, `/{id}/select-plan`, `/{id}/payment/initiate` | `ONBOARDING_UPDATE` + ownership |
| `POST /{id}/verify` | `ONBOARDING_VERIFY` (platform) |
| `POST /{id}/approve`, `POST /{id}/reject` | `ONBOARDING_APPROVE` (platform) |
| `POST /{id}/payment/waive` *(new)* | `ONBOARDING_PAYMENT_WAIVE` (platform) |
| `POST /{id}/provision` | `TENANT_PROVISION` (platform) |
| `POST /{id}/admin` | `ONBOARDING_ADMIN_CREATE` (platform) |
| `POST /{id}/retry`, `POST /{id}/activate` | `ONBOARDING_ACTIVATE` + `TENANT_PROVISION` (platform) |
| `POST /api/v1/payments/initiate` | `PAYMENT_CREATE` + ownership of `schoolId` |
| `POST /api/v1/payments/verify` | authenticated (settlement now requires a valid gateway signature) |
| `POST /api/v1/payments/manual/confirm` | `PAYMENT_MANUAL_CONFIRM` (platform) |
| `GET /api/v1/payments/{transactionCode}` | `PAYMENT_READ` + ownership of the transaction's school |
| `GET /api/v1/payments/school/{schoolId}` | `PAYMENT_READ` + school ownership |
| `POST /api/v1/payments/refund` | `PAYMENT_REFUND` + platform role |
| `POST /api/v1/schools`, `GET /schools`, `GET /schools/stats`, `PATCH /schools/{id}/status` | platform only |
| `GET/PUT /api/v1/schools/{id}`, branches, `/code/{code}`, `/tenant/{tenantId}` | `SCHOOL_READ`/`SCHOOL_UPDATE` + school ownership |
| `/api/v1/schools/{id}/contacts|addresses|boards|documents|complete-profile` | read: `SCHOOL_READ`; write: `SCHOOL_UPDATE` or `ONBOARDING_UPDATE`; + school ownership |
| `POST /api/v1/schools/{id}/documents/{docId}/verify|reject` | `ONBOARDING_VERIFY` + ownership |

Responses: **401** unauthenticated, **403** missing permission / not the owner.
Note: for non-platform users a non-existent onboarding/school also yields **403** (no existence oracle).

### 2.2 Workflow sequencing (422 `INVALID_STATE_TRANSITION`)

| Operation | Before | Now |
|-----------|--------|-----|
| `select-plan` | any non-terminal status (incl. DRAFT) | `VERIFIED` (re-select allowed in `PLAN_SELECTED` / `PAYMENT_PENDING`) |
| `payment/initiate` | from DRAFT or PLAN_SELECTED | `PLAN_SELECTED` (re-initiate in `PAYMENT_PENDING`) |
| `approve` (both services) | from VERIFIED / PLAN_SELECTED / PAYMENT_COMPLETED | **only** `PAYMENT_COMPLETED`, `PAYMENT_WAIVED`, `APPROVAL_PENDING` |
| `reject` | from **any** status, including ACTIVE | any pre-provisioning status up to `APPROVED`; **not** from `PROVISIONING`, `PROVISIONED`, `ACTIVE`, `REJECTED`, `CANCELLED` |
| `admin` (initial admin) | any status | `PROVISIONED` |
| `activate` (workflow) | any status; missing provisioning record treated as done | `PROVISIONED` + provisioning record `COMPLETED` |
| `retry` | always re-provisioned + activated | `APPROVED`, `PROVISIONING`, `PROVISIONING_FAILED`, `FAILED`, `PROVISIONED`; no-op when `ACTIVE` |
| Payment success event | advanced any non-terminal onboarding to PAYMENT_COMPLETED | advances only `PAYMENT_PENDING` |

### 2.3 `POST /api/v1/onboarding/{id}/activate` (path kept)

* Previously: subscribed the school, initiated **and self-confirmed** a payment (`confirmedBy` from the body),
  created a tenant on hard-coded `localhost:5432`, and activated from almost any status.
* Now: finishes an **approved** onboarding only — provisions if needed (`APPROVED`, `PROVISIONING_FAILED`, …)
  and activates via the workflow service. Any other status → **422**; `ACTIVE` → 422 (already active).
* Body is now **optional**. `planId`, `billingCycle`, `discountAmount`, `paymentProvider`, `paymentMethod`,
  `idempotencyKey`, `referenceNumber`, `confirmedBy` are **deprecated and ignored** (no longer `@NotNull`).
  `paymentNotes` is kept as the activation note.
* Migration: call `select-plan` → `payment/initiate` (or `payment/waive`) → `approve` → `activate`.

### 2.4 `POST /api/v1/onboarding/{id}/provision`

* `databaseHost` / `databasePort` are **rejected with 400** if present. Send no body or `{}`.
* The location comes from `vidya.provisioning.tenant-db.host` / `.port` (env `TENANT_DB_HOST` / `TENANT_DB_PORT`).

### 2.5 `POST /api/v1/onboarding/{id}/admin`

* `role` must be `SCHOOL_OWNER` (default) or `SCHOOL_ADMIN`; anything else → **400**.
* If the email belongs to an existing account → **409** (the account is not modified), unless it is already
  an active `SCHOOL_OWNER`/`SCHOOL_ADMIN` of the **same** school (idempotent replay → 200, no password).

### 2.6 Payments

* Razorpay `verify`: requires `providerOrderId`, `providerPaymentId` **and** a valid `providerSignature`
  (HMAC of `order|payment`); the order id must match the transaction. Missing/invalid → transaction `FAILED`.
* Cashfree `verify`: always fails closed (no server-side order lookup yet) — settle via signed webhook.
* Webhooks (both gateways): a valid signature is **always** required; the test-secret bypass is removed.
* `verify` on a `MANUAL` transaction → **422**; use `/payments/manual/confirm` (`PAYMENT_MANUAL_CONFIRM`).
* `ManualPaymentConfirmRequest.confirmedBy` and `WorkflowApproveRequest.approvedBy` are **deprecated and ignored**;
  the authenticated user is recorded.

---

## 3. New API

`POST /api/v1/onboarding/{id}/payment/waive` — permission `ONBOARDING_PAYMENT_WAIVE`

```json
{ "reason": "Pilot school — 6-month free trial", "reference": "CONTRACT-2026-0142" }
```

`reason` required (blank → 400). Allowed from `PLAN_SELECTED` or `PAYMENT_PENDING` → `PAYMENT_WAIVED`.
Response: `onboardingId, onboardingCode, onboardingStatus, nextStep, waivedBy, reason, waivedAt`.
The waiver is audited as `PAYMENT_WAIVED` with the authenticated approver.

New error: **409 `CONCURRENT_MODIFICATION`** when two requests update the same record concurrently (`@Version`).

---

## 4. Permissions & roles

New `PlatformPermission` values, granted to `PLATFORM_ADMIN` (and implicitly `PLATFORM_SUPER_ADMIN`):
`ONBOARDING_ACTIVATE`, `ONBOARDING_PAYMENT_WAIVE`, `ONBOARDING_ADMIN_CREATE`, `PAYMENT_MANUAL_CONFIRM`.
No existing role lost a permission. Permissions are enum-based (not persisted) — no data migration needed.

New authorization helpers: `TenantAuthorizationService.isCommunityMember(auth, communityId)` and
`canInitiateOnboarding(auth, communityId, communityMemberId)`.

---

## 5. Database migrations

| Migration | Change | Data impact |
|-----------|--------|-------------|
| Master `V28__onboarding_security_and_integrity.sql` (new) | `chk_school_onboarding_status` + `PAYMENT_WAIVED`; `chk_school_board` replaced (old one accepted any value because of `IN (..., NULL)`) | None. Board constraint added `NOT VALID`, then validated only if no row violates it; otherwise a WARNING is raised and the constraint still applies to all new writes |
| Tenant `V18__create_grading_and_report_cards.sql` (edited in place) | `section_id` FK → `section(id)` | None. The old version could never be applied (transactional DDL rolled back), so no checksum mismatch for any migrated tenant |

Operational note: if `V28` logs `chk_school_board left NOT VALID`, correct the listed rows and run
`ALTER TABLE school VALIDATE CONSTRAINT chk_school_board;`.

## 6. Configuration

```yaml
vidya.provisioning.tenant-db.host: ${TENANT_DB_HOST}        # dev default: localhost
vidya.provisioning.tenant-db.port: ${TENANT_DB_PORT:5432}
```

**Prod requires `TENANT_DB_HOST`.**

## 7. Provisioning robustness fixes (found by the real-DB tests)

* `CREATE DATABASE … LC_COLLATE 'en_US.UTF-8'` failed on servers whose template locale is `en_US.utf8`
  → now `TEMPLATE template0 ENCODING 'UTF8'` (server locale).
* `TenantProvisioningService` re-saved a detached entity with a stale `@Version` after every step → failures
  could not be recorded and retries broke; the engine now continues with the merged instance.
* `TenantInitializationService` bound `java.time.Instant` via JDBC `setObject` (unsupported by PgJDBC) → now
  `OffsetDateTime`. Previously **no** tenant could complete provisioning on a real database.
* Workflow `provisionTenant` re-saved a stale onboarding; `retryWorkflow` self-invoked `activateSchool`
  bypassing its transaction (LazyInitializationException). Both fixed.
