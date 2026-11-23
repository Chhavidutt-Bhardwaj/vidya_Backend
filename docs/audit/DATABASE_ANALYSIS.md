# Database Analysis

Sources: `src/main/resources/db/migration/master` (V1–V27) and `.../tenant` (V1–V18), plus JPA entities. No schema was modified during this audit.

## 1. Master database (one instance)

| Area | Tables | Notes |
|---|---|---|
| Identity | `platform_user`, `refresh_token` | Single `platform_role` enum column plus `school_id`. All logins for all schools live here. |
| Schools | `school`, `school_branch`, `school_address`, `school_contact_detail`, `school_board_affiliation`, `school_document` | Board affiliation is normalized (good). `school_document` stores metadata, but there's no file-storage integration. |
| Onboarding | `school_onboarding`, `onboarding_step_data`, `onboarding_activity` (+ V26 journey columns) | State machine |
| Tenancy | `tenant`, `tenant_provisioning` | Credentials stored as env-var *reference names* |
| Subscription | `subscription_plan`, `subscription_feature`, `subscription_plan_feature`, `school_subscription`, `school_subscription_feature_override` | Feature catalogue exists. **Feature gating is not enforced** on tenant APIs (no check found). |
| Payments | `payment_transaction` | Platform billing (school → Vidya), **not** student fees |
| Community (sales/field network) | `community`, `community_member`, 9 detail tables (personal, professional, address, bank, payroll, commission, commission_ledger, login_history), `community_role_definition`, `community_permission`, `community_role_permission`, `community_role_audit_log` | `community_role_audit_log` is created in V21 and again in V22 (`IF NOT EXISTS`). Harmless duplication. |
| Hub / support | `hub`, school-relationship/escalation tables, `school_support_query`, `school_query_comment`, `school_query_escalation_log` | |
| Audit | `audit_log` | Written only by `SecurityAuditServiceImpl` (security events) |
| Seed data | V18, V22 | **Seeds real-looking users with BCrypt of `Password@123` into every environment** (see SECURITY_AUDIT S-01) |

## 2. Tenant database (one per school)

| Area | Tables |
|---|---|
| Meta | `tenant_metadata` |
| People | `tenant_user`, `tenant_user_profile`, `student`, `student_guardian`, `staff` |
| Academic structure | `academic_year`, `academic_class`, `section` |
| Enrollment | `student_academic_assignment` (V5), `student_academic_enrollment`, `student_subject_enrollment` (V10) |
| Curriculum | `subject`, `curriculum`, `curriculum_subject`, `class_subject_assignment`, `teacher_subject_assignment`, `class_teacher_assignment` |
| Timetable | `room`, `bell_schedule`, `bell_schedule_period`, `timetable`, `timetable_entry`, `timetable_substitution` |
| Calendar | `school_holiday`, `working_day_config` |
| Attendance | `student_attendance` (+ V16 non-working-day override) |
| Exams | `grading_scale`, `grading_scale_tier`, `exam_term`, `exam_schedule`, `exam_subject_config`, `student_mark_entry` |
| Report cards | `student_report_card`, `report_card_subject_result`, `report_card_remark` (**V18: broken migration**) |

### 2.1 Constraint and index discipline (good)

Every tenant migration from V4 onward declares FKs, CHECK constraints mirroring Java enums, and indexes. Soft-delete uniqueness uses partial indexes (`WHERE deleted_at IS NULL`) in the newer tables. All tables carry `version` (optimistic locking), `created_at/updated_at`, and `created_by/updated_by`.

## 3. Defects

| ID | Severity | Finding | Location |
|---|---|---|---|
| DB-1 | **Critical** | FK targets a non-existent table `class_section` (actual table: `section`). Tenant Flyway fails at V18, so **no new tenant can be provisioned**, and every Testcontainers test errors. PostgreSQL DDL is transactional, so V18 has never applied anywhere and it is safe to correct the file in place. Verify that no environment has a manually patched V18 before doing so. | `tenant/V18__create_grading_and_report_cards.sql:11` |
| DB-2 | High | **Guardian is per-student.** `student_guardian.student_id` is a NOT NULL FK, so siblings duplicate their parent rows. There is no link from guardian to a login (`tenant_user`), so a parent portal, sibling discounts and "one parent, many children" have no data foundation. | tenant V10 |
| DB-3 | High | **No campus/branch dimension** in any tenant table. `school_branch` exists only in master. | tenant all |
| DB-4 | Medium | Early tables use **full** UNIQUE constraints despite soft delete. A soft-deleted student's `admission_number`, or a deleted user's `email`, can never be reused. | `tenant/V3` (`uq_tenant_user_email`, `uq_tenant_user_code`), `tenant/V4` (`uq_student_admission_number`, `uq_student_code`) |
| DB-5 | Medium | `tenant_user.email` is NOT NULL and UNIQUE. Young students often have no email, and siblings may share a parent email. This forces fake emails. | tenant V3 |
| DB-6 | Medium | Two overlapping enrollment models: `student_academic_assignment` (V5) and `student_academic_enrollment` (V10), with separate controllers. Needs a single source of truth for "which section is this student in this year". | tenant V5, V10 |
| DB-7 | Medium | Plaintext PII: `community_bank_detail.account_number`, `community_personal_detail.pan_number` / `aadhaar_number`. No column encryption and no masking. | master V21 |
| DB-8 | Medium | No tenant-side audit table. Changes to marks, attendance and student status are not historically traceable beyond `updated_by` / `updated_at` (last writer only). | tenant |
| DB-9 | Low | `student` has no DOB, gender, blood group, medical or previous-school fields. DOB and gender live in the generic `tenant_user_profile`, with no medical or emergency structure beyond one contact. | tenant V3/V4 |
| DB-10 | Low | `ON DELETE CASCADE` from `student` to `student_guardian`. Harmless while deletes are soft, but a hard delete would silently erase guardian history. | tenant V10 |
| DB-11 | Low | `audit_log` and `community_role_audit_log` are unpartitioned, with no retention policy. | master V16, V21 |
| DB-12 | Medium | `@EnableJpaAuditing` is configured only for the master EMF (`masterAuditorAware`). Tenant entities get `created_by` / `updated_by` only where a service sets them by hand (calendar, holiday, timetable, room, bell schedule, substitution). Students, staff, attendance and marks have null authorship. | `config/database/MasterDataSourceConfig.java:48,176` |
| DB-13 | **High** | **Existing tenant DBs are never migrated.** `TenantMigrationService` runs only during provisioning, so schema changes reach new schools only. | `tenant/provisioning/TenantProvisioningService.java` |
| DB-14 | ~~Low~~ Withdrawn | *Correction:* an earlier version of this audit claimed "one current academic year" was not DB-enforced. That was wrong. Tenant `V5` adds `deleted_at` and `CREATE UNIQUE INDEX idx_academic_year_unique_current … WHERE is_current AND deleted_at IS NULL`. | tenant V5 |

## 4. Query / ORM observations

- `open-in-view: false`: good. It prevents lazy loading in controllers.
- Timetable grid queries have explicit statement-count tests (intended to prove there is no N+1), but those tests are among the 9 currently erroring.
- Unbounded reads: `AnalyticsServiceImpl` loads **all** `school_subscription` and **all** `school_onboarding` rows into memory (`findAll()`, lines 156 and 185) to compute KPIs. Replace with aggregate SQL.
- 35 endpoints return `List<…>` without pagination. Most are naturally bounded (periods of a bell schedule, guardians of a student). Review: community detail lists, holidays, class-teacher assignments by year.
- `TenantAuthorizationServiceImpl` runs 2–4 master queries **per tenant request** (user, tenant, optionally onboarding). Cache per request/token or embed verified claims.

## 5. How current data maps to the target domain

| Target concept | Present? | Where / gap |
|---|---|---|
| Organization / tenant | Partly | `tenant` = one school. No school-group entity. |
| Schools / campuses | School yes, campus no | `school_branch` is master-only and unused by tenant data |
| Academic years | Yes | `academic_year` with status lifecycle |
| Students / guardians | Yes, with gaps | DB-2, DB-5, DB-9 |
| Staff / teachers | Basic | `staff`: no contracts, qualifications, documents, bank or salary |
| Classes / sections | Yes | |
| Subjects / curriculum | Yes | |
| Attendance | Students daily only | No period-wise, staff, or leave |
| Timetable | Yes | |
| Exams / marks | Yes | Report cards schema only |
| Fees and payments | **No** | `payment_transaction` is platform billing |
| Payroll, transport, library, inventory, notifications, documents | **No** | |
| Audit logs | Security only | DB-8 |
