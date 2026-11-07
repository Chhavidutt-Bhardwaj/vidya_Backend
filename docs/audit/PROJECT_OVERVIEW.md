# Vidya Backend — Project Overview & Codebase Audit

| | |
|---|---|
| Audit date | 2026-11-07 |
| Revision audited | `9c6d509` (branch `main`, clean tree) |
| Scope | Entire repository; no source code was modified |
| Method | Source reading, migration analysis, endpoint extraction from controllers, full `./mvnw test` run against Testcontainers PostgreSQL |

This folder replaces nothing. The root `PROJECT_OVERVIEW.md`, `README.md` and `docs/ARCHITECTURE.md` are left untouched. Where they disagree with the code, the code wins, and the discrepancies are listed in §6 below.

## Audit document set

| Document | Contents |
|---|---|
| [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) | This file: executive summary, stack, verdict |
| [ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md) | Packages, request flow, multi-tenancy, transactions |
| [DATABASE_ANALYSIS.md](DATABASE_ANALYSIS.md) | Master and tenant schemas, constraints, defects |
| [API_INVENTORY.md](API_INVENTORY.md) | All 363 endpoints with authorization status |
| [MODULE_STATUS.md](MODULE_STATUS.md) | Every product module classified COMPLETE / PARTIAL / PLACEHOLDER / MISSING / NEEDS REFACTORING |
| [SECURITY_AUDIT.md](SECURITY_AUDIT.md) | Findings ranked by severity, with file references |
| [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md) | Code, build, test and operational debt |
| [MISSING_FEATURES.md](MISSING_FEATURES.md) | Gap analysis against the target School ERP |
| [GAP_ANALYSIS_AND_ROADMAP.md](GAP_ANALYSIS_AND_ROADMAP.md) | Prioritized P0–P4 roadmap |

---

## 1. Executive summary

Vidya is a **Spring Boot 4.1.1 / Java 25 modular monolith** that uses a **database-per-tenant** PostgreSQL design. There are 879 Java files (738 production, 141 test), about 89k lines including tests.

It splits into two halves of very different maturity:

1. **Master/platform domain** (`com.vidya.master`): SaaS go-to-market tooling. It covers school onboarding, subscription plans and feature tiers, platform billing, field-agent "communities" with commissions, regional hubs, and a support desk. It has a lot of code but **almost no authorization**.
2. **Tenant/school domain** (`com.vidya.tenantdata`): the actual school ERP. It covers academic years, classes and sections, students and guardians, staff, subjects and curriculum, teacher assignments, timetable and substitutions, academic calendar, daily attendance, and examination marks. The engineering here is careful (fail-fast bulk APIs, partial unique indexes, optimistic locking). But it covers only the **academic core**: there are no fees, HR/payroll, transport, library, inventory, communication, documents or parent/student access.

### Verdict

The codebase is a **solid foundation that is not shippable today**. Before any new module work, three things must be fixed:

| # | Blocker | Evidence |
|---|---|---|
| 1 | **Known-password super-admin backdoor in every environment** | `security/init/SuperUserDataInitializer.java` has no `@Profile`. On every boot it forces `superuser@vidya.io` and `admin@vidya.io` to active `PLATFORM_SUPER_ADMIN` with password `Password@123`. |
| 2 | **Master APIs are unauthorized** | 136 of 363 endpoints check only "is authenticated". Any school TEACHER token can create or modify schools, publish plans, confirm or refund payments, approve onboarding and provision tenants. |
| 3 | **New tenant databases cannot be created** | Tenant migration `V18__create_grading_and_report_cards.sql:11` references a non-existent table `class_section`. Every tenant Flyway run fails, and all 9 real-PostgreSQL integration test classes error. |

Plus: payment verification can be bypassed (no signature means success). Bank account, PAN and Aadhaar numbers are stored in plaintext and returned as raw entities. There is no row-level scoping inside a school: the teacher-scope check is explicitly stubbed.

## 2. Test status (verified, not claimed)

Command: `./mvnw -B -o test` (Docker available, Testcontainers PostgreSQL)

```
Tests run: 1072, Failures: 0, Errors: 9, Skipped: 0  →  BUILD FAILURE
```

- The 9 errors are **every** Testcontainers integration class (`src/test/java/com/vidya/integration/*`). Each fails in `@BeforeAll` while applying tenant migration V18, so **none of their test methods actually ran**.
- The remaining ~1,063 passing tests are **Mockito unit tests and `MockMvcBuilders.standaloneSetup` controller tests**. Standalone MockMvc bypasses Spring Security entirely, so `@PreAuthorize` and the filters are never exercised.
- There is **no `@SpringBootTest` and no `@WebMvcTest` anywhere**. Nothing in the suite proves that the application context starts.
- `SecurityTenantIsolationIntegrationTest` is named an integration test but is pure Mockito.

**Conclusion:** the claims "983 passed / 100%" (root `PROJECT_OVERVIEW.md`) and "724 passed" (`README.md`) are stale. Real-database and real-security behaviour is currently **untested**.

## 3. Technology stack

| Technology | Version | Where used | Assessment |
|---|---|---|---|
| Java | 25 | whole project | Fine. It's a very new LTS, so confirm hosting support. |
| Spring Boot | 4.1.1 | parent POM | Current. Uses the Boot 4 modular starters (`spring-boot-starter-webmvc`, etc.). |
| Spring Security | Boot-managed | `security/` | Stateless JWT. Method security is enabled but applied inconsistently (see Security Audit). |
| Spring Data JPA / Hibernate | Boot-managed | two EntityManagerFactories (master and tenant) | Correct split. The master uses `hbm2ddl=validate`; the tenant uses `none`. |
| PostgreSQL | driver Boot-managed | master DB plus one DB per tenant | Appropriate. |
| Flyway | Boot-managed + `flyway-database-postgresql` | `config/flyway`, `tenant/provisioning/TenantMigrationService` | Run programmatically per tenant. Master V1–V27, tenant V1–V18 (**V18 broken**). |
| Redis (Lettuce) | Boot-managed | `TenantRegistryServiceImpl` cache (`RedisTemplate`); `spring-session-data-redis` | The registry cache is legitimate. **Spring Session is redundant** with `SessionCreationPolicy.STATELESS`, yet still makes Redis a hard boot dependency. |
| JJWT | 0.12.6 | `security/jwt` | Fine. HS256 with a ≥32-byte secret is enforced. |
| springdoc-openapi | 3.1.0 | `/swagger-ui.html` (disabled in prod) | Fine. |
| Thymeleaf + `thymeleaf-extras-springsecurity6` | Boot-managed | `WebPageController`: 2 marketing/portal pages | Low value in an API backend; the "springsecurity6" extra is for Security 6 (check compatibility with Boot 4 / Security 7). |
| MongoDB starter | Boot-managed | **Unused**: auto-config excluded in `VidyaApplication` | Remove (dead dependency). |
| Spring Mail starter | Boot-managed | **Unused**: no `JavaMailSender` anywhere | Remove, or keep for the notifications module. |
| Spring REST Docs + Asciidoctor plugin | 2.2.1 | Plugin configured, **no `src/docs/asciidoc` sources** | Dead build config. |
| Lombok | 1.18.46 (processor path) | everywhere | Fine. |
| Testcontainers | 1.20.4 (pinned) | 10 integration classes | Fine, but currently failing (V18). |
| Razorpay / Cashfree | **no SDK, no HTTP client** | `master/payment/gateway` | **Simulated.** Order IDs are random UUIDs and no provider API is called. |
| Email / SMS / WhatsApp / push | — | — | **Not present.** |
| File storage (S3/local) | — | — | **Not present.** There is no upload endpoint anywhere. |
| Docker / CI | — | — | **Not present.** No Dockerfile, compose file or CI pipeline. |
| Frontend | — | — | Separate repository (not here). `docs/frontend-api/*` are integration notes for it. |

No scheduled jobs (`@Scheduled`), async processing (`@Async`) or outbound HTTP clients exist. There is one in-process `@EventListener` (`SchoolOnboardingWorkflowServiceImpl:275`) that reacts to payment events.

## 4. What the platform actually is today

```
Master DB (one)                                   Tenant DB (one per school)
─────────────────────────────                     ──────────────────────────────────
platform_user  ← ALL logins live here             tenant_user (+profile) ← no login; mirrors people
school, school_branch, school_* details           academic_year, academic_class, section
school_onboarding (+steps, activity, journey)     student, student_guardian, enrollments
subscription_plan / feature / overrides           staff
school_subscription, payment_transaction          subject, curriculum, assignments
tenant, tenant_provisioning                       room, bell_schedule, timetable, substitutions
community (+9 detail tables), hub                 school_holiday, working_day_config
school_support_query (+comments, escalations)     student_attendance
audit_log (security events only)                  exam_term/schedule/subject_config, marks, grading
                                                  student_report_card … (schema only)
```

**Identity model:** every human who can log in is a `platform_user` in the master DB, carrying a single enum role and an optional `school_id`. Tenant DBs hold people records (`tenant_user`, `student`, `staff`) with a loose `master_user_id` back-reference, but there is no resolver from the logged-in user to their `staff` or `student` row. That is why row-level scoping such as "teacher sees own sections" or "parent sees own child" cannot be implemented today.

**Tenancy model:** one tenant equals one school. `school_branch` exists only in the master DB; tenant data has no campus/branch dimension. An "organization with multiple schools" exists only as the master-side `community`/`hub` concepts, which model sales and field agents, not school groups.

## 5. Strengths worth preserving

- Clean master/tenant split with two transaction managers, consistently qualified (`@Transactional("tenantTransactionManager")`).
- `TenantRoutingDataSource` fails loudly when there is no tenant context. It does not silently fall back to master.
- `TenantContext` deliberately avoids `InheritableThreadLocal` and is cleared in `finally`.
- The tenant header is treated as an untrusted hint and validated against the user's school (`TenantAuthorizationServiceImpl`).
- Refresh tokens are hashed, rotated and reuse-detected (family revocation). Login lockout is implemented.
- Tenant schema discipline: UUID PKs, `version` columns, soft delete with partial unique indexes, CHECK constraints mirroring enums, FK coverage.
- Fail-fast two-phase bulk APIs (attendance, marks, timetable) with explicit conflict detection.
- Consistent `ApiResponse` / `PagedApiResponse` envelope, a central `GlobalExceptionHandler`, and correlation-ID MDC logging.
- Configurable grading scales (no hardcoded board grading). This is the right direction for multi-board support.

## 6. Discrepancies with existing documentation

| Claim (source) | Reality |
|---|---|
| "983 passed / 983 total (100%)" (root `PROJECT_OVERVIEW.md`) | 1,072 run, 9 errors, BUILD FAILURE. Integration tests don't run. |
| "724 Passed" badge (`README.md`) | Stale |
| Tenant migrations "currently at V14" / "V1–V12" | Tenant is at V18; master is at V27 |
| Step 26 "Grading Engine & Report Cards" in progress | Schema, entities and DTOs exist; the service is an interface only, with no implementation and no controller |
| "RBAC: Protect endpoints with `@PreAuthorize`" (invariant 7.4) | Not followed in 15 master controllers (136 endpoints) |
| README: "`TenantContextFilter`" | The class is `TenantResolutionFilter` |
| "Platform Audit & Compliance" complete | `audit_log` records security events (access denied, login) only. There's no business audit trail, and nothing at all in tenant DBs. |
| Root overview contains a duplicated, malformed mermaid Gantt block | Cosmetic |

## 7. Recommended next step

Approve **Phase P0** in [GAP_ANALYSIS_AND_ROADMAP.md](GAP_ANALYSIS_AND_ROADMAP.md). Those are small, high-leverage fixes: the seeder backdoor, master authorization, the V18 migration, payment verification, PII exposure, and a real `@SpringBootTest` security test harness. No architectural change is needed for P0. The P1 identity work (linking logins to staff, guardian and student rows) **is** an architectural change and needs explicit sign-off.
