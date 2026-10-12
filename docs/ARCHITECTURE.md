# Vidya — Architectural Specification & System Guide

## 1. System Overview & Architecture
Vidya is a multi-tenant Software-as-a-Service (SaaS) School Management System built with a modular monolith architecture in Java 25 and Spring Boot 4.1.1. It provides physical database-per-tenant isolation for K-12 schools alongside a centralized Master platform database.

### 1.1 Dual-Database Strategy & Transaction Routing
- **Master Database (`masterTransactionManager`)**: Stores global platform identity, multi-school community networks, school branches, subscription tiers and feature gating, onboarding workflow state machines, and master audit logs.
- **Tenant Database (`tenantTransactionManager`)**: Each school operates on a physically isolated PostgreSQL database. Dynamic routing is managed by `TenantRoutingDataSource` using `TenantContext.getTenantId()`.
- **Flyway Database Migrations**:
  - Master DB migrations: `src/main/resources/db/migration/master/` (currently at `V20`)
  - Tenant DB migrations: `src/main/resources/db/migration/tenant/` (currently at `V14`)

---

## 2. Tenant Database Schema & Migrations (V1 to V14)

### 2.1 Applied Tenant Migrations
- **V1**: `tenant_metadata`
- **V2**: `academic_year`
- **V3**: `tenant_user`, `tenant_user_profile`
- **V4**: `student`
- **V5**: `academic_class`, `section`
- **V6**: `staff`, `staff_department`
- **V7**: `subject`, `curriculum`, `curriculum_subject`
- **V8**: `teacher_subject_assignment`
- **V9**: `timetable` initial foundation
- **V10**: `student_academic_assignment`
- **V11**: `class_teacher_assignment`
- **V12**: `student_attendance`
- **V13**: `timetable_hardening` — Canonical timetable and bell schedule entities:
  - `room`: Physical rooms, laboratories, and halls with type and capacity.
  - `bell_schedule`: Master daily timing definitions with status (`DRAFT`, `ACTIVE`, `ARCHIVED`).
  - `bell_schedule_period`: Period sequences, start/end times, and period types (`TEACHING`, `BREAK`, `HOMEROOM`, `ASSEMBLY`).
  - `timetable`: Root timetable entity referencing `academic_year` and `bell_schedule`, with status (`DRAFT`, `PUBLISHED`, `ACTIVE`, `ARCHIVED`, `CLOSED`), `effective_from`, and `effective_to`.
  - `timetable_entry`: Individual schedule slots referencing `timetable`, `bell_schedule_period`, `section`, `subject`, `staff` (teacher), and `room` with day-of-week checks and slot unique constraints (`uk_tte_section_slot`, `uk_tte_teacher_slot`, `uk_tte_room_slot`).
- **V14**: `timetable_substitution` — Daily timetable substitution management:
  - `timetable_substitution`: Daily substitution records referencing slot natural key (`timetable_id`, `substitution_date`, `period_id`, `section_id`), substitution type (`TEACHER_SUBSTITUTE`, `ROOM_CHANGE`, `CANCELLATION`), substitute teacher, substitute room, and status (`PENDING`, `ACTIVE`, `CANCELLED`).
  - Partial unique indexes ensuring no double-booking of substitute teachers or rooms on a single date.
- **V15**: `academic_calendar` — School calendar configuration and holiday management:
  - `working_day_config`: Per-academic-year weekday flags (`monday`..`sunday`), Saturday pattern (`ALL`, `FIRST_THIRD_FIFTH`, `SECOND_FOURTH`), and partial unique index ensuring one active config per year.
  - `school_holiday`: Academic year holidays with start/end date ranges, holiday type (`NATIONAL`, `REGIONAL`, `LOCAL`, `INSTITUTIONAL`, `EMERGENCY`), and non-deleted check constraints.
- **V16**: `attendance_non_working_override` — Attendance hardening on non-working days:
  - Adds `non_working_day_override` (BOOLEAN DEFAULT FALSE) and `override_reason` (VARCHAR(255)).
  - Check constraint `chk_student_attendance_override_reason`: rejects non-working day overrides where `override_reason` is NULL or blank when `non_working_day_override` is TRUE.

### 2.2 Saturday Patterns & Five-Saturday Month Handling
- `SaturdayPattern` enum values:
  - `ALL`: Every Saturday is a working day.
  - `FIRST_THIRD_FIFTH`: 1st, 3rd, and 5th Saturdays of each month are working days; 2nd and 4th are weekly offs.
  - `SECOND_FOURTH`: 2nd and 4th Saturdays of each month are working days; 1st, 3rd, and 5th are weekly offs.
- Ordinal computation: `saturdayOrdinal = (dayOfMonth - 1) / 7 + 1`. In months with 5 Saturdays (29th, 30th, or 31st), the 5th Saturday calculates as ordinal 5, making it active for `FIRST_THIRD_FIFTH` and weekly off for `SECOND_FOURTH`.

### 2.3 Attendance Non-Working Day Overrides & Authority
- Marking attendance on non-working days (weekly off or holiday) requires `allowNonWorkingDay = true` and a non-blank `overrideReason`.
- Overriding is strictly guarded by the `ATTENDANCE_OVERRIDE_NON_WORKING` permission, granted exclusively to `SCHOOL_ADMIN` and `SCHOOL_OWNER` roles (`PLATFORM_ADMIN` and `TEACHER` roles cannot override).

### 2.4 Timezone & Shared Clock
- Configurable application timezone: `vidya.school.timezone` (default: `Asia/Kolkata`).
- Shared `Clock` bean (`com.vidya.config.datetime.ClockConfig`) injected across `StudentAttendanceServiceImpl` and `TimetableSubstitutionServiceImpl` to ensure deterministic date/time evaluations in testing and production.

---

## 3. Security Architecture & RBAC

### 3.1 Authentication & Token Model
- **Stateless JWT**: Standardized Bearer token authentication.
- **Token Types**: Distinct claims for `ACCESS` (short-lived, 15m) and `REFRESH` (long-lived, 7d).
- **Environment Secrets**: `vidya.security.jwt.secret` configured via `${JWT_SECRET}` in production (fail-fast on startup if missing; default fallback permitted only when `dev` or `test` Spring profile is explicitly active).

### 3.2 Multi-Tenant Authorization (`TenantAuthorizationService`)
- Tenant context is passed via the `X-Tenant-ID` request header and resolved by `TenantResolutionFilter`.
- Every incoming request to tenant endpoints is authorized via `TenantAuthorizationService.canAccessTenant(platformUserId, schoolId)`:
  - `SUPER_ADMIN`: Global bypass; granted access across all tenants.
  - `PLATFORM_ADMIN`: Access granted for administrative operations.
  - `COMMUNITY_ADMIN`: Access verified through a single SQL query (`SchoolOnboardingRepository.existsByPlatformUserIdAndSchoolId`).
  - `SCHOOL_ADMIN`: Access permitted strictly to their linked school.
  - `TEACHER`: Access permitted strictly to their affiliated school.
  - `STUDENT` & `PARENT`: Access currently **denied** (`canAccessTenant` returns `false`). Dedicated student/parent portals are scheduled for future steps.
  - All other roles: Default deny.

---

## 4. Package Structure

```text
com.vidya
├── VidyaApplication.java
├── common/                  # Cross-cutting utilities, API envelopes, exception hierarchy
│   ├── api/                 # ApiResponse, PagedApiResponse, ApiResponseCode
│   ├── exception/           # BusinessException, DuplicateResourceException, etc.
│   └── util/                # CodeGenerator, SecurityUtils, DateUtils
├── config/                  # Master & Tenant DataSource routing, JPA, Redis configuration
├── master/                  # Master Platform SaaS Operations
│   ├── audit/               # Centralized audit logging
│   ├── community/           # Multi-school community networks
│   ├── onboarding/          # Multi-step school onboarding pipeline
│   ├── payment/             # Platform billing and payment verification
│   ├── school/              # School and branch directory
│   ├── subscription/        # Plan definitions and feature gating
│   ├── tenant/              # Dynamic tenant database provisioning & registry
│   └── workflow/            # Master state-machine workflows
├── security/                # JWT provider, filters, RBAC permissions, and isolation
└── tenantdata/              # Tenant Academic & Operational Domain
    ├── academic/            # AcademicYear, AcademicClass, Section
    ├── assignment/          # Teacher-Subject and Class Teacher assignments
    ├── attendance/          # Student daily attendance and bulk upsert
    ├── staff/               # Staff directory, designations, and departments
    ├── student/             # Student records, enrollments, and academic assignments
    ├── subject/             # Subjects, Curriculums, and class syllabi
    └── timetable/           # Rooms, Bell Schedules, Timetables, Entries, and Substitutions
```

---

## 5. Technology Dependencies & Testing Requirements

- **Caching & Sessions**: Uses Spring Session Redis (`spring-session-data-redis` with Lettuce driver), **not** Redisson.
- **Testing Infrastructure**: Integration tests (`*PostgresIntegrationTest`) use **Testcontainers** to spin up real PostgreSQL 16 containers. Docker / Docker Desktop is required to run the full test suite.
- **SQL Statement Count Invariance**: Timetable grid and occupancy endpoints enforce strict $O(1)$ query complexity verified against Testcontainers via Hibernate Statistics.

---

## 6. Known Domain Limitations & Architectural Trade-offs

1. **No Elective / Split-Batch Groups**: Currently, each timetable slot supports exactly one subject, one teacher, and one room per section. Split classes and elective subject groupings are planned for a subsequent release.
2. **Bulk Upsert Replaces Entry Identifiers**: `POST /api/v1/timetables/{id}/entries/bulk` replaces section schedule entries atomically in a fail-fast two-phase commit, generating new primary keys for replaced slots.
3. **No Holiday Calendar Integration**: Timetable day and weekly views currently do not automatically skip public or school holidays (Holiday Calendar module is scheduled for a future step).
4. **Natural-Key Slot Referencing for Substitutions**: `timetable_substitution` references schedule slots via natural key (`timetable_id`, `substitution_date`, `period_id`, `section_id`) rather than `timetable_entry_id` foreign keys, ensuring substitutions remain resilient across timetable re-upserts.
