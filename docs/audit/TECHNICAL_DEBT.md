# Technical Debt Register

Ordered by impact within each category. "Effort": S < 1 day, M 1–3 days, L 1–2 weeks.

## Build and tests

| ID | Debt | Impact | Effort |
|---|---|---|---|
| TD-T1 | Tenant migration V18 references `class_section`, so the build fails. All 9 Testcontainers classes error in `@BeforeAll`. | Build red. Real-DB behaviour untested. Provisioning broken. | S |
| TD-T2 | No `@SpringBootTest` / `@WebMvcTest` anywhere. 36 controller tests use `standaloneSetup` (no security, no filters, no exception-handler wiring parity). | Nothing proves the context boots or that authorization works | M |
| TD-T3 | "Integration" tests that are pure Mockito (`SecurityTenantIsolationIntegrationTest`). | False confidence | S (rename) / M (replace) |
| TD-T4 | No CI pipeline, so test-count claims in docs drifted (983 / 724 vs 1,072 run, 9 errors). | Regressions go unnoticed | S |
| TD-T5 | No architecture tests (ArchUnit): e.g. "every handler has `@PreAuthorize`", "tenantdata services use the tenant TM", "controllers don't return `@Entity`". | Invariants enforced only by convention, already violated | S |
| TD-T6 | Asciidoctor / REST Docs plugin configured with no asciidoc sources | Dead build step | S |

## Dependencies and configuration

| ID | Debt | Impact | Effort |
|---|---|---|---|
| TD-D1 | `spring-boot-starter-data-mongodb` (+ test starter) included but auto-config excluded; nothing uses it | Bloat, confusion, CVE surface | S |
| TD-D2 | `spring-boot-starter-session-data-redis` with a STATELESS security policy | Redundant. Session config is misleading. | S |
| TD-D3 | `spring-boot-starter-mail` unused | Keep only if the notifications module uses it soon | S |
| TD-D4 | `thymeleaf-extras-springsecurity6` on Boot 4 / Security 7 | Possible incompatibility. Verify, or drop server-side pages. | S |
| TD-D5 | Default active profile `dev`; secret defaults in YAML (S-07) | Unsafe deploys | S |
| TD-D6 | School timezone is a global property (`vidya.school.timezone`) | Wrong for multi-region tenants | M |
| TD-D7 | Empty POM metadata (`<name/>`, `<licenses><license/>`, etc.) | Cosmetic | S |

## Architecture

| ID | Debt | Impact | Effort |
|---|---|---|---|
| TD-A1 | Identity only in master, single enum role per user, no login↔staff/student/guardian resolver | Blocks row-level security, parent/student portal and configurable roles | L |
| TD-A2 | Role/permission matrix hardcoded in `PlatformRole` enum; `@PreAuthorize` strings list roles by hand on every method | Not configurable per school. Easy to get wrong. | M–L |
| TD-A3 | Existing tenant DBs never migrated (DB-13) | Every tenant schema change breaks existing schools | M |
| TD-A4 | Tenant DB credentials via per-tenant env vars | New school needs ops action plus restart | M |
| TD-A5 | Two enrollment models (`student_academic_assignment` vs `student_academic_enrollment`) | Ambiguous source of truth | M |
| TD-A6 | Orchestration spanning master and tenant with no saga or compensation; one synchronous `@EventListener` | Partial failures leave inconsistent state | M |
| TD-A7 | No outbox, job scheduler or async infrastructure | Needed for notifications, reminders, fines and report generation | M |
| TD-A8 | No file-storage abstraction | Needed for admissions, documents, certificates and report-card PDFs | M |
| TD-A9 | Interface + single Impl for nearly every service | Ceremony only. Not worth churning. Stop adding new ones unless needed. | — |
| TD-A10 | Duplicate route prefixes (`/api/v1/tenant/x` and `/api/v1/x`) | Surface area. Client confusion. | S (deprecate) |

## Code-level

| ID | Debt | Location | Effort |
|---|---|---|---|
| TD-C1 | Entities returned from controllers | `CommunityDetailController` | S |
| TD-C2 | Unbounded `findAll()` for analytics | `AnalyticsServiceImpl:156,185` | S |
| TD-C3 | Missing `@Transactional` on multi-write services | `SchoolSupportQueryServiceImpl`, `OnboardingOrchestrationServiceImpl` | S |
| TD-C4 | Tenant JPA auditing not enabled; `created_by` set by hand in some services only | `TenantDataSourceConfig` | S |
| TD-C5 | Each of `CustomAccessDeniedHandler` and `CustomAuthenticationEntryPoint` builds its own `ObjectMapper` rather than injecting the configured one | `security/config` | S |
| TD-C6 | Payment gateways special-case literal test secrets (`"rzp_test_webhook_secret"`) in production code paths | `master/payment/gateway/*` | S |
| TD-C7 | Teacher-scope placeholder returning `null` | `StudentAttendanceController#extractStaffId` | part of TD-A1 |
| TD-C8 | Per-request master DB lookups for tenant authorization (2–4 queries) | `TenantAuthorizationServiceImpl` | S–M |

## Documentation

| ID | Debt | Effort |
|---|---|---|
| TD-X1 | Root `PROJECT_OVERVIEW.md`, `README.md` and `docs/ARCHITECTURE.md` carry stale test counts, migration versions and class names. The overview contains a duplicated, malformed Gantt block. | S |
| TD-X2 | `docs/frontend-api/*` document endpoints without their real authorization requirements | S (regenerate from OpenAPI) |
