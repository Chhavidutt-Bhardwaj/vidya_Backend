# Missing Features: Gap Analysis

Each gap compares what exists in the repository (see MODULE_STATUS) with the target school ERP from the product brief. Priorities follow the roadmap scale: P0 security and integrity · P1 core operations · P2 academics, HR and operations · P3 communication, compliance and analytics · P4 AI and advanced. Complexity: S / M / L / XL.

## 1. Foundational gaps (these block many modules)

| Gap | Exists today | Missing | Business impact | Technical impact | Depends on | Recommended solution | Pri | Cx |
|---|---|---|---|---|---|---|---|---|
| **G-1 Identity ↔ person linkage** | Logins in master `platform_user`. People in tenant `tenant_user` / `staff` / `student`. Loose `master_user_id`. | Resolver from authenticated user to staff, student and guardian rows. Guardian login. Student login. | No teacher-scoped access, no parent/student portal, no "my classes". | Touches security, every tenant service and data model | — | `CurrentActor` service resolved once per request (cached), carrying `{userId, tenantCode, staffId?, studentId?, guardianId?, roles, permissions}`. Link guardians to `tenant_user`. Invitation-based account creation from staff, guardian and student records. | P1 | L |
| **G-2 Configurable RBAC per school** | Enum roles and static permissions | DB-backed roles and permissions per tenant, multi-role users, scope (school / class / section / department) | Schools can't add Librarian, HOD, Transport Manager… | Replace hand-written `@PreAuthorize` role lists | G-1 | Keep `PlatformRole` for platform staff. Add tenant-side `role`, `permission`, `role_permission`, `user_role_assignment(scope_type, scope_id)`. Use `@PreAuthorize("@authz.can('ATTENDANCE_MARK', #sectionId)")`. Reuse the design of the community role system. | P1 | L |
| **G-3 Tenant schema upgrade path** | Migrations at provisioning only | Migrating existing tenants on deploy, with status tracking | Every release with a tenant migration breaks existing schools | Deployment process | — | Startup or admin-triggered `TenantMigrationRunner` iterating active tenants, recording per-tenant schema version in `tenant`, blocking traffic to a tenant whose migration failed. | P0 | M |
| **G-4 Tenant credential management** | Env-var reference per tenant | Generated credentials stored in a secret store (Vault / AWS Secrets Manager / encrypted master column) | Onboarding a school needs a redeploy | Ops | — | A new `CredentialResolver` implementation. The interface already exists. | P1 | M |
| **G-5 Business audit trail** | Security events only | Who/when/before/after for marks, attendance, status, fees and payroll | Disputes, board inspections, fraud | New tenant table plus listener | G-1 | Tenant `audit_event` table written through a JPA entity listener or explicit service calls for sensitive aggregates. Enable tenant `AuditorAware`. | P1 | M |
| **G-6 Async, jobs and outbox** | None | Scheduler (ShedLock for multi-node), transactional outbox, retry | Reminders, fines, notifications, report generation impossible | Infra | — | Spring `@Scheduled` + ShedLock (Postgres) and an outbox table per tenant DB. Avoid adding a message broker until needed. | P1 | M |
| **G-7 File storage** | None | Upload, download and virus scan. Signed URLs. Access control. | Admissions docs, certificates, report-card PDFs | New abstraction | G-1 | `FileStorage` interface (S3-compatible plus local dev). `document` table per tenant with owner, type, expiry and ACL. | P1 | M |
| **G-8 Organization and campus model** | One tenant = one school. `school_branch` master-only. | School groups owning many tenants. Campus dimension in tenant data. | Chains and trusts can't be managed centrally | Schema-wide | — | **Decision needed.** Option A: campus = separate tenant, plus an org-level reporting store. Option B: add `campus_id` to tenant tables. Decide before fees and attendance data grow. | P1 (decision) | L |
| **G-9 Feature entitlement enforcement** | Plans, features and overrides exist | Runtime check on tenant APIs | Can't monetize tiers | Cross-cutting | — | `@RequiresFeature("FEES")` aspect backed by a cached tenant entitlement snapshot | P2 | S |

## 2. Module gaps

### C. Student lifecycle (P1)
| Exists | Missing | Recommendation | Cx |
|---|---|---|---|
| Direct admission, student CRUD, status state machine, per-student guardians | Enquiry → application → document verification → approval/rejection → admission fee → registration. Shared guardians (siblings). Medical and emergency records (restricted). Previous schooling. **Year-end promotion** (bulk, with detained/promoted/graduated outcomes). Transfer certificate and withdrawal. Alumni. | Build `admission_enquiry` and `admission_application` as a state machine feeding the existing `admitStudent`. Promotion as a bulk two-phase operation (the pattern already used by attendance and marks). Fix DB-2, DB-4 and DB-5 first. | L |

### E. Attendance (P1 for student/staff daily, P2 for the rest)
| Exists | Missing | Cx |
|---|---|---|
| Student daily attendance, bulk upsert, holiday validation | Teacher scope (G-1), period-wise attendance (timetable exists to drive it), staff attendance, leave application and approval (student and staff), late/early, correction-with-approval and audit, parent notification (G-6 + L), biometric import API | M–L |

### G. Fees and finance (P1, entirely missing)
Design rules from the brief: auditable, append-only, reversals instead of edits.

Minimum viable workflow:
1. `fee_head` (category), `fee_structure` per class and year, with installment schedule
2. `student_fee_assignment` (with discounts, scholarships and concessions as separate approved records)
3. `fee_invoice` + `fee_invoice_line`, generated per installment (job, G-6)
4. `fee_payment` (cash, cheque, online), `fee_receipt` (immutable, sequential numbering per school per year), `fee_payment_reversal`
5. Late fine rules (job), outstanding and defaulter reports
6. Online payments: **reuse the gateway abstraction from `master/payment` after fixing S-03**, with per-school merchant credentials.

Complexity XL. Depends on G-1, G-5, G-6 and a decision on G-8.

### F. Examinations and results (P2)
| Exists | Missing | Cx |
|---|---|---|
| Grading scales, terms, schedules, paper configs, marks with grade calc, lock | Fix V18, then report-card service and controller. Weightage across terms and components (configurable per board). Result approval → publish → lock. Controlled unlock with reason and audit. CSV/XLSX bulk upload. PDF generation (G-7). QR verification. | L |

### H. Staff and HR (P2)
Staff profile extensions (qualifications, contracts, documents, bank details encrypted), departments and designations as entities, leave policies and balances, salary structures, payroll runs with approval, payslips (PDF), exit workflow. Complexity XL.

### D. Academics (P2)
Lesson plans, homework and assignments with submissions (needs G-7), learning outcomes and competencies mapped to subjects, timetable auto-generation (constraint solver, P4).

### L. Communication (P3, but notifications infrastructure is P1 via G-6)
Notices and circulars with audience targeting (class, section, role), acknowledgement tracking, notification templates, channel adapters (email via the existing mail starter, SMS and WhatsApp providers to be selected), PTM scheduling, delivery logs.

### I / J / K. Transport, library, inventory (P2–P3)
Independent bounded modules. Each is M–L. Build after fees (transport fee depends on fee heads).

### M. Safety and wellbeing (P3)
Incidents, discipline, counselling (restricted permission plus audit on read), pickup authorization, consent records, emergency broadcast (uses L).

### N. Documents and certificates (P2)
Template-driven certificates (bonafide, character, TC) with sequential numbering, QR verification endpoint (public, minimal data), expiry tracking. Depends on G-7.

### O. Reports and analytics (P3)
School dashboards (strength, attendance, fee collection, results). Use read-optimized SQL views per tenant. Org-level aggregation depends on G-8.

### P. Compliance (P3)
Board-configurable checklists, evidence (G-7), deadlines, UDISE+ export mapping. **Do not build direct government-portal integrations without confirming that official APIs and permitted mechanisms exist.** Start with export files in the published formats.

### Phase 6. AI (P4)
Prerequisites: G-1, G-2 and G-5, so AI calls can be scoped to the caller's permissions and audited. Start with read-only features (natural-language reports over permitted data, parent message drafting with human send). No AI writes to marks, fees, payroll, admissions or discipline.
