# Architecture Overview (as implemented)

Revision `9c6d509`. Describes what the code does, not what earlier documents intended.

## 1. Style

A **modular monolith**, one deployable (`VidyaApplication`), with package-by-feature modules. Each feature module follows the same layering:

```
controller → service (interface + Impl) → repository (Spring Data JPA) → entity
                 ↑ dto (request/response) + mapper (hand-written)
```

Interfaces exist for almost every service, each with a single implementation. That's harmless, but it's ceremony: it doubles the file count without enabling polymorphism. Only the payment gateways (`PaymentGatewayProvider`: Razorpay, Cashfree, Manual) and `CredentialResolver` have real alternative implementations.

## 2. Package map

```
com.vidya
├── VidyaApplication              excludes Mongo auto-config
├── common/       api (ApiResponse, PagedApiResponse, ApiResponseCode), exception (+GlobalExceptionHandler),
│                 correlation (CorrelationIdFilter, MDC keys), util (CodeGenerator, SecurityUtils),
│                 audit (AuditableEntity base), config (OpenAPI)
├── config/       database (Master/Tenant DataSource+EMF+TM), flyway, redis, jackson, datetime (Clock)
├── security/     SecurityConfig, JWT filter/provider, AuthController, PlatformUser/Role/Permission enums,
│                 TenantAuthorizationService, SecurityAuditService, SuperUserDataInitializer
├── tenant/       context (ThreadLocal), resolver (TenantResolutionFilter), datasource (routing, factory,
│                 manager cache, credential resolver), registry (Redis-cached), provisioning (create DB,
│                 migrate, init)
├── master/       [master DB] school, onboarding, workflow, subscription, payment, community, hub,
│                 support, analytics, audit, tenant, provisioning
├── tenantdata/   [tenant DB] academic, student, staff, user, subject, assignment, timetable, calendar,
│                 attendance, examination, reportcard
└── web/          WebPageController (Thymeleaf landing + /schools/{slug} page, robots, sitemap, llms.txt)
```

Module sizes (production LOC): timetable 7.9k, student 4.7k, community 4.1k, assignment 3.7k, school 3.6k, security 3.3k, examination 2.9k, academic 2.9k, attendance 2.6k, subscription 2.6k, everything else under 2k.

## 3. Request lifecycle

```
HTTP
 │
 ├─ CorrelationIdFilter            sets X-Correlation-ID + MDC
 ├─ Spring Security chain (order -100)
 │    ├─ CORS, security headers (CSP, HSTS, frame deny)
 │    ├─ JwtAuthenticationFilter   Bearer → validate → reject REFRESH type → loadUserByUsername(email)
 │    │                            → PlatformUserDetails(role + permissions from enum) in SecurityContext
 │    │                            (does NOT check enabled/locked; see SECURITY_AUDIT S-06)
 │    └─ authorizeHttpRequests     permitAll list, else authenticated()
 ├─ TenantResolutionFilter (@Order(3), servlet filter)
 │    ├─ skip if path starts with a master prefix (/api/v1/schools, /payments, /communities, …)
 │    ├─ tenantCode = X-Tenant-ID header ▸ else user's tenantCode ▸ else lookup by user.schoolId
 │    ├─ registry lookup (Redis cache → master DB), reject suspended or inactive
 │    ├─ TenantAuthorizationService.canAccessTenant(userId, tenantCode)   [2–4 master queries/request]
 │    └─ TenantContext.set(...) … finally clear()
 ├─ DispatcherServlet → @PreAuthorize (method security) → Controller
 ├─ Service @Transactional("tenantTransactionManager" | "masterTransactionManager")
 │    └─ TenantRoutingDataSource.determineTargetDataSource()
 │         → TenantDataSourceManager.getDataSource(tenantCode)   (cached Hikari pool per tenant,
 │           max 200/500, idle TTL); throws IllegalStateException if no tenant context
 └─ ApiResponse / PagedApiResponse envelope; errors via GlobalExceptionHandler
```

### Authorization layers actually present

| Layer | Master endpoints | Tenant endpoints |
|---|---|---|
| Authenticated | yes | yes |
| Tenant membership (`canAccessTenant`) | n/a | yes, in the filter |
| Role/permission (`@PreAuthorize`) | **no** (15 controllers, 136 endpoints) | yes (every endpoint), always in the form `hasAuthority('X') or hasRole(...)` |
| Ownership / row scope (own school, own section, own child) | **no** | **no** (teacher scope stubbed, `StudentAttendanceController#extractStaffId` returns `null`) |

## 4. Multi-tenancy

- **Isolation:** physical, one PostgreSQL database per school, created at runtime by `TenantDatabaseCreator` (`CREATE USER` / `CREATE DATABASE` / `GRANT`, names validated against a safe regex), then migrated by `TenantMigrationService` (Flyway `db/migration/tenant`).
- **Credentials:** `tenant.database_username_ref` / `database_password_ref` hold *names of environment variables*, resolved by `EnvironmentCredentialResolver` through `System.getenv`. **Every new school therefore needs new environment variables on every app node, followed by a restart, before provisioning succeeds.** That makes self-service onboarding impossible operationally (see TECHNICAL_DEBT TD-OPS-1).
- **Routing:** `AbstractRoutingDataSource` subclass, keyed by `TenantContext` (ThreadLocal). Pools are created lazily and evicted by TTL/size.
- **Migrations on upgrade: none.** `TenantMigrationService` is called only from `TenantProvisioningService`. No startup runner or admin endpoint migrates *existing* tenant databases. A school provisioned at tenant V12 keeps the V12 schema forever, and code that touches V13+ tables (timetable hardening, calendar, exams) fails at runtime for that school. This needs fixing before the first production deploy that adds a tenant migration (see SECURITY_AUDIT / roadmap P0-7).
- **Cross-DB consistency:** master `platform_user.id` ↔ tenant `tenant_user.master_user_id` has no FK (and can't have one), and no sync job exists. Creating a staff member does not create a login, and vice versa.

## 5. Transactions

- Two `JpaTransactionManager`s. Master is `@Primary`, so an unqualified `@Transactional` on tenant code would silently bind to master. The current code qualifies every tenant service.
- `SchoolSupportQueryServiceImpl` and `OnboardingOrchestrationServiceImpl` have **no `@Transactional`**. Multi-save operations there are not atomic. Orchestration spans master and tenant DBs, so it needs an explicit saga/compensation design, not a single transaction.
- No distributed transaction. "Never mix master and tenant in one transaction" is a stated invariant and appears to be respected.

## 6. Cross-cutting

| Concern | Implementation | Gap |
|---|---|---|
| Error handling | `GlobalExceptionHandler` maps domain exceptions → 400/401/403/404/409/422/500 | Good |
| Validation | Jakarta Validation on request DTOs | Coverage not audited per-DTO |
| Logging | Pattern includes `correlationId`, `tenantId`; dev logs SQL | No JSON/structured logging for prod |
| Metrics/health | Actuator `health,info,metrics` | No Prometheus registry, no tracing |
| Caching | Redis for tenant registry only | Fine for now |
| Events | One `@EventListener` (payment → onboarding workflow) | Synchronous, in-transaction. No outbox, so a listener failure rolls back or loses events. |
| Jobs | none | Needed for fee reminders, overdue fines, notifications, report generation |
| Files | none | Needed for documents, certificates, admissions |
| Time | `ClockConfig` (injectable `Clock`), school timezone `Asia/Kolkata` global property | Timezone should be per-school (tenant config), not global |

## 7. Architectural risks to resolve before scaling features

1. **Identity lives only in master, with a single enum role.** Parents, students and fine-grained staff roles cannot be modelled (see SECURITY_AUDIT S-04, S-09). This is the most consequential design decision to revisit.
2. **No campus dimension in tenant data.** Multi-campus schools would need either one tenant per campus (losing cross-campus reporting) or a `campus_id` added to tenant tables early, before data accumulates.
3. **Organization / school-group model is missing.** `community` and `hub` are sales structures. A trust running 5 schools has no first-class representation, and cross-school reporting would mean querying N databases.
4. **Per-tenant env-var credentials** do not scale past a handful of schools.
