# Security Audit

Revision `9c6d509`. Every finding below was confirmed by reading the source; file references are given. Severity reflects impact if the build were deployed as-is.

## Summary

| ID | Severity | Title |
|---|---|---|
| S-01 | **Critical** | Super-admin accounts force-reset to a public password on every startup, in every environment |
| S-02 | **Critical** | 136 master endpoints have no authorization beyond "logged in" |
| S-03 | **Critical** | Payment verification and webhooks can be forged |
| S-04 | High | No row-level scoping inside a school (teacher, parent and student scope missing) |
| S-05 | High | Bank account, PAN and Aadhaar stored in plaintext and returned unmasked as raw entities |
| S-06 | Medium | Suspended or locked users keep master API access until their token expires |
| S-07 | Medium | Unsafe defaults: dev profile active by default; dev secrets in config |
| S-08 | Medium | CORS falls back to `*` with credentials when the origin list is empty |
| S-09 | Medium | Role model is a hardcoded enum; most school roles cannot enter a tenant at all |
| S-10 | Medium | No business audit trail for sensitive academic records |
| S-11 | Low | Client-writable login history |
| S-12 | Low | Security filters registered twice (servlet container plus security chain) |
| S-13 | Info | Security test coverage is illusory |

---

### S-01 Critical: Known-password super-admin backdoor

`security/init/SuperUserDataInitializer.java` is an `ApplicationRunner` `@Component` with **no `@Profile` or `@Conditional`**. On every boot it:

1. Ensures `superuser@vidya.io` and `admin@vidya.io` exist as `PLATFORM_SUPER_ADMIN`.
2. If their password does not match `Password@123`, **resets it to `Password@123`** (line ~44).
3. Forces status `ACTIVE` and clears lockout counters.

Master migrations `V18__seed_initial_master_data.sql` and `V22__seed_superuser_and_auto_role_support.sql` also insert these and demo users (`agency.lead@edupartners.org`, `agent.rahul@edupartners.org`) with the same BCrypt hash into every environment.

**Impact:** anyone who reads this repository, or guesses the password, has full platform control in production, and rotating the password does not help because the next restart reverts it.
**Fix:** delete the runner, or restrict it to the `dev` profile. Move demo seed rows out of versioned migrations into a dev-only `R__` or `afterMigrate` script excluded from prod locations. Bootstrap the first prod admin through a one-time, env-provided credential with a forced password change.

### S-02 Critical: Master domain has no authorization

15 controllers and 136 endpoints (full list in API_INVENTORY §3) carry no `@PreAuthorize`, and no master service calls `TenantAuthorizationService` or checks roles. `TenantResolutionFilter` exempts these paths from tenant checks. Any authenticated user, including a school TEACHER, can therefore:

- `POST /api/v1/schools`, `PUT /api/v1/schools/{id}`, `PATCH /api/v1/schools/{id}/status` (deactivate any school)
- `POST /api/v1/onboarding/{id}/approve`, `POST /api/v1/onboarding/{id}/provision` (create tenant databases)
- `POST /api/v1/subscription-plans`, `PATCH …/{id}/publish`, `POST …/custom-plan`
- `POST /api/v1/payments/manual/confirm`, `POST /api/v1/payments/refund`
- read every school, payment, community member's PII, analytics KPIs and support ticket

`TenantAuthorizationServiceImpl` already implements `canAccessSchool` and `canAccessOnboarding`, but nothing calls them outside the tenant filter.
**Fix:** permission-based `@PreAuthorize` on every master endpoint plus ownership checks (`@tenantAuthorizationService.canAccessSchool(authentication, #id)`). Add a default-deny safety net: a startup assertion or ArchUnit test that fails the build if any `@RestController` handler lacks `@PreAuthorize`, `@PermitAll` or an explicit allowlist entry.

### S-03 Critical: Payment verification bypass

| Path | Problem | Location |
|---|---|---|
| `RazorpayPaymentGateway.verifyPayment` | Signature checked **only if** signature, order ID and payment ID are all non-null. Otherwise it falls through to `successful(true)`. | `master/payment/gateway/razorpay/RazorpayPaymentGateway.java:74-100` |
| Razorpay webhook | Verified only `if (signature != null && secret not blank && secret != "rzp_test_webhook_secret")`. A request **without** the header is processed. | same file `:124-130` |
| Cashfree webhook | Same pattern (`timestamp != null && signature != null && secret != "cf_test_vidya_secret"`) | `cashfree/CashfreePaymentGateway.java:116-123` |
| `ManualPaymentGateway.verifyPayment` / `processRefund` | Always succeed. Reachable by any authenticated user (S-02). | `manual/ManualPaymentGateway.java:51-75` |
| Webhook endpoints | `permitAll` in `SecurityConfig` | `security/config/SecurityConfig.java` |

The gateways also never call provider APIs (order IDs are random strings), so amounts and order existence are never reconciled with the provider.
**Impact:** an unauthenticated attacker can post `{"event":"payment.captured",...}` with a known order ID and mark a subscription paid, which (through the `@EventListener` in `SchoolOnboardingWorkflowServiceImpl`) advances onboarding and provisioning.
**Fix:** fail closed. Reject when the signature is missing or invalid, never special-case test secrets, verify amount and currency against the stored transaction, make processing idempotent on provider event ID, and use real provider SDK/API calls for order creation and status fetch.

### S-04 High: No intra-tenant data scoping

Tenant access is checked at school level only. Inside a school:

- `StudentAttendanceController#extractStaffId` **always returns `null`**, and the service skips teacher-scope checks when it is null (documented in the source comment at ~line 245).
- Marks entry (`StudentMarkServiceImpl`) does not verify that the caller is assigned to that subject and section.
- A TEACHER can read and modify every student, guardian, attendance record, timetable and mark in the school.
- PARENT and STUDENT roles exist in `PlatformRole`, but `TenantAuthorizationServiceImpl.canAccessTenant` denies them, so there is no way to serve "my child only" data.

Root cause: there's no resolver from `PlatformUser` → `tenant_user` → `staff` / `student` / guardian (ARCHITECTURE_OVERVIEW §7).

### S-05 High: Plaintext financial and national-ID data, unmasked

`master/community/entity/CommunityBankDetail.java` (`accountNumber`, `ifscCode`) and `CommunityPersonalDetail.java` (`panNumber`, `aadhaarNumber`) are stored as plain `VARCHAR` and returned **as JPA entities** by `CommunityDetailController` (`GET …/bank-details`, `…/payroll`, `…/commissions`) to any authenticated user (S-02). Aadhaar handling is regulated in India (storage, masking, purpose limitation), and India's DPDP Act 2023 applies to all personal data processed here, including student data.
**Fix:** DTOs with masking (`XXXX1234`), column-level encryption (JPA `AttributeConverter` with a KMS-held key, or `pgcrypto`), a dedicated permission for unmasked reads, and an audit entry on every unmasked access. Re-check whether Aadhaar needs to be stored at all.

### S-06 Medium: Disabled users keep access

`JwtAuthenticationFilter` loads `UserDetails` but never checks `isEnabled()` / `isAccountNonLocked()` before authenticating. Tenant routes re-check status in `canAccessTenant`; master routes do not. A suspended platform admin keeps master access for up to 30 minutes (`expiry-seconds: 1800`). Refresh is correctly blocked.

### S-07 Medium: Unsafe defaults

- `application.yml`: `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`. A prod deploy that forgets the variable runs **dev** with SQL logging, Swagger enabled, and the defaults below.
- `application-dev.yml` defaults: DB password `0001`, JWT secret `vidya-dev-secret-key-change-in-production-min-256-bits`, provisioning superuser `postgres/0001`, test payment keys.
- `JwtTokenProvider` also embeds a fallback secret for dev/test.

**Fix:** no default profile (fail fast), no secret defaults outside `application-local.yml` (git-ignored), and a startup guard that refuses known dev secrets when the `prod` profile is active.

### S-08 Medium: CORS wildcard fallback

`SecurityConfig.corsConfigurationSource`: `setAllowedOriginPatterns(origins.isEmpty() ? List.of("*") : origins)` together with `setAllowCredentials(true)`. If `CORS_ALLOWED_ORIGINS` is set but empty in prod, every origin is allowed with credentials. Bearer tokens aren't sent automatically by browsers, which limits the impact, but the fallback should be "deny" rather than `*`.

### S-09 Medium: Hardcoded, coarse role model

`PlatformRole` is a Java enum of 15 roles, each mapped to a static `EnumSet<PlatformPermission>` (154 permissions). Consequences:

- Schools cannot define roles (Librarian, Transport Manager, HOD, Receptionist and so on) without a code release.
- `canAccessTenant` admits only `SCHOOL_OWNER`, `SCHOOL_ADMIN`, `TEACHER` and community roles. **`PRINCIPAL`, `COORDINATOR`, `SUBJECT_TEACHER`, `ACCOUNTS`, `PARENT`, `STUDENT` are denied tenant access entirely** (`TenantAuthorizationServiceImpl.java:582-615`).
- One role per user. A teacher who is also a class teacher and an HOD cannot be modelled.
- Communities already have a configurable role and permission system (`community_role_definition`, `community_permission`). Schools need the equivalent.

### S-10 Medium: No business audit trail

`audit_log` (master) records security events only. Tenant DBs have no audit table, and JPA auditing isn't enabled for the tenant EMF (DATABASE_ANALYSIS DB-12). Changes to marks, attendance corrections, student status changes and mark unlocks leave no who/when/before/after record. That's needed for result integrity, disputes and board inspections.

### S-11 Low: Client-writable login history

`POST /api/v1/communities/{communityId}/details/login-history` and `/logout` let a client fabricate login records. Login history should be written only by `AuthService`.

### S-12 Low: Double filter registration

`JwtAuthenticationFilter` and `TenantResolutionFilter` are `@Component` `OncePerRequestFilter`s, so Spring Boot also registers them as plain servlet filters. `OncePerRequestFilter` prevents double execution today, but the behaviour depends on ordering. Add `FilterRegistrationBean#setEnabled(false)` for the JWT filter, and consider moving tenant resolution into the security chain (`addFilterAfter`).

### S-13 Info: Security tests don't test security

All 36 controller test classes use `MockMvcBuilders.standaloneSetup`, which bypasses the security filter chain and method security. There is no `@SpringBootTest` or `@WebMvcTest`. `SecurityTenantIsolationIntegrationTest` and `IdorAndSecurityBoundaryTest` are Mockito unit tests. None of S-02 through S-06 would be caught by the current suite.

---

## What is done well

- BCrypt password hashing. Login lockout (`max-failed-attempts: 5`, 15 minutes).
- Refresh tokens: stored as SHA-256 hashes, rotated on use, reuse detected with family-wide revocation.
- Access and refresh token types are distinguished, and refresh tokens are rejected as bearer tokens.
- JWT secret length enforced (≥256-bit) and required outside dev/test.
- `X-Tenant-ID` treated as untrusted and validated against the user's school and the tenant's status. Access denials are audited.
- Tenant provisioning SQL validates identifiers against a safe-name regex and escapes the password literal.
- Security headers: CSP, HSTS, `frame-ancestors 'none'`, X-Frame-Options DENY.
- Swagger and API docs are disabled in the prod profile.

## Required security test suite (to add in P0)

1. `@SpringBootTest` + Testcontainers (Postgres, Redis) + real JWTs, running against the real filter chain.
2. A matrix test: for every endpoint × every role, assert the expected status. Generate it from the endpoint inventory so new endpoints fail until they're classified.
3. Cross-tenant: user of school A sends `X-Tenant-ID` for school B on every tenant endpoint and gets 403.
4. Cross-school IDOR on master: a school-A admin requests `/schools/{B}` and `/payments/school/{B}` and gets 403.
5. Webhook forgery: missing or bad signature gets 4xx with no state change.
6. Suspended-user token gets 401 on all routes.
