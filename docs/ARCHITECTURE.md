# Vidya — Comprehensive Software Architecture Document

> **⚠️ The repository implementation takes precedence over any stale prose in this document. Always verify against source code before modifying architectural components.**

---

## 1. Document Control and Architecture Status

### 1.1 Purpose and Audience

This document is the primary technical reference for AI coding agents and human developers working on the Vidya codebase. It describes the system's structure, architectural decisions, execution flows, tenant isolation, database design, caching, security, module boundaries, migrations, transactions, testing, deployment, and engineering conventions.

### 1.2 Scope

- Complete multi-tenant architecture (master + tenant databases).
- Security, authentication, and authorization.
- All implemented domain modules through Step 24H+ (including Calendar and Attendance Override).
- Build, test, and deployment architecture.

**Exclusions**: Future roadmap modules (Steps 25–34) are documented as **Planned** only.

### 1.3 Metadata

| Field | Value |
|---|---|
| Last verified date | 2026-10-17 |
| Source-code revision | `2b915c1d3155baa8579d72dee6c204bc2e668ce3` |
| Architecture doc version | 2.0.0 |
| Source files inspected | 535 production, 130 test |
| Claimed test count | 983 (per PROJECT_OVERVIEW.md) — **Documented** |

### 1.4 Evidence Classification Legend

| Label | Meaning |
|---|---|
| **Implemented** | Verified in source code |
| **Tested** | Demonstrated by automated tests |
| **Configured** | Specified in configuration but not necessarily exercised |
| **Documented** | Claimed in an existing document but not independently verified |
| **Planned** | Intended but not implemented |
| **Unverified** | Evidence is insufficient |
| **Known limitation** | A confirmed restriction or gap |

### 1.5 How to Update This Document

1. Make the code change first.
2. Verify the change with tests.
3. Update relevant sections of this file.
4. Increment the architecture doc version.
5. Add an entry to Section 32 (Change Log).

### 1.6 Known Documentation Gaps

- No CI/CD pipeline definition files found in repository. Deployment procedures are **Unverified**.
- No Dockerfile or container orchestration files found. Containerization is **Planned**.
- Redis session storage configured via `spring.session.store-type: redis` — session behavior at scale is **Unverified**.
- Performance benchmarks cited in PROJECT_OVERVIEW.md are **Documented** not independently verified.

---

## 2. Table of Contents

<!-- TOC -->
- [1. Document Control and Architecture Status](#1-document-control-and-architecture-status)
- [2. Table of Contents](#2-table-of-contents)
- [3. System Overview](#3-system-overview)
- [4. Architecture Principles and Invariants](#4-architecture-principles-and-invariants)
- [5. Technology Stack and Dependency Inventory](#5-technology-stack-and-dependency-inventory)
- [6. Repository Structure and Package Map](#6-repository-structure-and-package-map)
- [7. System Context and Runtime Components](#7-system-context-and-runtime-components)
- [8. Application Layering and Request Lifecycle](#8-application-layering-and-request-lifecycle)
- [9. Modular Monolith and Module Boundaries](#9-modular-monolith-and-module-boundaries)
- [10. Multi-Tenant Architecture](#10-multi-tenant-architecture)
- [11. Database Architecture](#11-database-architecture)
- [12. Caching Architecture](#12-caching-architecture)
- [13. Authentication, Authorization, and Security](#13-authentication-authorization-and-security)
- [14. API and Integration Architecture](#14-api-and-integration-architecture)
- [15. Domain Module Architecture](#15-domain-module-architecture)
- [16. Background Jobs, Events, and Asynchronous Processing](#16-background-jobs-events-and-asynchronous-processing)
- [17. Error Handling and Observability](#17-error-handling-and-observability)
- [18. Configuration and Environment Management](#18-configuration-and-environment-management)
- [19. Timezone, Dates, and Scheduling](#19-timezone-dates-and-scheduling)
- [20. Performance and Scalability](#20-performance-and-scalability)
- [21. Testing Architecture](#21-testing-architecture)
- [22. Build, Run, and Deployment Architecture](#22-build-run-and-deployment-architecture)
- [23. Failure Modes and Recovery](#23-failure-modes-and-recovery)
- [24. Known Limitations and Technical Debt](#24-known-limitations-and-technical-debt)
- [25. Architecture Decision Records](#25-architecture-decision-records)
- [26. Developer Guide: How to Extend Vidya Safely](#26-developer-guide-how-to-extend-vidya-safely)
- [27. AI Coding Agent Operating Rules](#27-ai-coding-agent-operating-rules)
- [28. Architecture Diagrams](#28-architecture-diagrams)
- [29. Architecture Glossary](#29-architecture-glossary)
- [30. Source Reference Index](#30-source-reference-index)
- [31. Architecture Verification Checklist](#31-architecture-verification-checklist)
- [32. Change Log](#32-change-log)
<!-- /TOC -->

---

## 3. System Overview

### 3.1 Purpose and System Boundaries

Vidya is an enterprise-grade, multi-tenant SaaS School Management Platform. It automates academic, administrative, and financial operations for K-12 schools, multi-branch educational institutions, and school chains. **[Implemented]**

### 3.2 Core Domain Concepts

| Concept | Description | Database |
|---|---|---|
| Platform | Central SaaS operations: users, communities, schools, onboarding, subscriptions, billing | Master DB |
| Tenant / School | A single school instance with complete data isolation | Dedicated Tenant DB |
| Academic Structure | Academic years, classes, sections, subjects, curricula | Tenant DB |
| Student Management | Student records, enrollment, academic assignments, guardians | Tenant DB |
| Staff Management | Staff records, employment, departments, designations | Tenant DB |
| Scheduling | Timetables, bell schedules, rooms, substitutions | Tenant DB |
| Attendance | Daily student attendance with bulk upsert | Tenant DB |
| Calendar | Working day configuration, school holidays | Tenant DB |

### 3.3 Platform vs School Responsibilities

- **Platform-level** (Master DB): Identity management, multi-school communities, onboarding workflows, subscription plans/features, billing/payments, tenant provisioning, platform audit logs.
- **School-level** (Tenant DB): All academic and operational data — students, staff, academics, attendance, timetables, calendar.

### 3.4 High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Web / Mobile / API Clients                       │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ HTTPS + JWT + X-Tenant-ID
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│               Spring Boot 4.1.1 Modular Monolith                     │
│  ┌──────────────┐  ┌──────────────────┐  ┌────────────────────┐     │
│  │CorrelationId │→│ JwtAuthentication │→│ TenantResolution   │     │
│  │ Filter (O:1) │  │  Filter (O:2)    │  │  Filter (O:3)     │     │
│  └──────────────┘  └──────────────────┘  └────────────────────┘     │
│                            │                                         │
│                    ┌───────┴────────┐                                │
│                    ▼                ▼                                │
│          ┌─────────────┐   ┌──────────────┐                         │
│          │   Master    │   │   Tenant     │                         │
│          │  Services   │   │  Services    │                         │
│          └──────┬──────┘   └──────┬───────┘                         │
│                 │                  │                                  │
│     masterTxMgr │       tenantTxMgr│                                │
│                 ▼                  ▼                                  │
│         ┌────────────┐   ┌──────────────────┐                       │
│         │ Master DS  │   │TenantRoutingDS   │──→ TenantDS Cache     │
│         │ (HikariCP) │   │(per-tenant pools)│                       │
│         └─────┬──────┘   └──┬──────┬────┬───┘                       │
│               │             │      │    │                            │
└───────────────┼─────────────┼──────┼────┼───────────────────────────┘
                ▼             ▼      ▼    ▼
          ┌──────────┐  ┌─────┐ ┌─────┐ ┌─────┐     ┌───────────┐
          │  Master  │  │ T-1 │ │ T-2 │ │ T-N │     │   Redis   │
          │PostgreSQL│  │ DB  │ │ DB  │ │ DB  │     │(Cache+Ses)│
          └──────────┘  └─────┘ └─────┘ └─────┘     └───────────┘
```

### 3.5 Implemented vs Planned Capabilities

| Capability | Status | Evidence |
|---|---|---|
| Platform RBAC & Identity | **Implemented** | `com.vidya.security.*`, V1–V20 master migrations |
| Multi-school Communities | **Implemented** | `com.vidya.master.community.*` |
| School & Branch Directory | **Implemented** | `com.vidya.master.school.*` |
| Tenant Provisioning Engine | **Implemented** | `com.vidya.tenant.provisioning.*` |
| Multi-step Onboarding | **Implemented** | `com.vidya.master.onboarding.*`, `com.vidya.master.workflow.*` |
| SaaS Subscriptions & Features | **Implemented** | `com.vidya.master.subscription.*` |
| Platform Payments & Billing | **Implemented** | `com.vidya.master.payment.*` |
| Platform Audit & Compliance | **Implemented** | `com.vidya.master.audit.*` |
| Tenant Users & Profiles | **Implemented** | `com.vidya.tenantdata.user.*` |
| Academic Structure | **Implemented** | `com.vidya.tenantdata.academic.*` |
| Student Management & Enrollment | **Implemented** | `com.vidya.tenantdata.student.*` |
| Staff & Teacher Management | **Implemented** | `com.vidya.tenantdata.staff.*` |
| Subjects & Curricula | **Implemented** | `com.vidya.tenantdata.subject.*` |
| Teacher & Class Teacher Assignments | **Implemented** | `com.vidya.tenantdata.assignment.*` |
| Student Attendance & Bulk Upsert | **Implemented** | `com.vidya.tenantdata.attendance.*` |
| Timetable & Scheduling Engine | **Implemented** | `com.vidya.tenantdata.timetable.*` |
| Academic Calendar & Holidays | **Implemented** | `com.vidya.tenantdata.calendar.*` |
| Attendance Non-Working Day Override | **Implemented** | V16 tenant migration |
| Examination & Assessment | **Implemented** | `com.vidya.tenantdata.examination.*` (V17, Step 25) |
| Grading & Report Cards | **Planned** | Step 26 |
| Fee Structure & Invoicing | **Planned** | Step 27 |
| Fee Collection & Online Pay | **Planned** | Step 28 |
| Parent & Student Portal | **Planned** | Step 31 |

---

## 4. Architecture Principles and Invariants

### 4.1 Enforced Invariants

| # | Invariant | Enforcement | Evidence |
|---|---|---|---|
| I-1 | **Modular monolith, not microservices** | Single deployable artifact | `pom.xml` — single module |
| I-2 | **Database-per-tenant physical isolation** | Each school gets a dedicated PostgreSQL DB | `TenantDatabaseCreator`, `TenantProvisioningService` |
| I-3 | **Explicit transaction manager selection** | Every `@Transactional` must specify `masterTransactionManager` or `tenantTransactionManager` | `MasterDataSourceConfig`, `TenantDataSourceConfig` |
| I-4 | **No master–tenant cross-database transactions** | Each transaction manager targets exactly one DataSource | Separate `EntityManagerFactory` beans |
| I-5 | **Tenant context must be cleared after every request** | `TenantResolutionFilter.finally` block | `TenantResolutionFilter` |
| I-6 | **ThreadLocal, not InheritableThreadLocal** | Prevents context leakage in pooled threads | `TenantContext` |
| I-7 | **X-Tenant-ID is untrusted** | Validated against registry and authorization service | `TenantResolutionFilter` |
| I-8 | **UUID primary keys** | `@GeneratedValue(strategy = GenerationType.UUID)` | `AuditableEntity`, all entities |
| I-9 | **Optimistic locking on all entities** | `@Version` column | `AuditableEntity` |
| I-10 | **Soft deletion via `deleted_at`** | Partial unique indexes `WHERE deleted_at IS NULL` | V12, V13, V14 tenant migrations |
| I-11 | **Flyway manages all DDL** | `hibernate.hbm2ddl.auto = validate` (master) / `none` (tenant) | `MasterDataSourceConfig`, `TenantDataSourceConfig` |
| I-12 | **open-in-view disabled** | `spring.jpa.open-in-view: false` | `application.yml` |
| I-13 | **Fail-fast JWT secret in production** | Startup fails if `vidya.security.jwt.secret` is empty/missing and profile is not dev/test | `JwtTokenProvider` |
| I-14 | **Flyway clean disabled in all environments** | `cleanDisabled(true)` | `MasterFlywayConfig`, `TenantMigrationService` |

### 4.2 Architectural Intentions (Not Enforced by Tooling)

- Module dependency direction (no compile-time enforcement; convention only).
- SQL statement count O(1) invariants (enforced in specific integration tests, not globally).
- 100% test suite green policy (social contract, no CI gate verified).

---

## 5. Technology Stack and Dependency Inventory

| Component | Technology | Version | Source |
|---|---|---|---|
| Language | Java | 25 | `pom.xml` — **Implemented** |
| Framework | Spring Boot | 4.1.1 | `pom.xml` — **Implemented** |
| Persistence | Spring Data JPA / Hibernate | Managed by Spring Boot 4.1.1 | `spring-boot-starter-data-jpa` |
| Database | PostgreSQL | 16+ (runtime) | `org.postgresql:postgresql` (runtime) |
| JDBC Driver | PostgreSQL JDBC | Managed by Spring Boot | `pom.xml` |
| Connection Pool | HikariCP | Managed by Spring Boot | Explicit config in `MasterDataSourceConfig`, `TenantDataSourceFactory` |
| Migrations | Flyway | Managed by Spring Boot | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| Redis Client | Lettuce | Managed by Spring Boot | `spring-boot-starter-session-data-redis`, `RedisConfig` |
| Session Store | Spring Session (Redis) | Managed by Spring Boot | `spring.session.store-type: redis` |
| Security | Spring Security | Managed by Spring Boot | `spring-boot-starter-security` |
| JWT | JJWT (io.jsonwebtoken) | 0.12.6 | `pom.xml` |
| Validation | Jakarta Bean Validation / Hibernate Validator | Managed by Spring Boot | `spring-boot-starter-validation` |
| API Docs | SpringDoc OpenAPI | 3.1.0 | `pom.xml` |
| Email | Spring Boot Mail | Managed by Spring Boot | `spring-boot-starter-mail` — **Configured** |
| Template Engine | Thymeleaf | Managed by Spring Boot | `spring-boot-starter-thymeleaf` — used for web pages |
| Boilerplate | Lombok | 1.18.46 | `pom.xml`, annotation processor |
| Monitoring | Spring Boot Actuator | Managed by Spring Boot | `spring-boot-starter-actuator` |
| REST Docs | Spring REST Docs / Asciidoctor | Managed by Spring Boot | test scope |
| Testing | JUnit 5, Mockito, MockMvc | Managed by Spring Boot | test starters |
| Integration Testing | Testcontainers | 1.20.4 | `pom.xml` |
| Build Tool | Maven | Via Maven Wrapper (`mvnw`) | `.mvn/` directory |

---

## 6. Repository Structure and Package Map

### 6.1 Directory Tree

```
Vidya-Backend-Project/
├── pom.xml                          # Maven build descriptor
├── mvnw / mvnw.cmd                  # Maven wrapper
├── ARCHITECTURE.md                  # This document
├── PROJECT_OVERVIEW.md              # Product overview & roadmap
├── README.md                        # Quick-start readme
├── docs/                            # Additional docs (PRD, existing arch)
├── src/
│   ├── main/
│   │   ├── java/com/vidya/
│   │   │   ├── VidyaApplication.java       # Entry point
│   │   │   ├── common/                     # Cross-cutting concerns
│   │   │   │   ├── api/                    # ApiResponse, PagedApiResponse, ApiResponseCode
│   │   │   │   ├── audit/                  # AuditableEntity base class
│   │   │   │   ├── config/                 # OpenApiConfig
│   │   │   │   ├── correlation/            # CorrelationIdFilter, MdcKeys
│   │   │   │   ├── exception/              # GlobalExceptionHandler, domain exceptions
│   │   │   │   └── util/                   # CodeGenerator, SecurityUtils
│   │   │   ├── config/                     # Infrastructure configuration
│   │   │   │   ├── database/               # MasterDataSourceConfig, TenantDataSourceConfig
│   │   │   │   ├── datetime/               # ClockConfig
│   │   │   │   ├── flyway/                 # MasterFlywayConfig, TenantFlywayConfig
│   │   │   │   ├── jackson/                # JacksonConfig
│   │   │   │   └── redis/                  # RedisConfig
│   │   │   ├── master/                     # Master Platform Domain
│   │   │   │   ├── audit/                  # Platform audit logs
│   │   │   │   ├── community/              # Communities & members
│   │   │   │   ├── onboarding/             # School onboarding pipeline
│   │   │   │   ├── payment/                # Platform payments
│   │   │   │   ├── provisioning/           # Provisioning entity & repository
│   │   │   │   ├── school/                 # Schools & branches
│   │   │   │   ├── subscription/           # Plans, features, school subscriptions
│   │   │   │   ├── tenant/                 # Tenant entity & repository
│   │   │   │   └── workflow/               # Onboarding workflow orchestration
│   │   │   ├── security/                   # Authentication & authorization
│   │   │   │   ├── config/                 # SecurityConfig, entry points
│   │   │   │   ├── controller/             # AuthController
│   │   │   │   ├── dto/                    # Auth DTOs
│   │   │   │   ├── entity/                 # PlatformUser, PlatformRole, PlatformPermission
│   │   │   │   ├── jwt/                    # JwtTokenProvider, JwtAuthenticationFilter
│   │   │   │   ├── repository/             # PlatformUserRepository, RefreshTokenRepository
│   │   │   │   ├── service/                # AuthService, TenantAuthorizationService
│   │   │   │   └── user/                   # PlatformUserDetails
│   │   │   ├── tenant/                     # Tenant infrastructure
│   │   │   │   ├── context/                # TenantContext (ThreadLocal)
│   │   │   │   ├── datasource/             # TenantRoutingDataSource, Manager, Factory
│   │   │   │   ├── provisioning/           # Provisioning orchestration
│   │   │   │   ├── registry/               # TenantRegistryService, TenantRegistration
│   │   │   │   └── resolver/               # TenantResolutionFilter
│   │   │   ├── tenantdata/                 # Tenant Domain Modules
│   │   │   │   ├── academic/               # AcademicYear, AcademicClass, Section
│   │   │   │   ├── assignment/             # Teacher/Class assignments
│   │   │   │   ├── attendance/             # StudentAttendance, bulk upsert
│   │   │   │   ├── calendar/               # WorkingDayConfig, SchoolHoliday
│   │   │   │   ├── staff/                  # Staff, StaffDepartment
│   │   │   │   ├── student/                # Student, enrollment, guardians
│   │   │   │   ├── subject/                # Subject, Curriculum, CurriculumSubject
│   │   │   │   ├── timetable/              # Room, BellSchedule, Timetable, Entries
│   │   │   │   └── user/                   # TenantUser, TenantUserProfile
│   │   │   └── web/                        # WebPageController (Thymeleaf)
│   │   └── resources/
│   │       ├── application.yml             # Base config
│   │       ├── application-dev.yml         # Dev profile
│   │       ├── application-prod.yml        # Production profile
│   │       ├── db/migration/
│   │       │   ├── master/                 # V1–V20 master migrations
│   │       │   └── tenant/                 # V1–V16 tenant migrations
│   │       ├── static/                     # Static web assets
│   │       └── templates/                  # Thymeleaf templates
│   └── test/
│       └── java/com/vidya/
│           ├── integration/                # Testcontainers PostgreSQL integration tests
│           ├── master/                     # Master domain tests
│           ├── security/                   # Security & tenant isolation tests
│           └── tenantdata/                 # Tenant domain tests
```

### 6.2 Module Responsibility Table

| Module / Package | Responsibility | Depends On | Must Not Depend On |
|---|---|---|---|
| `com.vidya.common` | API envelopes, exceptions, utilities, audit base | None (self-contained) | Any domain module |
| `com.vidya.config` | DataSource, Flyway, Redis, Clock, Jackson config | `com.vidya.tenant.datasource` | Domain modules |
| `com.vidya.security` | Authentication, JWT, RBAC, platform users | `com.vidya.common`, `com.vidya.master` | `com.vidya.tenantdata` |
| `com.vidya.tenant` | Tenant context, routing, provisioning, registry | `com.vidya.common`, `com.vidya.master.tenant` | `com.vidya.tenantdata` |
| `com.vidya.master` | Platform domain (schools, onboarding, subscriptions) | `com.vidya.common`, `com.vidya.security`, `com.vidya.tenant` | `com.vidya.tenantdata` |
| `com.vidya.tenantdata` | School academic/operational domain modules | `com.vidya.common` | `com.vidya.master`, `com.vidya.security` |
| `com.vidya.web` | Thymeleaf web page controller | `com.vidya.common` | Domain services directly |

> **Note**: Dependency direction is enforced by convention only. No ArchUnit or module-info.java enforcement is present. **[Known limitation]**

---

## 7. System Context and Runtime Components

### 7.1 Runtime Components

| Component | Status | Description |
|---|---|---|
| Spring Boot Application | **Implemented** | Single JVM process |
| Master Database (PostgreSQL) | **Implemented** | Platform data |
| Tenant Databases (PostgreSQL) | **Implemented** | One per school |
| Redis | **Implemented** | Tenant registry cache; session store configured |
| Flyway (Master) | **Implemented** | Runs at startup |
| Flyway (Tenant) | **Implemented** | Runs during provisioning |
| Email (SMTP) | **Configured** | Starter present, usage unverified |
| Payment Gateway | **Configured** | Razorpay/Cashfree config keys present |
| Background Job Scheduler | **Not implemented** | No `@Scheduled`, `@Async`, or job framework found |

---

## 8. Application Layering and Request Lifecycle

### 8.1 Filter Chain Order

| Order | Filter | Responsibility |
|---|---|---|
| 1 | `CorrelationIdFilter` | Assigns/propagates `X-Correlation-ID`, sets MDC |
| 2 | `JwtAuthenticationFilter` | Extracts and validates JWT Bearer token, sets SecurityContext |
| 3 | `TenantResolutionFilter` | Resolves `X-Tenant-ID`, validates tenant existence & authorization, sets `TenantContext` |
| — | Spring Security `SecurityFilterChain` | URL-level access control, CORS, CSRF (disabled), session management (STATELESS) |

### 8.2 Request Lifecycle

1. **CorrelationIdFilter** (Order 1): Assigns or propagates `X-Correlation-ID`. Sets `correlationId`, `httpMethod`, `requestUri` in MDC. Clears MDC in `finally`.
2. **JwtAuthenticationFilter** (Order 2): Extracts JWT from `Authorization: Bearer <token>`. Validates token. Rejects refresh tokens used as access tokens. Loads `PlatformUserDetails` and sets `SecurityContextHolder`.
3. **TenantResolutionFilter** (Order 3): Checks if path is tenant-exempt. Extracts `X-Tenant-ID` header. Verifies authentication exists. Calls `TenantRegistryService.findByTenantCode()` (Redis cache → DB fallback). Checks tenant status (`ACTIVE`/`PROVISIONED`). Calls `TenantAuthorizationService.canAccessTenant()`. Sets `TenantContext.setTenantCode()` and MDC keys. Clears in `finally`.
4. **Spring Security FilterChain**: URL-level authorization.
5. **Controller**: `@PreAuthorize` permission check. Delegates to service.
6. **Service**: `@Transactional(tenantTransactionManager)`. JPA repository calls.
7. **TenantRoutingDataSource**: `determineTargetDataSource()` reads `TenantContext.getTenantCode()`. Gets DataSource from `TenantDataSourceManager`.
8. **Response**: `ApiResponse` JSON envelope with correlation ID.

### 8.3 Cross-Cutting Concerns

| Concern | Where Applied | Where NOT Applied |
|---|---|---|
| Correlation ID | All requests (Filter Order 1) | — |
| Tenant Context | Requests with `X-Tenant-ID` to non-exempt paths | Auth, onboarding, subscription, admin, actuator |
| JWT Authentication | All authenticated endpoints | Public endpoints |
| RBAC (@PreAuthorize) | Controller methods | Internal service-to-service calls |
| Transaction Management | Service layer (`@Transactional`) | Filters, controllers |
| JPA Auditing | Entity persistence | — |
| Exception Handling | All controllers via `GlobalExceptionHandler` | Filters (handled inline) |

---

## 9. Modular Monolith and Module Boundaries

### 9.1 Rules for Adding a New Module

1. Decide if data belongs in Master DB or Tenant DB (see Section 11.4).
2. Create package under `com.vidya.master.<module>` or `com.vidya.tenantdata.<module>`.
3. Follow the standard sub-package structure: `entity/`, `repository/`, `dto/`, `mapper/`, `service/`, `controller/`.
4. Never reference `com.vidya.tenantdata` from `com.vidya.master` or vice versa.
5. Add Flyway migration to the appropriate directory.
6. Use the correct transaction manager.

---

## 10. Multi-Tenant Architecture

### 10.1 Tenancy Model

Vidya uses **database-per-tenant** isolation. Each school receives a physically separate PostgreSQL database. This provides: **[Implemented]**

- Complete data isolation at the database level.
- Independent backup and restore per school.
- No risk of query-level cross-tenant data leakage.
- Independent schema migration lifecycle.

### 10.2 Tenant Identity and Resolution

| Step | Mechanism | Source |
|---|---|---|
| 1. Tenant hint | `X-Tenant-ID` request header | Client-supplied, **untrusted** |
| 2. Authentication | JWT Bearer token → `PlatformUserDetails` | `JwtAuthenticationFilter` |
| 3. Registry lookup | `TenantRegistryService.findByTenantCode()` | Redis cache → Master DB fallback |
| 4. Status check | Tenant must be `ACTIVE` or `PROVISIONED` | `TenantRegistration.isAccessible()` |
| 5. Authorization | `TenantAuthorizationService.canAccessTenant(userId, tenantCode)` | Master DB queries |
| 6. Context set | `TenantContext.setTenantCode(tenantCode)` | ThreadLocal |
| 7. MDC set | `tenantId` and `schoolId` in MDC | Structured logging |

**Missing/invalid tenant handling**: **[Implemented]**
- Missing `X-Tenant-ID` header → filter passes through without setting context.
- Tenant not found in registry → `TenantNotFoundException` → HTTP 404.
- Tenant suspended → `TenantSuspendedException` → HTTP 403.
- Tenant not active → `TenantNotFoundException` → HTTP 404.
- User not authorized for tenant → `TenantAccessDeniedException` → HTTP 403.

### 10.3 Tenant Context Lifecycle

**Critical design decisions**: **[Implemented]**
- Uses `ThreadLocal`, **not** `InheritableThreadLocal` — prevents silent leakage to pooled child threads.
- `TenantContext.clear()` is called in a `finally` block, guaranteeing cleanup on both success and failure.
- Async tasks (`@Async`, virtual threads) must explicitly copy tenant context. **[No async usage found — Known limitation]**

### 10.4 Tenant Routing DataSource

**Implementation**: `TenantRoutingDataSource` extends `AbstractRoutingDataSource`. **[Implemented]**

| Aspect | Behavior |
|---|---|
| Routing key | `TenantContext.getTenantCode()` |
| DataSource lookup | Delegates to `TenantDataSourceManager.getDataSource()` |
| Null tenant code | Throws `IllegalStateException` with diagnostic message |
| Cache | `ConcurrentHashMap` in `TenantDataSourceManager` |
| Cache miss | Looks up tenant in registry, builds `HikariDataSource` via `TenantDataSourceFactory` |
| Max cache size | `vidya.tenant.datasource.cache.max-size` (default 200 dev, 500 prod) |
| Eviction | Manual via `TenantDataSourceManager.evict()` — no automatic LRU |
| Default DS fallback | **None** — no default DataSource is set. Missing context = hard failure |

### 10.5 Tenant Authorization

| Role | Tenant Access | Mechanism |
|---|---|---|
| `PLATFORM_SUPER_ADMIN` | All tenants | Unrestricted bypass |
| `PLATFORM_ADMIN` | All non-DEACTIVATED/ARCHIVED tenants | Status check only |
| `SCHOOL_OWNER` | Own school's tenant only | `tenant.schoolId == user.schoolId` |
| `SCHOOL_ADMIN` | Own school's tenant only | `tenant.schoolId == user.schoolId` |
| `TEACHER` | Own school's tenant only | `tenant.schoolId == user.schoolId` |
| `COMMUNITY_ADMIN` | Schools linked via onboarding | `SchoolOnboardingRepository` lookup |
| `COMMUNITY_MANAGER` | Schools linked via onboarding | Same as COMMUNITY_ADMIN |
| `FIELD_AGENT` | Schools linked via onboarding | Same as COMMUNITY_ADMIN |
| `STUDENT` / `PARENT` | **Denied** | Default deny — portal planned |

### 10.6 Tenant Lifecycle

**Provisioning flow** (each step commits independently): **[Implemented]**

1. `REQUESTED` → Create provisioning record in master DB.
2. `DATABASE_CREATING` → `TenantDatabaseCreator` creates PostgreSQL database and user via admin connection. Validates DB/user names against `[a-z][a-z0-9_]{1,62}` pattern. Idempotent checks via `pg_database`/`pg_roles`.
3. `MIGRATION_RUNNING` → `TenantMigrationService` runs Flyway tenant migrations.
4. `INITIALIZATION_RUNNING` → `TenantInitializationService` seeds initial data.
5. `COMPLETED` → Tenant status set to `PROVISIONED`. DataSource cache evicted. Registry cache evicted.

**Retry**: Increments `attempt_count`, resumes from `last_completed_step`. Steps with order ≤ lastCompleted are skipped.

### 10.7 Tenant Isolation Invariants

| # | Invariant | Enforcement |
|---|---|---|
| T-1 | A request must never silently route to another tenant | `TenantRoutingDataSource` throws on null context |
| T-2 | Tenant context must be cleared after use | `TenantResolutionFilter.finally` |
| T-3 | Tenant authorization checked independently of routing | `TenantAuthorizationService` before `TenantContext.setTenantCode()` |
| T-4 | No InheritableThreadLocal for tenant context | Uses plain `ThreadLocal` |
| T-5 | Background tasks must establish own context | No background tasks exist yet — **Gap** |
| T-6 | Tenant cache entries keyed by tenant code | `vidya:tenant:code:{tenantCode}` |

---

## 11. Database Architecture

### 11.1 Database Topology

- **Master Database** (`vidya_master`): Connected via `masterDataSource` (HikariCP). `@Primary`. Scans `com.vidya.master`, `com.vidya.security` entities.
- **Tenant Databases** (`vidya_tenant_*`): Connected via `TenantRoutingDataSource` (per-tenant HikariCP pools). Not primary. Scans `com.vidya.tenantdata` entities.
- **Admin Database** (`postgres`): Used by `TenantDatabaseCreator` for DDL operations during provisioning only.

### 11.2 Master Database Schema

**Migrations**: V1–V20 in `src/main/resources/db/migration/master/` **[Implemented]**

| Migration | Tables / Changes |
|---|---|
| V1 | `platform_user` — Platform identity with RBAC |
| V2 | `community` — Educational trusts/networks |
| V3 | `community_member` — User-community associations |
| V4 | `school` — School entities |
| V5 | `school_branch` — Multi-branch hierarchy |
| V6 | `tenant` — Tenant registry |
| V7 | `tenant_provisioning` — Provisioning state machine |
| V8 | `school_onboarding` — Onboarding pipeline |
| V9 | `onboarding_step_data` — Step-level data |
| V10 | `onboarding_activity` — Activity log |
| V11 | `subscription_plan` — SaaS plan tiers |
| V12 | `subscription_feature` — Feature definitions |
| V13 | `subscription_plan_feature` — Plan-feature mapping |
| V14 | `school_subscription` — School subscriptions |
| V15 | `payment_transaction` — Billing records |
| V16 | `audit_log` — Platform audit |
| V17 | Additional indexes |
| V18 | Seed initial master data |
| V19 | Workflow orchestration enhancements |
| V20 | Security and tenant authorization |

### 11.3 Tenant Database Schema

**Migrations**: V1–V16 in `src/main/resources/db/migration/tenant/` **[Implemented]**

| Migration | Tables / Changes |
|---|---|
| V1 | `tenant_metadata` |
| V2 | `academic_year` |
| V3 | `tenant_user`, `tenant_user_profile` |
| V4 | `student` |
| V5 | `academic_class`, `section` |
| V6 | `staff`, `staff_department` |
| V7 | `subject`, `curriculum`, `curriculum_subject` |
| V8 | `teacher_subject_assignment` |
| V9 | `timetable` (initial foundation) |
| V10 | `student_academic_assignment` |
| V11 | `class_teacher_assignment` |
| V12 | `student_attendance` |
| V13 | `room`, `bell_schedule`, `bell_schedule_period`, `timetable` hardening, `timetable_entry` |
| V14 | `timetable_substitution` |
| V15 | `working_day_config`, `school_holiday` |
| V16 | `student_attendance` — adds `non_working_day_override`, `override_reason` columns |

### 11.4 Data Ownership Decision Table

| Data Category | Database | Rationale |
|---|---|---|
| Platform user identity & credentials | Master | Cross-tenant, single login |
| School directory & branches | Master | Platform-wide visibility |
| Community networks | Master | Cross-school relationships |
| Onboarding workflows | Master | Platform operational data |
| Subscription plans & features | Master | Global SaaS configuration |
| Payment transactions | Master | Platform billing |
| Audit logs (platform) | Master | Cross-tenant compliance |
| Tenant registry & provisioning state | Master | Routing metadata |
| Students, staff, academics | Tenant | School-specific, isolated |
| Attendance, timetables, calendar | Tenant | School-specific, isolated |

**Decision rule**: Data is placed in the tenant DB if it belongs to exactly one school and should be physically isolated. Data goes to the master DB only if it serves platform-level operations or must be visible across schools.

### 11.5 ORM and Persistence Conventions

| Convention | Implementation |
|---|---|
| Primary keys | `UUID` via `@GeneratedValue(strategy = GenerationType.UUID)` |
| Business codes | Immutable human-readable codes via `CodeGenerator` (e.g., `STU-XXXX`, `ATT-XXXX`) |
| Audit fields | `createdAt`, `updatedAt` (Instant), `createdBy`, `updatedBy` (UUID), `version` (Long) |
| Base class | `AuditableEntity` — `@MappedSuperclass` with `@EntityListeners(AuditingEntityListener.class)` |
| Soft deletion | `@Column(name = "deleted_at") private Instant deletedAt` with partial unique indexes |
| Optimistic locking | `@Version` on every entity |
| Constructor | `@NoArgsConstructor(access = AccessLevel.PROTECTED)` for JPA + explicit domain constructor |
| Relationships | Lazy loading by default; `open-in-view: false` enforces boundary |
| Batch inserts | `hibernate.jdbc.batch_size = 20`, `order_inserts = true`, `order_updates = true` |
| Timestamp zone | `hibernate.jdbc.time_zone = UTC` |
| Dialect | `org.hibernate.dialect.PostgreSQLDialect` |

### 11.6 Connection Pools and Resource Limits

| Pool | Config Key | Dev Default | Prod Default |
|---|---|---|---|
| **Master HikariCP** | `vidya.datasource.master.hikari.*` | max=10, min-idle=2 | max=20, min-idle=5 |
| **Tenant HikariCP** (per tenant) | `vidya.tenant.datasource.hikari.*` | max=5, min-idle=1 | max=10, min-idle=2 |
| **Tenant DS cache** | `vidya.tenant.datasource.cache.max-size` | 200 | 500 |
| **Connection timeout** | Both | 30s | 30s |
| **Idle timeout** | Master: 600s, Tenant: 300s | — | — |
| **Max lifetime** | Master: 1800s, Tenant: 900s | — | — |
| **Auto-commit** | Both | `false` | `false` |

**Resource risk**: With 500 tenants × 10 connections = 5,000 potential connections. Monitor PostgreSQL `max_connections`. **[Known limitation — no LRU eviction]**

### 11.7 Transaction Rules

**Critical rule**: A single `@Transactional` method must use exactly one transaction manager. Never mix master and tenant operations in one transaction.

**Provisioning exception**: `TenantProvisioningService.executeProvisioning()` touches both databases sequentially (not atomically). Each step commits independently. The `TenantProvisioning` state machine tracks progress for safe retry. **[Implemented]**

### 11.8 Database Migration Strategy

| Aspect | Master | Tenant |
|---|---|---|
| **Trigger** | Application startup (bean init) | During provisioning (programmatic) |
| **Location** | `classpath:db/migration/master` | `classpath:db/migration/tenant` |
| **Current version** | V20 | V16 |
| **Out-of-order** | Disabled | Disabled |
| **Baseline on migrate** | Disabled | Disabled |
| **Validate on migrate** | Enabled | Enabled |
| **Clean disabled** | Yes (always) | Yes (always) |
| **Schema history table** | `flyway_schema_history` (per DB) | `flyway_schema_history` (per DB) |
| **Existing tenant upgrade** | N/A | **Gap — no automatic upgrade trigger** |

**Checklist for adding a migration**:

1. Determine master or tenant migration.
2. Find the current highest version number in the appropriate directory.
3. Create `V{N+1}__{description}.sql`.
4. Never edit an already-applied migration.
5. Use partial unique indexes for soft-delete columns: `WHERE deleted_at IS NULL`.
6. Include `IF NOT EXISTS` for idempotent DDL where possible.
7. Add corresponding JPA entity changes.
8. Run `mvn test` to verify Hibernate validation passes.

---

## 12. Caching Architecture

### 12.1 Cache Inventory

| Cache | Type | Implementation | Status |
|---|---|---|---|
| Tenant registry | Redis | `TenantRegistryServiceImpl` | **Implemented** |
| DataSource pool cache | In-memory `ConcurrentHashMap` | `TenantDataSourceManager` | **Implemented** |
| HTTP sessions | Redis | `spring.session.store-type: redis` | **Configured** |
| Application-data cache | — | — | **Not implemented** |
| Spring `@Cacheable` | — | — | **Not implemented** |
| Hibernate L2 cache | — | — | **Not configured** |

### 12.2 Redis Architecture

| Aspect | Detail |
|---|---|
| Purpose | Tenant registry caching; session store |
| Client | Lettuce (Spring Boot default) |
| Serialization | Keys: `StringRedisSerializer`; Values: `RedisSerializer.json()` (Jackson) |
| Connection | Standalone (`RedisStandaloneConfiguration`) |
| Key prefix | `vidya:tenant:code:{tenantCode}` |
| TTL | `vidya.redis.ttl.tenant-registry-seconds` (default 600s / 10 minutes) |
| Eviction | Manual via `TenantRegistryService.evictCache()` |
| Failure behavior | Redis failure is **non-fatal** — falls through to master DB query |

### 12.3 Caching Guidelines for Future Development

Before adding any cache:
1. **Measure** — prove the query is a bottleneck.
2. **Tenant-scope keys** — include tenant identifier in every cache key.
3. **Document TTL** — specify and justify the TTL.
4. **Invalidation** — implement explicit invalidation on writes.
5. **Test** — add tests for cache hit, miss, invalidation, and cross-tenant isolation.
6. **Observe** — add cache hit/miss metrics.

---

## 13. Authentication, Authorization, and Security

### 13.1 Authentication Flow

1. Client sends `POST /api/v1/auth/login` with `{email, password}`.
2. `AuthService` finds `PlatformUser` by email in master DB.
3. Password verified via BCrypt.
4. Account status and lockout checked.
5. `JwtTokenProvider.generateAccessToken()` creates JWT with claims: `sub` (userId), `email`, `userCode`, `role`, `type=ACCESS`, `schoolId` (optional).
6. `JwtTokenProvider.generateRefreshToken()` creates refresh JWT.
7. Refresh token hash stored in master DB.
8. Response: `{accessToken, refreshToken, expiresIn, user}`.

### 13.2 Token Model

| Token | Lifetime | Claims | Storage |
|---|---|---|---|
| Access Token | Default 1800s (30 min) | `sub`, `email`, `userCode`, `role`, `type=ACCESS`, `schoolId` | Client-side only |
| Refresh Token | Default 604800s (7 days) | `sub`, `email`, `type=REFRESH` | SHA-256 hash in master DB |

**Security measures**: **[Implemented]**
- Refresh tokens cannot be used as bearer access tokens (checked in `JwtAuthenticationFilter`).
- JWT secret minimum 32 bytes (256 bits) enforced at startup.
- Production requires explicit `vidya.security.jwt.secret`; dev/test profiles allow fallback default.
- Passwords hashed with BCrypt.
- Account lockout: max 5 failed attempts, 15-minute lockout.
- Token hashing: SHA-256 for refresh token storage.

### 13.3 Spring Security Filter Chain

**[Implemented]** in `SecurityConfig`:
- CSRF: **Disabled** (stateless JWT API).
- Session management: `STATELESS`.
- CORS: Configurable origins via `vidya.security.cors.allowed-origins`.
- Allowed CORS methods: GET, POST, PUT, PATCH, DELETE, OPTIONS.
- Allowed CORS headers: Authorization, Content-Type, X-Tenant-ID, X-Correlation-ID, Accept.
- Exposed CORS headers: X-Correlation-ID.
- Custom `CustomAuthenticationEntryPoint` (401) and `CustomAccessDeniedHandler` (403) return `ApiResponse` JSON.
- Method-level security: `@EnableMethodSecurity` → `@PreAuthorize("hasAuthority('...')")`.

**Public endpoints** (no authentication required):
- `/`, `/index`, `/index.html`, `/schools/**`
- `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`
- `/api/v1/webhooks/**`
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- `/actuator/health`, `/actuator/info`
- Static resources, `favicon.ico`, `robots.txt`, `sitemap.xml`, `llms.txt`, `llms-full.txt`

### 13.4 RBAC Model

Roles are defined in `PlatformRole` enum with `EnumSet<PlatformPermission>`. **[Implemented]**

| Role | Scope | Permission Count |
|---|---|---|
| `PLATFORM_SUPER_ADMIN` | All permissions | All (~100+) |
| `PLATFORM_ADMIN` | Nearly all permissions | ~95 |
| `COMMUNITY_ADMIN` | Community + onboarding + calendar read | 8 |
| `COMMUNITY_MANAGER` | Community + onboarding | 5 |
| `FIELD_AGENT` | Onboarding + school read | 4 |
| `SCHOOL_OWNER` | Full school management + attendance override | ~80 |
| `SCHOOL_ADMIN` | Full school management + attendance override | ~80 |
| `TEACHER` | Attendance CRUD + read-only academic/timetable | ~15 |

### 13.5 Security Headers

- Content Security Policy: `default-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; img-src 'self' data: https:; frame-ancestors 'none';`
- X-Frame-Options: `DENY`.
- HSTS: `max-age=31536000; includeSubDomains`.

---

## 14. API and Integration Architecture

### 14.1 API Conventions

| Convention | Standard |
|---|---|
| Base path | `/api/v1/` |
| Response envelope (single) | `ApiResponse.success(code, message, data)` |
| Response envelope (paged) | `PagedApiResponse.of(code, message, page)` |
| Error envelope | `ApiResponse.error(code, message)` + `correlationId` |
| Content type | `application/json` |
| Authentication | `Authorization: Bearer <JWT>` |
| Tenant selection | `X-Tenant-ID: <tenantCode>` |
| Correlation tracking | `X-Correlation-ID` (generated if not provided) |
| Validation | Jakarta Bean Validation (`@Valid`) |
| Documentation | SpringDoc OpenAPI 3.1 annotations |
| Pagination | Spring Data `Pageable` → `PagedApiResponse` |

### 14.2 Tenant-Exempt Paths

These paths bypass `TenantResolutionFilter` and use the master DB:
`/api/v1/auth/`, `/api/v1/onboarding`, `/api/v1/subscription-plans`, `/api/v1/communities`, `/api/v1/schools`, `/api/v1/admin`, `/api/v1/platform`, `/api/v1/webhooks`, `/actuator`, `/v3/api-docs`, `/swagger-ui`

### 14.3 Error Response Codes

| Exception | HTTP Status | Code |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `IllegalArgumentException` | 400 | `INVALID_ARGUMENT` |
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `TenantNotFoundException` | 404 | `TENANT_NOT_FOUND` |
| `DuplicateResourceException` | 409 | `DUPLICATE_RESOURCE` |
| `BusinessException` | 422 | Custom code |
| `InvalidStateTransitionException` | 422 | `INVALID_STATE_TRANSITION` |
| `TenantAccessDeniedException` | 403 | `TENANT_ACCESS_DENIED` |
| `TenantSuspendedException` | 403 | `TENANT_SUSPENDED` |
| `AccessDeniedException` | 403 | `FORBIDDEN` |
| `AuthenticationException` | 401 | `UNAUTHORIZED` |
| `PaymentException` | 422 | `PAYMENT_FAILED` |
| `TenantProvisioningException` | 500 | `TENANT_PROVISIONING_FAILED` |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` |

**Security**: Stack traces are **never** returned in response bodies. **[Implemented]**

---

## 15. Domain Module Architecture

### 15.1 Master Domain Modules

#### Platform Identity & RBAC (`com.vidya.security`)

- **Entities**: `PlatformUser`, `PlatformRole` (enum with 8 roles), `PlatformPermission` (enum with 100+ permissions), `RefreshToken`, `UserStatus`
- **Services**: `AuthService`, `PlatformUserDetailsService`, `TenantAuthorizationService`, `SecurityAuditService`
- **Controller**: `AuthController` (`/api/v1/auth/*`)

#### Community Networks (`com.vidya.master.community`)

- **Entities**: Community, CommunityMember, CommunityStatus

#### School & Branch Directory (`com.vidya.master.school`)

- **Entities**: School, SchoolBranch, SchoolBoard, SchoolStatus, SchoolType

#### Onboarding Pipeline (`com.vidya.master.onboarding`)

- **State machine**: Multi-step onboarding (Basic Info → Legal Docs → Academic Setup → Verification → Approval → Provisioning)

#### Subscriptions & Features (`com.vidya.master.subscription`)

- **Entities**: SubscriptionPlan, SubscriptionFeature, SubscriptionPlanFeature, SchoolSubscription
- **Tiers**: Standard, Pro, Enterprise

#### Platform Payments (`com.vidya.master.payment`)

- **Integration**: Razorpay and Cashfree configuration present

### 15.2 Tenant Domain Modules

Each module follows: Entity → Repository → DTO → Mapper → Service (interface + impl) → Controller.

#### Tenant Users (`com.vidya.tenantdata.user`)

- **Entities**: `TenantUser`, `TenantUserProfile`
- **Types**: STUDENT, TEACHER, PARENT, STAFF, SCHOOL_ADMIN

#### Academic Structure (`com.vidya.tenantdata.academic`)

- **Entities**: `AcademicYear` (DRAFT→ACTIVE→ARCHIVED→CLOSED), `AcademicClass`, `Section`

#### Student Management (`com.vidya.tenantdata.student`)

- **Entities**: Student, StudentAcademicAssignment, StudentGuardian, StudentSubjectEnrollment
- **Status machine**: ACTIVE→PROMOTED/TRANSFERRED/EXPELLED/ALUMNI
- **Business code**: `STU-XXXX`

#### Staff Management (`com.vidya.tenantdata.staff`)

- **Entities**: Staff, StaffDepartment

#### Subjects & Curricula (`com.vidya.tenantdata.subject`)

- **Entities**: Subject, Curriculum, CurriculumSubject

#### Assignments (`com.vidya.tenantdata.assignment`)

- **Entities**: TeacherSubjectAssignment, ClassTeacherAssignment, ClassSubjectAssignment
- **Invariant**: Single active class teacher per section

#### Attendance (`com.vidya.tenantdata.attendance`)

- **Entity**: `StudentAttendance`
- **States**: PRESENT, ABSENT, LATE, HALF_DAY, EXCUSED, DELETED
- **Bulk upsert**: `POST /api/v1/attendance/bulk` — natural key `studentId + academicYearId + attendanceDate`
- **Non-working day override**: Requires `ATTENDANCE_OVERRIDE_NON_WORKING` permission
- **Clock injection**: Uses `Clock` bean for deterministic testing

#### Timetable & Scheduling (`com.vidya.tenantdata.timetable`)

- **Entities**: Room, BellSchedule, BellSchedulePeriod, Timetable, TimetableEntry, TimetableSubstitution
- **Conflict detection**: Teacher, room, and section clash validation
- **Bulk upsert**: Fail-fast two-phase (validate all → write all)
- **Day schedule views**: Section and teacher daily schedules with substitution overlay

#### Calendar (`com.vidya.tenantdata.calendar`)

- **Entities**: WorkingDayConfig, SchoolHoliday
- **Saturday patterns**: ALL, FIRST_THIRD_FIFTH, SECOND_FOURTH
- **Holiday types**: NATIONAL, REGIONAL, LOCAL, INSTITUTIONAL, EMERGENCY

---

## 16. Background Jobs, Events, and Asynchronous Processing

**Status**: No background job scheduler, `@Scheduled` methods, `@Async` methods, message queues, or event-driven processing infrastructure is present in the codebase. **[Not implemented]**

**Implications**:
- Tenant provisioning (`executeProvisioning()`) runs synchronously.
- No automatic migration of existing tenant databases when new migrations are added.
- No periodic cleanup of expired sessions, stale DataSource caches, or audit log archival.

---

## 17. Error Handling and Observability

### 17.1 Global Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) catches all controller exceptions. **[Implemented]**

**Guarantees**:
- Stack traces are **never** returned in response bodies.
- Internal database/JPA details are **never** leaked.
- Every error response includes a `correlationId` for log tracing.
- Unexpected exceptions are logged at ERROR level with full detail.

### 17.2 Structured Logging

**Log pattern** (`application.yml`):
```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId}] [%X{tenantId}] %-5level %logger{36} - %msg%n
```

**MDC keys** (managed by `CorrelationIdFilter` and `TenantResolutionFilter`):
- `correlationId` — UUID per request
- `tenantId` — tenant code (set when tenant context is active)
- `schoolId` — school UUID
- `httpMethod` — GET, POST, etc.
- `requestUri` — request path

### 17.3 Actuator Health & Metrics

**Exposed endpoints**:
- `/actuator/health` (show-details: when-authorized)
- `/actuator/info`
- `/actuator/metrics`

### 17.4 Security Audit

`SecurityAuditService` logs access denials with user ID, email, resource, reason, IP, and correlation ID. **[Implemented]**

---

## 18. Configuration and Environment Management

### 18.1 Configuration Files

| File | Purpose |
|---|---|
| `application.yml` | Base/shared config |
| `application-dev.yml` | Local development defaults |
| `application-prod.yml` | Production (env-var bound, no defaults for secrets) |

### 18.2 Configuration Inventory

| Property | Purpose | Default (Dev) | Required (Prod) | Sensitive |
|---|---|---|---|---|
| `vidya.datasource.master.url` | Master DB JDBC URL | `jdbc:postgresql://localhost:5432/vidya_master` | Yes | No |
| `vidya.datasource.master.username` | Master DB user | `postgres` | Yes | Yes |
| `vidya.datasource.master.password` | Master DB password | (dev default) | Yes | **Yes** |
| `vidya.tenant.datasource.hikari.*` | Tenant pool settings | Various | No | No |
| `vidya.tenant.datasource.cache.max-size` | Max cached tenant DSes | 200 | No | No |
| `vidya.provisioning.admin.url` | Admin DB URL for provisioning | `jdbc:postgresql://localhost:5432/postgres` | Yes | No |
| `vidya.provisioning.admin.username` | Admin DB user | `postgres` | Yes | **Yes** |
| `vidya.provisioning.admin.password` | Admin DB password | (dev default) | Yes | **Yes** |
| `vidya.redis.host` | Redis host | `localhost` | Yes | No |
| `vidya.redis.port` | Redis port | 6379 | No | No |
| `vidya.redis.password` | Redis password | (empty) | Recommended | **Yes** |
| `vidya.redis.ttl.tenant-registry-seconds` | Tenant cache TTL | 600 | No | No |
| `vidya.security.jwt.secret` | JWT signing secret | Dev fallback | **Yes (fail-fast)** | **Yes** |
| `vidya.security.jwt.expiry-seconds` | Access token TTL | 1800 | No | No |
| `vidya.security.jwt.refresh-expiry-seconds` | Refresh token TTL | 604800 | No | No |
| `vidya.security.cors.allowed-origins` | CORS origins | localhost variants | Yes | No |
| `vidya.security.login.max-failed-attempts` | Lockout threshold | 5 | No | No |
| `vidya.security.login.lockout-minutes` | Lockout duration | 15 | No | No |
| `vidya.school.timezone` | Application timezone | `Asia/Kolkata` | No | No |

---

## 19. Timezone, Dates, and Scheduling

### 19.1 Configuration

- **Default timezone**: `Asia/Kolkata` via `vidya.school.timezone` property.
- **Clock bean**: `ClockConfig` creates `Clock.system(ZoneId.of(timezone))`. **[Implemented]**
- **Hibernate timezone**: `hibernate.jdbc.time_zone = UTC` — all timestamps stored as UTC in PostgreSQL.

### 19.2 Date Conventions

| Use Case | Java Type | Database Type |
|---|---|---|
| Audit timestamps | `Instant` | `TIMESTAMP` (UTC) |
| Attendance date | `LocalDate` | `DATE` |
| Academic year dates | `LocalDate` | `DATE` |
| Bell schedule times | `LocalTime` | `TIME` |
| Holiday date range | `LocalDate` | `DATE` |
| Substitution date | `LocalDate` | `DATE` |
| Timetable effective dates | `LocalDate` | `DATE` |

### 19.3 Testing with Clock

Services inject `Clock` for deterministic testing. Tests provide a fixed-time `Clock` to control `LocalDate.now(clock)`. **[Implemented]**

---

## 20. Performance and Scalability

### 20.1 Design-Level Performance Measures

| Mechanism | Evidence |
|---|---|
| Hibernate batch inserts (size 20) | `MasterDataSourceConfig`, `TenantDataSourceConfig` |
| PreparedStatement caching | `cachePrepStmts=true` in `TenantDataSourceFactory` |
| HikariCP connection pooling | Per-tenant and master pools |
| `open-in-view: false` | Prevents lazy-loading outside transactions |
| Bounded timetable queries | O(1) statement count — **Tested** |
| Bulk attendance upsert | Single-transaction batch with natural key dedup |

### 20.2 Known Scalability Constraints

- **Tenant DataSource cache**: No automatic LRU eviction. At scale, PostgreSQL connection limits may be reached.
- **Single application instance**: No documented multi-instance deployment or distributed locking.
- **Synchronous provisioning**: Database creation, migration, and initialization are blocking.

---

## 21. Testing Architecture

### 21.1 Test Summary

| Category | Count (files) | Infrastructure |
|---|---|---|
| Source files | 535 | — |
| Test files | 130 | — |
| Integration tests (Testcontainers) | 22 | PostgreSQL 16 containers |
| Isolation tests | ~12 | Verify cross-tenant data isolation |
| Concurrency tests | ~8 | Verify thread-safety |
| Unit tests (service) | ~40+ | Mockito |
| Unit tests (controller) | ~25+ | MockMvc |
| Entity/enum tests | ~15+ | Plain JUnit |

### 21.2 Integration Test Infrastructure

- **Base class**: `AbstractTenantPostgresIntegrationTest` — Testcontainers PostgreSQL setup with Flyway migration.
- **Container**: PostgreSQL 16 via Testcontainers 1.20.4.
- **Statement count tests**: `TimetableStatementCountPostgresIntegrationTest` — verifies O(1) SQL query count.

### 21.3 Testing Gaps

| Area | Status |
|---|---|
| Redis failure fallback | **Gap** |
| JWT expiry/revocation | **Unverified** |
| Provisioning retry/failure | **Unverified** |
| Load/stress testing | **Not implemented** |

---

## 22. Build, Run, and Deployment Architecture

### 22.1 Prerequisites

- Java 25 (JDK)
- PostgreSQL 16 (local or Docker)
- Redis (local or Docker)
- Docker Desktop (for Testcontainers)
- Maven (via wrapper)

### 22.2 Commands

| Task | Command |
|---|---|
| Build (skip tests) | `./mvnw clean package -DskipTests` |
| Run all tests | `./mvnw test` |
| Run integration tests | `./mvnw test -Dtest="*IntegrationTest"` |
| Start application | `./mvnw spring-boot:run` |
| Package JAR | `./mvnw clean package` |
| Run JAR | `java -jar target/Vidya-Backend-Project-0.0.1-SNAPSHOT.jar` |

### 22.3 Local Development Setup

1. Start PostgreSQL and create `vidya_master` database.
2. Start Redis on default port.
3. Set environment variables or use `application-dev.yml` defaults.
4. Run `./mvnw spring-boot:run` — master Flyway runs automatically.
5. Verify: `GET http://localhost:8085/actuator/health`

### 22.4 Deployment

No CI/CD pipeline, Dockerfile, or container orchestration files were found in the repository. **[Unverified]**

- Production profile: `SPRING_PROFILES_ACTIVE=prod`
- Production disables Swagger/OpenAPI.
- Production requires all secrets via environment variables.

---

## 23. Failure Modes and Recovery

| Failure | Expected Behavior | Recovery | Data Risk |
|---|---|---|---|
| Master DB unavailable | Application fails to start (Flyway) | Restore DB, restart app | None (startup gate) |
| Tenant DB unavailable | Requests to that tenant fail with 500 | Restore DB; DS auto-recreated on next request | Tenant-isolated |
| Redis unavailable | Tenant lookups fall to master DB; sessions may fail | Restart Redis | Performance degradation |
| Invalid X-Tenant-ID | `TenantNotFoundException` (404) | Client-side fix | None |
| Migration failure | `TenantProvisioningException`; status = FAILED | Fix migration, retry | Partial schema state |
| Connection pool exhaustion | Connection timeout (30s), request fails | Increase pool size | None |
| JWT secret missing (prod) | Startup fails | Configure secret | None |
| Partial provisioning | State machine tracks last completed step | `retryProvisioning()` | Safe — idempotent |
| Application restart mid-operation | Open transactions roll back | Retry if needed | Safe for ACID |

---

## 24. Known Limitations and Technical Debt

| # | Issue | Severity | Module | Status |
|---|---|---|---|---|
| 1 | No automatic tenant migration upgrade trigger | Medium | `tenant.provisioning` | **Gap** |
| 2 | No LRU eviction for tenant DataSource cache | Medium | `tenant.datasource` | **Known limitation** |
| 3 | No module boundary enforcement (ArchUnit) | Low | All | **Known limitation** |
| 4 | No background job framework | Medium | Global | **Known limitation** |
| 5 | No elective/split-batch timetable support | Low | `tenantdata.timetable` | **Known limitation** |
| 6 | STUDENT/PARENT roles denied tenant access | Medium | `security` | **Planned** (Step 31) |
| 7 | No distributed locking | Low | `tenant.provisioning` | **Known limitation** |
| 8 | Timetable day views don't skip holidays | Low | `tenantdata.timetable` | **Known limitation** |
| 9 | No Redis password in dev defaults | Low | `config.redis` | **Known limitation** |
| 10 | MongoDB excluded but dependency present | Low | `pom.xml` | **Known limitation** |

---

## 25. Architecture Decision Records

### ADR-001: Modular Monolith

**Decision**: Single deployable Spring Boot application with package-level module separation.
**Rationale**: Reduced operational complexity for an early-stage product. All modules share a JVM, simplifying transactions, debugging, and deployment.
**Consequences**: No compile-time module boundary enforcement. Cross-module coupling possible via convention violation.
**Status**: Active

### ADR-002: Database-per-Tenant Isolation

**Decision**: Each school gets a physically separate PostgreSQL database.
**Rationale**: Maximum data isolation. No risk of query-level cross-tenant leakage. Independent backup/restore. Compliance-friendly for data residency.
**Consequences**: Connection pool overhead per tenant. Schema migration must be applied to all tenant databases individually.
**Status**: Active

### ADR-003: Separate Transaction Managers

**Decision**: `masterTransactionManager` and `tenantTransactionManager` with explicit selection.
**Rationale**: Prevents accidental cross-database transactions. Makes database ownership explicit.
**Consequences**: Every `@Transactional` must specify the correct manager. Cross-database operations are explicitly non-atomic.
**Status**: Active

### ADR-004: Redis for Tenant Registry Cache

**Decision**: Redis used as cache layer for tenant metadata lookups, with master DB as fallback.
**Rationale**: Every tenant request requires a registry lookup. Caching reduces master DB load.
**Consequences**: Cache invalidation must be called on tenant status changes. Redis failure is non-fatal (graceful degradation).
**Status**: Active

### ADR-005: JWT Stateless Authentication

**Decision**: Stateless JWT with access + refresh tokens. No server-side session for API authentication.
**Rationale**: Scalability — no shared session state required across application instances.
**Consequences**: Token revocation requires checking refresh token status. Access tokens are valid until expiry.
**Status**: Active

### ADR Template

```markdown
### ADR-NNN: [Title]
**Decision**: [What was decided]
**Rationale**: [Why — verified/inferred]
**Consequences**: [Trade-offs and constraints created]
**Status**: Active | Superseded by ADR-XXX
```

---

## 26. Developer Guide: How to Extend Vidya Safely

### 26.1 Add a New API Endpoint (Tenant Domain)

1. Identify the domain module in `com.vidya.tenantdata.<module>`.
2. Create request/response DTOs in `<module>/dto/`.
3. Add mapper methods in `<module>/mapper/`.
4. Add service interface method in `<module>/service/`.
5. Implement in `<module>/service/<Module>ServiceImpl.java`.
6. Annotate with `@Transactional(transactionManager = "tenantTransactionManager")`.
7. Add controller method with `@PreAuthorize`, `@Operation` annotations.
8. Return `ApiResponse.success(...)` or `PagedApiResponse.of(...)`.
9. Add unit tests (service + controller).
10. Run `./mvnw test`.

### 26.2 Add a New Domain Module

1. Create `com.vidya.tenantdata.<newmodule>/` with: `entity/`, `repository/`, `dto/`, `mapper/`, `service/`, `controller/`.
2. Add Flyway migration `V{N+1}__create_<table>.sql` in `db/migration/tenant/`.
3. Entities must extend `AuditableEntity` or include audit fields manually.
4. Use `@GeneratedValue(strategy = GenerationType.UUID)` for primary keys.
5. Include `deleted_at` column with partial unique index.
6. Use `@Transactional(transactionManager = "tenantTransactionManager")`.
7. Add comprehensive tests.
8. Update this document (Section 15).

### 26.3 Add a Database Migration

1. Check current max version: `ls src/main/resources/db/migration/{master|tenant}/`.
2. Create `V{N+1}__{description}.sql`.
3. **Never** edit an already-applied migration.
4. Use `IF NOT EXISTS` where possible.
5. For soft-delete tables, add partial unique index: `WHERE deleted_at IS NULL`.
6. Update corresponding JPA entity.
7. Run `./mvnw test`.

### 26.4 Add a Permission or Role

1. Add value to `PlatformPermission` enum.
2. Add to relevant `PlatformRole` enum constructor sets.
3. Use in `@PreAuthorize("hasAuthority('NEW_PERMISSION')")`.
4. Update tests.

### 26.5 Add an Integration Test

1. Extend `AbstractTenantPostgresIntegrationTest`.
2. Use `@Testcontainers` and `@Container` annotations.
3. Verify tenant isolation by testing cross-tenant access denial.
4. Keep test self-contained.

---

## 27. AI Coding Agent Operating Rules

### 27.1 Mandatory Requirements

1. **Read `ARCHITECTURE.md` before changing architectural or cross-cutting code.**
2. **Inspect relevant source files** rather than relying on documentation alone.
3. **Search for existing patterns** before introducing new abstractions.
4. **Preserve module boundaries** and naming conventions.
5. **Never bypass tenant authorization or routing.**
6. **Never add tenant-specific data to the master database** without a documented reason.
7. **Never assume master and tenant database transactions are atomic together.**
8. **Never introduce caching** without explicit tenant-safe keys, TTLs, invalidation, and tests.
9. **Never change migration history** or edit already-applied migrations.
10. **Never log** secrets, JWTs, passwords, or unnecessary student data.
11. **Add tests** for changed behavior and security boundaries.
12. **Run `./mvnw test`** and verify 0 failures.
13. **Report failures and unverified behavior honestly.**
14. **Update `ARCHITECTURE.md`** when architectural invariants change.
15. **Avoid unrelated refactoring** and scope expansion.

### 27.2 Pre-Implementation Checklist

- [ ] Identified correct database (Master vs Tenant).
- [ ] Identified correct transaction manager.
- [ ] Checked for existing patterns in the module.
- [ ] Verified migration version numbering.
- [ ] Confirmed tenant isolation is preserved.
- [ ] Confirmed no cross-database transaction.

### 27.3 Post-Implementation Checklist

- [ ] All existing tests pass (`./mvnw test`).
- [ ] New unit tests added for service and controller.
- [ ] Integration/isolation test added if touching tenant data.
- [ ] `@PreAuthorize` added to new controller endpoints.
- [ ] `@Transactional` specifies correct transaction manager.
- [ ] Migration file follows naming convention.
- [ ] No secrets in logs or responses.
- [ ] ARCHITECTURE.md updated if architectural change.

---

## 28. Architecture Diagrams

| # | Diagram | Location |
|---|---|---|
| 1 | High-Level Architecture | Section 3.4 |
| 2 | Filter Chain Order | Section 8.1 |
| 3 | Request Lifecycle | Section 8.2 |
| 4 | Tenant Resolution | Section 10.2 |
| 5 | Tenant Routing DataSource | Section 10.4 |
| 6 | Database Topology | Section 11.1 |
| 7 | Tenant Provisioning | Section 10.6 |
| 8 | Authentication Flow | Section 13.1 |

---

## 29. Architecture Glossary

| Term | Definition |
|---|---|
| **Tenant** | A single school instance with its own dedicated database |
| **Master Database** | The central platform database storing cross-tenant data |
| **Tenant Database** | A physically isolated PostgreSQL database for one school |
| **Tenant Context** | `ThreadLocal<String>` holding the current tenant's code for the request duration |
| **Tenant Routing** | Mechanism by which `TenantRoutingDataSource` selects the correct DataSource |
| **Tenant Code** | A unique string identifier for a tenant (e.g., `SCHOOL_ABC`) |
| **RBAC** | Role-Based Access Control — permissions assigned to roles, roles assigned to users |
| **Transaction Manager** | Spring's `PlatformTransactionManager` — one per DataSource |
| **Migration** | A versioned SQL script managed by Flyway |
| **Provisioning** | Creating a new tenant's database, running migrations, and seeding data |
| **AuditableEntity** | The `@MappedSuperclass` providing audit fields |
| **ApiResponse** | The standard JSON envelope wrapping all API responses |
| **Correlation ID** | A UUID assigned to each HTTP request for tracing |
| **Soft Deletion** | Marking records as deleted via `deleted_at` timestamp |
| **Partial Unique Index** | A PostgreSQL index with `WHERE deleted_at IS NULL` |

---

## 30. Source Reference Index

| Component | Path |
|---|---|
| Application entry point | `src/main/java/com/vidya/VidyaApplication.java` |
| Security configuration | `src/main/java/com/vidya/security/config/SecurityConfig.java` |
| Tenant resolution filter | `src/main/java/com/vidya/tenant/resolver/TenantResolutionFilter.java` |
| Tenant context (ThreadLocal) | `src/main/java/com/vidya/tenant/context/TenantContext.java` |
| Tenant routing DataSource | `src/main/java/com/vidya/tenant/datasource/TenantRoutingDataSource.java` |
| Tenant DataSource manager | `src/main/java/com/vidya/tenant/datasource/TenantDataSourceManager.java` |
| Tenant DataSource factory | `src/main/java/com/vidya/tenant/datasource/TenantDataSourceFactory.java` |
| Tenant registry service | `src/main/java/com/vidya/tenant/registry/TenantRegistryServiceImpl.java` |
| Tenant authorization service | `src/main/java/com/vidya/security/service/TenantAuthorizationServiceImpl.java` |
| Tenant provisioning | `src/main/java/com/vidya/tenant/provisioning/TenantProvisioningService.java` |
| Tenant database creator | `src/main/java/com/vidya/tenant/provisioning/TenantDatabaseCreator.java` |
| Tenant migration service | `src/main/java/com/vidya/tenant/provisioning/TenantMigrationService.java` |
| Master DataSource config | `src/main/java/com/vidya/config/database/MasterDataSourceConfig.java` |
| Tenant DataSource config | `src/main/java/com/vidya/config/database/TenantDataSourceConfig.java` |
| Master Flyway config | `src/main/java/com/vidya/config/flyway/MasterFlywayConfig.java` |
| Tenant Flyway config | `src/main/java/com/vidya/config/flyway/TenantFlywayConfig.java` |
| Redis config | `src/main/java/com/vidya/config/redis/RedisConfig.java` |
| Clock config | `src/main/java/com/vidya/config/datetime/ClockConfig.java` |
| JWT token provider | `src/main/java/com/vidya/security/jwt/JwtTokenProvider.java` |
| JWT authentication filter | `src/main/java/com/vidya/security/jwt/JwtAuthenticationFilter.java` |
| Platform role & permissions | `src/main/java/com/vidya/security/entity/PlatformRole.java` |
| API response envelope | `src/main/java/com/vidya/common/api/ApiResponse.java` |
| Global exception handler | `src/main/java/com/vidya/common/exception/GlobalExceptionHandler.java` |
| Correlation ID filter | `src/main/java/com/vidya/common/correlation/CorrelationIdFilter.java` |
| Auditable entity base | `src/main/java/com/vidya/common/audit/AuditableEntity.java` |
| Base config | `src/main/resources/application.yml` |
| Dev config | `src/main/resources/application-dev.yml` |
| Prod config | `src/main/resources/application-prod.yml` |
| Master migrations | `src/main/resources/db/migration/master/` (V1–V20) |
| Tenant migrations | `src/main/resources/db/migration/tenant/` (V1–V16) |
| Testcontainers base | `src/test/java/com/vidya/integration/AbstractTenantPostgresIntegrationTest.java` |

---

## 31. Architecture Verification Checklist

| Area | Status | Notes |
|---|---|---|
| Technology stack | ✅ Verified | Java 25, Spring Boot 4.1.1, PostgreSQL, Redis, Flyway |
| Repository structure | ✅ Verified | 535 source files, 130 test files |
| Module dependencies | ⚠️ Partially verified | Convention-based, no compile-time enforcement |
| Tenant lifecycle | ✅ Verified | Full provisioning flow inspected |
| Tenant routing | ✅ Verified | TenantRoutingDataSource → TenantDataSourceManager |
| Authorization boundaries | ✅ Verified | TenantAuthorizationService with role-based access |
| Database topology | ✅ Verified | Master + per-tenant databases |
| Transaction semantics | ✅ Verified | Separate transaction managers |
| Migration behavior | ✅ Verified | Master at startup, tenant during provisioning |
| Redis/session behavior | ⚠️ Partially verified | Tenant cache verified; session store configured |
| API conventions | ✅ Verified | ApiResponse envelope, /api/v1/ prefix |
| Security controls | ✅ Verified | JWT, RBAC, tenant auth, hardened headers |
| Timezone handling | ✅ Verified | Clock injection, UTC storage |
| Test coverage | ⚠️ Partially verified | 130 test files; 983 tests claimed |
| Deployment | ❌ Unverified | No CI/CD or container config found |

---

## 32. Change Log

| Version | Date | Summary |
|---|---|---|
| 1.0.0 | Pre-existing | Initial architecture in `docs/ARCHITECTURE.md` |
| 2.0.0 | 2026-10-17 | Complete rewrite from systematic codebase inspection. All 32 sections. Verified against commit `2b915c1`. 535 source files, 130 test files inspected. |
