# 🎓 Vidya — Multi-Tenant SaaS School Management System

[![Java](https://img.shields.io/badge/Java-25-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B-blue.svg?style=flat&logo=postgresql)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-red.svg?style=flat&logo=flyway)](https://flywaydb.org/)
[![Redis](https://img.shields.io/badge/Redis-Cache-critical.svg?style=flat&logo=redis)](https://redis.io/)
[![Build & Tests](https://img.shields.io/badge/Tests-724%20Passed%20(100%25)-success.svg?style=flat)]()
[![License](https://img.shields.io/badge/License-Proprietary-lightgrey.svg)]()

**Vidya** is an enterprise-grade, multi-tenant Software-as-a-Service (SaaS) School Management Platform designed to streamline and automate complete academic, administrative, and financial operations for K-12 schools, multi-branch educational institutions, and school chains.

---

## 🏛️ System Architecture

Vidya utilizes a **Database-per-Tenant** multi-tenancy model to guarantee strict data isolation, regulatory compliance, and high performance.

```
                          ┌────────────────────────┐
                          │    REST / Mobile API   │
                          └───────────┬────────────┘
                                      │
                      ┌───────────────▼───────────────┐
                      │    Spring Security + JWT      │
                      │    TenantContextFilter        │
                      └───────────────┬───────────────┘
                                      │
         ┌────────────────────────────┴────────────────────────────┐
         │                                                         │
┌────────▼─────────────────────┐                 ┌─────────────────▼─────────────────────┐
│       MASTER DOMAIN          │                 │              TENANT DOMAIN            │
│  (masterTransactionManager)  │                 │      (tenantTransactionManager)       │
├──────────────────────────────┤                 ├───────────────────────────────────────┤
│ • Platform RBAC & Identity   │                 │ • Tenant Identity (User & Profiles)   │
│ • Schools & Multi-Branches   │                 │ • Academic Structure (Year/Class/Sec) │
│ • Onboarding State Machine   │                 │ • Student Master & Academic History   │
│ • Subscription Plans & Tiers │                 │ • Staff & Teacher Management          │
│ • Master Billing & Payments  │                 │ • Teacher & Subject Assignments       │
│ • Tenant DB Provisioning     │                 │ • Class Teacher Assignments           │
│ • Master Flyway (V1 - V20)   │                 │ • Student Daily Attendance (Upsert)   │
│                              │                 │ • Tenant Dynamic Flyway (V1 - V12)    │
└──────────────┬───────────────┘                 └───────────────────┬───────────────────┘
               │                                                     │
        ┌──────▼──────┐                                     ┌────────▼────────┐
        │  Master DB  │                                     │ Dynamic Tenant  │
        │ (PostgreSQL)│                                     │ DB per School   │
        └─────────────┘                                     └─────────────────┘
```

### Multi-Tenancy Flow
1. Incoming HTTP requests contain `X-Tenant-ID: <tenantCode>` or a JWT tenant claim.
2. `TenantContextFilter` sets the thread-local `TenantContext.setTenantId(tenantCode)`.
3. `@Transactional(transactionManager = "tenantTransactionManager")` triggers `TenantRoutingDataSource` to dynamically route database queries to the target school's database connection pool.
4. `TenantContext.clear()` executes cleanly in a `finally` block after request execution.

---

## 🚀 Technology Stack

| Layer | Technology |
|---|---|
| **Core Language** | **Java 25** |
| **Framework** | **Spring Boot 4.1.1** (Spring MVC, Spring Data JPA, Spring Security) |
| **Database** | **PostgreSQL 16+** (Master DB + Database-per-tenant dynamic routing) |
| **Database Migrations** | **Flyway** (Independent Master and Tenant migration lifecycles) |
| **Caching & Messaging** | **Redis / Redisson** |
| **API Documentation** | **SpringDoc OpenAPI 3 / Swagger UI** |
| **Validation** | **Jakarta Validation / Hibernate Validator** |
| **Testing** | **JUnit 5**, **Mockito**, **AssertJ**, **MockMvc** (724+ automated tests) |

---

## 📦 Project Structure

```text
com.vidya
├── VidyaApplication.java
├── common/                  # Cross-cutting concerns
│   ├── api/                 # ApiResponse, PagedApiResponse, ErrorResponse
│   ├── exception/           # BusinessException, ResourceNotFoundException, etc.
│   └── util/                # CodeGenerator, DateUtils
├── config/                  # Master & Tenant DataSource configuration, Flyway config, Redis config
├── master/                  # Master Platform Domain (SaaS Operations)
│   ├── community/           # Multi-school community networks
│   ├── school/              # Schools & School Branches
│   ├── tenant/              # Tenant registry, datasource routing & provisioning
│   ├── onboarding/          # Multi-step school onboarding state machine
│   ├── subscription/        # SaaS subscription plans, features & tiers
│   ├── payment/             # Master platform billing & payments
│   └── audit/               # Platform-wide audit logs
├── security/                # JWT filters, TenantContextFilter, Spring Security RBAC
└── tenantdata/              # Tenant School Domain (Dynamic Database-per-Tenant)
    ├── academic/            # AcademicYear, AcademicClass, Section, Assignments
    ├── student/             # Student master record, enrollments, admissions
    ├── staff/               # Staff master record, employment, designations
    ├── subject/             # Subjects, Curriculums, Subject-Class mapping
    ├── assignment/          # Teacher-Subject & Class Teacher assignments
    ├── timetable/           # Period definitions, timetable entries
    └── attendance/          # Student attendance, daily sheets, bulk upsert
```

---

## ⚡ Implemented Modules & Capabilities

### Master SaaS Domain (`com.vidya.master`)
- **Platform Identity & Security**: Super Admin, Platform Admin, role hierarchy, stateless JWT authentication.
- **Community & Multi-Branch School Management**: Educational trusts, school networks, branch hierarchy.
- **Automated Tenant Provisioning**: Dynamic creation of tenant databases and automated execution of Flyway tenant migrations.
- **State-Machine Driven School Onboarding**: Step-by-step onboarding pipeline (Basic Info, Legal Docs, Academic Setup, Verification, Approval).
- **SaaS Subscriptions & Feature Toggles**: Standard, Pro, Enterprise plans, feature gating, and limit enforcement.
- **Platform Payments**: Payment transaction tracking, settlement reconciliations, and webhook integration.
- **Platform Audit**: Complete immutable audit trail of platform events and security operations.

### School Tenant Domain (`com.vidya.tenantdata`)
- **Tenant Identity**: Role-based access for `STUDENT`, `TEACHER`, `PARENT`, `STAFF`, and `SCHOOL_ADMIN`.
- **Academic Hierarchy**:
  - `AcademicYear` with state transitions (`DRAFT` → `ACTIVE` → `ARCHIVED` → `CLOSED`).
  - `AcademicClass` and `Section` management with capacity constraints.
- **Student Management**: Student master records, admission records, and historical `StudentAcademicAssignment` tracking.
- **Staff & Teacher Management**: Staff profiles, designations, department mapping, and employment status lifecycle.
- **Curriculum & Subjects**: Subject catalog, curriculums, and class-subject mappings.
- **Teacher Assignments**: Mapping teachers to subjects across specific academic years and sections.
- **Class Teacher Assignments**: Enforces single active class teacher per section invariant with historical tracking.
- **Student Daily Attendance**:
  - Daily attendance tracking (`PRESENT`, `ABSENT`, `LATE`, `HALF_DAY`, `EXCUSED`, `DELETED`).
  - **Transactional Bulk Upsert** (`POST /api/v1/attendance/bulk`) using natural key `studentId + academicYearId + attendanceDate`.
  - Database-level partial unique index (`WHERE deleted_at IS NULL`) preventing race conditions and duplicate entries.
  - Section daily attendance sheets and student attendance summary percentage calculations.

---

## 🛠️ Getting Started

### Prerequisites
- **JDK 25**
- **Maven 3.9+**
- **PostgreSQL 16+**
- **Redis 7+**

### Local Configuration
Create or configure your `application-local.yml` or export the following environment variables:

```bash
# Master Database Configuration
export MASTER_DB_URL=jdbc:postgresql://localhost:5432/vidya_master
export MASTER_DB_USERNAME=postgres
export MASTER_DB_PASSWORD=postgres

# Tenant Databases Host Configuration
export TENANT_DB_HOST=localhost
export TENANT_DB_PORT=5432
export TENANT_DB_USERNAME=postgres
export TENANT_DB_PASSWORD=postgres

# Redis Configuration
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=6379

# JWT Security
export JWT_SECRET=your-256-bit-secret-your-256-bit-secret-key-goes-here
```

### Build & Run

```bash
# Clone the repository
git clone https://github.com/Chhavidutt-Bhardwaj/vidya_Backend.git
cd Vidya-Backend-Project

# Run all unit and integration tests
mvn clean test

# Run the Spring Boot application
mvn spring-boot:run
```

---

## 📖 API Documentation

Once the application is running locally:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 🧪 Testing & Code Quality

The test suite covers full domain state machines, repository isolation, service validation boundaries, optimistic locking, and MockMvc REST endpoints.

```bash
# Execute entire test suite
mvn test

# Run a specific test class
mvn test -Dtest=StudentAttendanceServiceTest
```

**Test Execution Results:**
```text
Tests run: 724, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 📋 Architectural Invariants & Coding Standards

1. **Dual Transaction Managers**:
   - Master DB Service methods: `@Transactional(transactionManager = "masterTransactionManager")`
   - Tenant DB Service methods: `@Transactional(transactionManager = "tenantTransactionManager")`
2. **Entity Standards**:
   - Primary Keys: Always `UUID` (`@GeneratedValue(strategy = GenerationType.UUID)`).
   - Optimistic Locking: `@Version @Column(name = "version") private Long version;`.
   - Immutable Business Codes: Stable human-readable codes generated via `CodeGenerator` (e.g. `ATT-XXXX`, `STU-XXXX`).
   - Soft-Deletes: Supported via `@Column(name = "deleted_at") private Instant deletedAt;` and partial unique indexes `WHERE deleted_at IS NULL`.
3. **Fail-Fast Validation**:
   - Bulk APIs must perform a 100% non-mutating validation phase before any database persistence occurs.
4. **Standard API Envelope**:
   - `ApiResponse<T>` for individual objects / mutations.
   - `PagedApiResponse<T>` for paginated queries.

---

## 📄 License

Proprietary — All rights reserved © Vidya Platform.
