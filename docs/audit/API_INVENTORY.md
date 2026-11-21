# API Inventory

Generated from controller source at revision `9c6d509` by extracting `@RequestMapping`, verb mappings and `@PreAuthorize` per handler. **363 handler methods in 49 controllers.**

The full per-endpoint table is in §4. §1–§3 summarise the findings.

## 1. Conventions in use

| Aspect | Status |
|---|---|
| Versioning | `/api/v1/...` everywhere (good) |
| Envelope | `ApiResponse<T>` { code, message, data, correlationId } / `PagedApiResponse<T>`, used consistently |
| Pagination | Spring `Pageable` with `@PageableDefault` on list endpoints. 35 endpoints return unpaginated `List<…>`. |
| Errors | `GlobalExceptionHandler`: 400 validation / `IllegalArgumentException`, 401, 403, 404, 409 duplicate, 422 business/state, 500 |
| Docs | springdoc `@Operation` / `@Tag` on most controllers. Swagger is disabled in prod. |
| DTOs | Request/response DTOs plus hand-written mappers almost everywhere. **Exceptions below.** |
| Auth | Bearer JWT. Tenant chosen through the `X-Tenant-ID` header or derived from the user's school. |

## 2. Findings

| ID | Severity | Finding |
|---|---|---|
| API-1 | **Critical** | **136 endpoints in 15 master controllers have no role or permission check** (`authenticated only` below): `SchoolController`, `SchoolDetailController`, `SchoolRelationshipController`, `OnboardingController`, `OnboardingOrchestrationController`, `SchoolOnboardingWorkflowController`, `SubscriptionPlanController`, `SchoolSubscriptionController`, `PaymentController`, `CommunityController`, `CommunityRoleController`, `CommunityDetailController`, `HubController`, `SchoolSupportQueryController`, `AnalyticsController`. No service in `master/` performs authorization either. |
| API-2 | **Critical** | `POST /api/v1/webhooks/payments/{razorpay,cashfree}` is `permitAll`, and signature verification is skipped when the signature header is absent or the configured secret equals the default. `POST /api/v1/payments/verify` succeeds when no signature is sent. See SECURITY_AUDIT S-03. |
| API-3 | High | **JPA entities returned directly**: `CommunityDetailController` returns `CommunityBankDetail`, `CommunityPayroll`, `CommunityCommission`, `CommunityProfessionalDetail`, `CommunityAddress`, `CommunityLoginHistory`. This leaks internal fields and unmasked bank numbers, and risks lazy-loading errors. |
| API-4 | Medium | **Duplicate route aliases**: 14 tenant controllers are mapped twice (`/api/v1/tenant/x` **and** `/api/v1/x`). `StudentAdmissionController` is mapped under 4 prefixes. That doubles the attack and documentation surface and splits client usage. Pick one canonical prefix and deprecate the other. |
| API-5 | Medium | Two overlapping enrollment APIs: `StudentAcademicAssignmentController` (`/students/{id}/academic-assignments`) and `StudentEnrollmentController` / `ClassStudentController`. Clients can't tell which one is authoritative (see DATABASE_ANALYSIS DB-6). |
| API-6 | Medium | `@PreAuthorize` expressions are hand-written role lists repeated on every method (`hasAuthority('X') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')`). Error-prone and impossible to configure per school. Should be permission-only, with roles mapped to permissions in one place. |
| API-7 | Medium | Role-gated tenant endpoints never mention `PRINCIPAL`, `COORDINATOR`, `ACCOUNTS`, `SUBJECT_TEACHER`, `PARENT`, `STUDENT`. Combined with `TenantAuthorizationServiceImpl` denying those roles tenant access, **no tenant API is usable by a principal, accountant, parent or student**. |
| API-8 | Medium | Authenticated `POST /api/v1/communities/{id}/details/login-history` lets the client *write* its own login-history records. Login history should be server-generated only. |
| API-9 | Low | `GET /api/v1/analytics/*` computes KPIs from full-table loads (see DATABASE_ANALYSIS §4). |
| API-10 | Low | No idempotency keys on payment initiation or bulk endpoints, so client retries can double-submit. |
| API-11 | Info | No dummy or hardcoded responses found in tenant controllers. The payment **gateways** return fabricated provider data (`order_rzp_<uuid>`), so `/payments/initiate` produces orders that do not exist at Razorpay or Cashfree. |

## 3. Endpoint counts by module (exact)

| Module | DB | Endpoints | No role/permission check | Public |
|---|---|---|---|---|
| security (auth) | master | 4 | 0 | 3 (login, refresh, logout) |
| web (HTML pages) | — | 6 | 0 | 6 |
| school | master | 36 | **36** | 0 |
| onboarding + orchestration | master | 11 | **11** | 0 |
| workflow | master | 6 | **6** | 0 |
| subscription | master | 20 | **20** | 0 |
| payment (+ webhooks) | master | 8 | **6** | 2 (webhooks) |
| community | master | 37 | **37** | 0 |
| hub | master | 9 | **9** | 0 |
| support | master | 8 | **8** | 0 |
| analytics | master | 3 | **3** | 0 |
| academic | tenant | 26 | 0 | 0 |
| student | tenant | 31 | 0 | 0 |
| staff | tenant | 8 | 0 | 0 |
| user | tenant | 6 | 0 | 0 |
| subject | tenant | 19 | 0 | 0 |
| assignment | tenant | 29 | 0 | 0 |
| timetable | tenant | 49 | 0 | 0 |
| calendar | tenant | 9 | 0 | 0 |
| attendance | tenant | 11 | 0 | 0 |
| examination | tenant | 27 | 0 | 0 |
| reportcard | tenant | 0 | — | — |
| **Total** | | **363** | **136** | **11** |

## 4. Full endpoint table

Legend: `perm:` = `hasAuthority(...)`, `roles:` = `hasRole(...)` (P_ = PLATFORM_, S_ = SCHOOL_). **authenticated only** = no role or permission check of any kind.

#### `AnalyticsController` (analytics)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| GET | `/api/v1/analytics/kpis` | `getPlatformKpis` | authenticated only |
| GET | `/api/v1/analytics/revenue-trends` | `getRevenueTrends` | authenticated only |
| GET | `/api/v1/analytics/onboarding-funnel` | `getOnboardingFunnel` | authenticated only |

#### `CommunityController` (community)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/communities` | `createCommunity` | authenticated only |
| GET | `/api/v1/communities/{id}` | `getCommunityById` | authenticated only |
| GET | `/api/v1/communities/code/{code}` | `getCommunityByCode` | authenticated only |
| GET | `/api/v1/communities` | `getAllCommunities` | authenticated only |
| PUT | `/api/v1/communities/{id}` | `updateCommunity` | authenticated only |
| PATCH | `/api/v1/communities/{id}/status` | `changeCommunityStatus` | authenticated only |
| POST | `/api/v1/communities/{id}/members` | `addMember` | authenticated only |
| GET | `/api/v1/communities/{id}/members` | `getCommunityMembers` | authenticated only |
| PATCH | `/api/v1/communities/{id}/members/{memberId}/role` | `updateMemberRole` | authenticated only |
| DELETE | `/api/v1/communities/{id}/members/{memberId}` | `removeMember` | authenticated only |

#### `CommunityDetailController` (community)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/communities/{communityId}/details/personal` | `createPersonalDetail` | authenticated only |
| PUT | `/api/v1/communities/{communityId}/details/personal` | `updatePersonalDetail` | authenticated only |
| GET | `/api/v1/communities/{communityId}/details/personal` | `getPersonalDetail` | authenticated only |
| POST | `/api/v1/communities/{communityId}/details/professional` | `createProfessionalDetail` | authenticated only |
| GET | `/api/v1/communities/{communityId}/details/professional` | `getProfessionalDetail` | authenticated only |
| POST | `/api/v1/communities/{communityId}/details/addresses` | `addAddress` | authenticated only |
| GET | `/api/v1/communities/{communityId}/details/addresses` | `getAddresses` | authenticated only |
| POST | `/api/v1/communities/{communityId}/details/bank-details` | `addBankDetail` | authenticated only |
| GET | `/api/v1/communities/{communityId}/details/bank-details` | `getBankDetails` | authenticated only |
| POST | `/api/v1/communities/{communityId}/details/payroll` | `createPayroll` | authenticated only |
| GET | `/api/v1/communities/{communityId}/details/payroll` | `getPayrolls` | authenticated only |
| POST | `/api/v1/communities/{communityId}/details/commissions` | `createCommission` | authenticated only |
| GET | `/api/v1/communities/{communityId}/details/commissions` | `getCommissions` | authenticated only |
| POST | `/api/v1/communities/{communityId}/details/login-history` | `recordLogin` | authenticated only |
| POST | `/api/v1/communities/{communityId}/details/login-history/logout` | `recordLogout` | authenticated only |
| GET | `/api/v1/communities/{communityId}/details/login-history` | `getLoginHistory` | authenticated only |
| GET | `/api/v1/communities/{communityId}/details/commission-ledger` | `getCommissionLedger` | authenticated only |

#### `CommunityRoleController` (community)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/communities/roles` | `createRole` | authenticated only |
| GET | `/api/v1/communities/roles/type/{communityType}` | `getRolesByCommunityType` | authenticated only |
| GET | `/api/v1/communities/roles/{id}` | `getRoleById` | authenticated only |
| GET | `/api/v1/communities/roles/code/{roleCode}` | `getRoleByCode` | authenticated only |
| PUT | `/api/v1/communities/roles/{id}` | `updateRole` | authenticated only |
| PUT | `/api/v1/communities/roles/{id}/permissions` | `updateRolePermissions` | authenticated only |
| DELETE | `/api/v1/communities/roles/{id}` | `deleteRole` | authenticated only |
| POST | `/api/v1/communities/roles/members/assign` | `assignRoleToMember` | authenticated only |
| GET | `/api/v1/communities/roles/my-permissions` | `getMyPermissions` | authenticated only |
| GET | `/api/v1/communities/roles/{communityId}/audit-logs` | `getAuditLogs` | authenticated only |

#### `HubController` (hub)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/hubs` | `createHub` | authenticated only |
| GET | `/api/v1/hubs/{id}` | `getHubById` | authenticated only |
| GET | `/api/v1/hubs/code/{hubCode}` | `getHubByCode` | authenticated only |
| GET | `/api/v1/hubs` | `listHubs` | authenticated only |
| PUT | `/api/v1/hubs/{id}` | `updateHub` | authenticated only |
| POST | `/api/v1/hubs/{id}/managers` | `assignHubManagers` | authenticated only |
| POST | `/api/v1/hubs/{id}/lead-community/{communityId}` | `assignLeadCommunity` | authenticated only |
| POST | `/api/v1/hubs/{id}/communities/{communityId}` | `assignCommunityToHub` | authenticated only |
| DELETE | `/api/v1/hubs/{id}/communities/{communityId}` | `removeCommunityFromHub` | authenticated only |

#### `OnboardingController` (onboarding)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/onboarding` | `initiateOnboarding` | authenticated only |
| GET | `/api/v1/onboarding/{id}` | `getOnboardingById` | authenticated only |
| GET | `/api/v1/onboarding/code/{code}` | `getOnboardingByCode` | authenticated only |
| GET | `/api/v1/onboarding/{id}/detail` | `getOnboardingDetail` | authenticated only |
| PUT | `/api/v1/onboarding/{id}/steps` | `saveStepData` | authenticated only |
| POST | `/api/v1/onboarding/{id}/submit` | `submitOnboarding` | authenticated only |
| POST | `/api/v1/onboarding/{id}/verify` | `verifyOnboarding` | authenticated only |
| POST | `/api/v1/onboarding/{id}/approve` | `approveOnboarding` | authenticated only |
| POST | `/api/v1/onboarding/{id}/reject` | `rejectOnboarding` | authenticated only |
| GET | `/api/v1/onboarding` | `getAllOnboardings` | authenticated only |

#### `OnboardingOrchestrationController` (orchestration)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/onboarding/{id}/activate` | `completeOnboardingAndActivate` | authenticated only |

#### `PaymentController` (payment)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/payments/initiate` | `initiatePayment` | authenticated only |
| POST | `/api/v1/payments/verify` | `verifyPayment` | authenticated only |
| POST | `/api/v1/payments/manual/confirm` | `confirmManualPayment` | authenticated only |
| GET | `/api/v1/payments/{transactionCode}` | `getByTransactionCode` | authenticated only |
| GET | `/api/v1/payments/school/{schoolId}` | `getBySchool` | authenticated only |
| POST | `/api/v1/payments/refund` | `refundPayment` | authenticated only |

#### `PaymentWebhookController` (payment)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/webhooks/payments/razorpay` | `handleRazorpayWebhook` | public |
| POST | `/api/v1/webhooks/payments/cashfree` | `handleCashfreeWebhook` | public |

#### `SchoolController` (school)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/schools` | `createSchool` | authenticated only |
| GET | `/api/v1/schools/{id}` | `getSchoolById` | authenticated only |
| GET | `/api/v1/schools/code/{code}` | `getSchoolByCode` | authenticated only |
| GET | `/api/v1/schools/tenant/{tenantId}` | `getSchoolByTenantId` | authenticated only |
| GET | `/api/v1/schools/stats` | `getMasterStats` | authenticated only |
| GET | `/api/v1/schools` | `getAllSchools` | authenticated only |
| PUT | `/api/v1/schools/{id}` | `updateSchool` | authenticated only |
| PATCH | `/api/v1/schools/{id}/status` | `changeSchoolStatus` | authenticated only |
| POST | `/api/v1/schools/{id}/branches` | `addBranch` | authenticated only |
| GET | `/api/v1/schools/{id}/branches` | `getSchoolBranches` | authenticated only |
| GET | `/api/v1/schools/{id}/branches/{branchId}` | `getBranchById` | authenticated only |
| PUT | `/api/v1/schools/{id}/branches/{branchId}` | `updateBranch` | authenticated only |

#### `SchoolDetailController` (school)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/schools/{id}/contacts` | `addContact` | authenticated only |
| GET | `/api/v1/schools/{id}/contacts` | `getContacts` | authenticated only |
| PUT | `/api/v1/schools/{id}/contacts/{contactId}` | `updateContact` | authenticated only |
| DELETE | `/api/v1/schools/{id}/contacts/{contactId}` | `deleteContact` | authenticated only |
| POST | `/api/v1/schools/{id}/addresses` | `addAddress` | authenticated only |
| GET | `/api/v1/schools/{id}/addresses` | `getAddresses` | authenticated only |
| PUT | `/api/v1/schools/{id}/addresses/{addressId}` | `updateAddress` | authenticated only |
| DELETE | `/api/v1/schools/{id}/addresses/{addressId}` | `deleteAddress` | authenticated only |
| POST | `/api/v1/schools/{id}/boards` | `addBoardAffiliation` | authenticated only |
| GET | `/api/v1/schools/{id}/boards` | `getBoardAffiliations` | authenticated only |
| PUT | `/api/v1/schools/{id}/boards/{affiliationId}` | `updateBoardAffiliation` | authenticated only |
| DELETE | `/api/v1/schools/{id}/boards/{affiliationId}` | `deleteBoardAffiliation` | authenticated only |
| POST | `/api/v1/schools/{id}/documents` | `addDocument` | authenticated only |
| GET | `/api/v1/schools/{id}/documents` | `getDocuments` | authenticated only |
| POST | `/api/v1/schools/{id}/documents/{documentId}/verify` | `verifyDocument` | authenticated only |
| POST | `/api/v1/schools/{id}/documents/{documentId}/reject` | `rejectDocument` | authenticated only |
| DELETE | `/api/v1/schools/{id}/documents/{documentId}` | `deleteDocument` | authenticated only |
| GET | `/api/v1/schools/{id}/complete-profile` | `getCompleteSchoolProfile` | authenticated only |
| PUT | `/api/v1/schools/{id}/complete-profile` | `saveCompleteSchoolProfile` | authenticated only |

#### `SchoolRelationshipController` (school)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/schools/{schoolId}/relationship/assign-rm` | `assignRelationshipManager` | authenticated only |
| POST | `/api/v1/schools/{schoolId}/relationship/auto-assign-nearest` | `autoAssignNearest` | authenticated only |
| GET | `/api/v1/schools/{schoolId}/relationship` | `getSchoolRelationship` | authenticated only |
| GET | `/api/v1/communities/{communityId}/assigned-schools` | `listAssignedSchoolsForCommunity` | authenticated only |
| GET | `/api/v1/hubs/{hubId}/assigned-schools` | `listAssignedSchoolsForHub` | authenticated only |

#### `SchoolSubscriptionController` (subscription)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/schools/{schoolId}/subscriptions` | `subscribeSchool` | authenticated only |
| GET | `/api/v1/schools/{schoolId}/subscriptions/active` | `getActiveSubscription` | authenticated only |
| GET | `/api/v1/schools/{schoolId}/subscriptions/entitlements` | `getSchoolEntitlements` | authenticated only |
| PUT | `/api/v1/schools/{schoolId}/subscriptions/feature-overrides` | `overrideFeature` | authenticated only |
| DELETE | `/api/v1/schools/{schoolId}/subscriptions/feature-overrides/{featureCode}` | `removeFeatureOverride` | authenticated only |
| GET | `/api/v1/schools/{schoolId}/subscriptions` | `getSubscriptionHistory` | authenticated only |
| POST | `/api/v1/schools/{schoolId}/subscriptions/{subscriptionId}/cancel` | `cancelSubscription` | authenticated only |

#### `SubscriptionPlanController` (subscription)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| GET | `/api/v1/subscription-plans/tiers` | `getAvailableTiers` | authenticated only |
| GET | `/api/v1/subscription-plans/tier/{tier}` | `getPlansByTier` | authenticated only |
| GET | `/api/v1/subscription-plans/modules` | `getModularCatalog` | authenticated only |
| POST | `/api/v1/subscription-plans/calculate-quote` | `calculateQuote` | authenticated only |
| POST | `/api/v1/subscription-plans/custom-plan` | `createCustomSchoolPlan` | authenticated only |
| GET | `/api/v1/subscription-plans/catalog` | `getPublicCatalog` | authenticated only |
| GET | `/api/v1/subscription-plans/features` | `getAllFeatures` | authenticated only |
| GET | `/api/v1/subscription-plans/{id}` | `getPlanById` | authenticated only |
| GET | `/api/v1/subscription-plans/code/{planCode}` | `getActivePlanByCode` | authenticated only |
| GET | `/api/v1/subscription-plans` | `getAllPlans` | authenticated only |
| POST | `/api/v1/subscription-plans` | `createPlan` | authenticated only |
| PATCH | `/api/v1/subscription-plans/{id}/publish` | `publishPlan` | authenticated only |
| PATCH | `/api/v1/subscription-plans/{id}/deprecate` | `deprecatePlan` | authenticated only |

#### `SchoolSupportQueryController` (support)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/support/queries` | `createQuery` | authenticated only |
| GET | `/api/v1/support/queries/{id}` | `getQueryById` | authenticated only |
| GET | `/api/v1/support/queries/ticket/{ticketNumber}` | `getQueryByTicketNumber` | authenticated only |
| GET | `/api/v1/support/queries` | `listQueries` | authenticated only |
| POST | `/api/v1/support/queries/{id}/escalate` | `escalateQuery` | authenticated only |
| POST | `/api/v1/support/queries/{id}/resolve` | `resolveQuery` | authenticated only |
| POST | `/api/v1/support/queries/{id}/comments` | `addComment` | authenticated only |
| GET | `/api/v1/support/queries/{id}/comments` | `getComments` | authenticated only |

#### `SchoolOnboardingWorkflowController` (workflow)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/onboarding/{id}/select-plan` | `selectPlan` | authenticated only |
| POST | `/api/v1/onboarding/{id}/payment/initiate` | `initiatePayment` | authenticated only |
| POST | `/api/v1/onboarding/{id}/provision` | `provisionTenant` | authenticated only |
| POST | `/api/v1/onboarding/{id}/admin` | `createInitialAdmin` | authenticated only |
| POST | `/api/v1/onboarding/{id}/retry` | `retryWorkflow` | authenticated only |
| GET | `/api/v1/onboarding/{id}/workflow-status` | `getWorkflowProgress` | authenticated only |

#### `AuthController` (security)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/auth/login` | `login` | public |
| POST | `/api/v1/auth/refresh` | `refreshToken` | public |
| POST | `/api/v1/auth/logout` | `logout` | public |
| GET | `/api/v1/auth/me` | `getCurrentUser` | authenticated (self) |

#### `AcademicClassController` (academic) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/academic-classes` | `createAcademicClass` | perm:ACADEMIC_CLASS_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/academic-classes/{id}` | `getAcademicClassById` | perm:ACADEMIC_CLASS_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/academic-classes` | `listAcademicClasses` | perm:ACADEMIC_CLASS_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/academic-classes/{id}` | `updateAcademicClass` | perm:ACADEMIC_CLASS_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/academic-classes/{id}` | `deleteAcademicClass` | perm:ACADEMIC_CLASS_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `AcademicYearController` (academic)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| GET | `/api/v1/academic-years/{id}/sections/{sectionId}/timetable` | `getActiveSectionTimetable` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/academic-years` | `createAcademicYear` | perm:ACADEMIC_YEAR_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/academic-years/{id}` | `getAcademicYearById` | perm:ACADEMIC_YEAR_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/academic-years/current` | `getCurrentAcademicYear` | perm:ACADEMIC_YEAR_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/academic-years` | `listAcademicYears` | perm:ACADEMIC_YEAR_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/academic-years/{id}` | `updateAcademicYear` | perm:ACADEMIC_YEAR_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/academic-years/{id}/activate` | `activateAcademicYear` | perm:ACADEMIC_YEAR_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/academic-years/{id}/close` | `closeAcademicYear` | perm:ACADEMIC_YEAR_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/academic-years/{id}/current` | `setCurrentAcademicYear` | perm:ACADEMIC_YEAR_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/academic-years/{id}` | `deleteAcademicYear` | perm:ACADEMIC_YEAR_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `SectionController` (academic) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/sections` | `createSection` | perm:SECTION_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/sections/{id}` | `getSectionById` | perm:SECTION_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/sections` | `listSections` | perm:SECTION_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/sections/{id}` | `updateSection` | perm:SECTION_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/sections/{id}` | `deleteSection` | perm:SECTION_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StudentAcademicAssignmentController` (academic) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/students/{studentId}/academic-assignments` | `assignStudent` | perm:STUDENT_ACADEMIC_ASSIGNMENT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/{studentId}/academic-assignments` | `getStudentAssignments` | perm:STUDENT_ACADEMIC_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/{studentId}/academic-assignments/current` | `getCurrentAssignment` | perm:STUDENT_ACADEMIC_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/{studentId}/academic-assignments/{assignmentId}` | `getAssignmentById` | perm:STUDENT_ACADEMIC_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/students/{studentId}/academic-assignments/{assignmentId}` | `updateAssignment` | perm:STUDENT_ACADEMIC_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/tenant/students/{studentId}/academic-assignments/{assignmentId}/transfer` | `transferStudent` | perm:STUDENT_ACADEMIC_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `ClassSubjectAssignmentController` (assignment) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/class-subject-assignments` | `createAssignment` | perm:CLASS_SUBJECT_ASSIGNMENT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-subject-assignments` | `searchAssignments` | perm:CLASS_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-subject-assignments/{id}` | `getAssignment` | perm:CLASS_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-subject-assignments/code/{assignmentCode}` | `getAssignmentByCode` | perm:CLASS_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-subject-assignments/class/{academicClassId}` | `getActiveAssignmentsByClass` | perm:CLASS_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/class-subject-assignments/{id}` | `updateAssignment` | perm:CLASS_SUBJECT_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/tenant/class-subject-assignments/{id}/status` | `updateStatus` | perm:CLASS_SUBJECT_ASSIGNMENT_STATUS_UPDATE,CLASS_SUBJECT_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/class-subject-assignments/{id}` | `deleteAssignment` | perm:CLASS_SUBJECT_ASSIGNMENT_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `ClassTeacherAssignmentController` (assignment) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/class-teacher-assignments` | `createAssignment` | perm:CLASS_TEACHER_ASSIGNMENT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-teacher-assignments` | `searchAssignments` | perm:CLASS_TEACHER_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-teacher-assignments/{id}` | `getAssignment` | perm:CLASS_TEACHER_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-teacher-assignments/code/{assignmentCode}` | `getAssignmentByCode` | perm:CLASS_TEACHER_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-teacher-assignments/academic-year/{academicYearId}` | `getByAcademicYear` | perm:CLASS_TEACHER_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-teacher-assignments/section/{sectionId}` | `getBySection` | perm:CLASS_TEACHER_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-teacher-assignments/section/{sectionId}/current` | `getCurrentClassTeacher` | perm:CLASS_TEACHER_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/class-teacher-assignments/staff/{staffId}` | `getActiveByStaff` | perm:CLASS_TEACHER_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/class-teacher-assignments/{id}` | `updateAssignment` | perm:CLASS_TEACHER_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/tenant/class-teacher-assignments/{id}/status` | `updateStatus` | perm:CLASS_TEACHER_ASSIGNMENT_STATUS_UPDATE,CLASS_TEACHER_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/tenant/class-teacher-assignments/{id}/replace` | `replaceClassTeacher` | perm:CLASS_TEACHER_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/class-teacher-assignments/{id}` | `deleteAssignment` | perm:CLASS_TEACHER_ASSIGNMENT_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `TeacherSubjectAssignmentController` (assignment) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/teacher-assignments` | `createAssignment` | perm:TEACHER_SUBJECT_ASSIGNMENT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/teacher-assignments` | `searchAssignments` | perm:TEACHER_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/teacher-assignments/{id}` | `getAssignment` | perm:TEACHER_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/teacher-assignments/code/{assignmentCode}` | `getAssignmentByCode` | perm:TEACHER_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/teacher-assignments/teacher/{teacherId}` | `getActiveAssignmentsByTeacher` | perm:TEACHER_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/teacher-assignments/class/{academicClassId}` | `getActiveAssignmentsByClass` | perm:TEACHER_SUBJECT_ASSIGNMENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/teacher-assignments/{id}` | `updateAssignment` | perm:TEACHER_SUBJECT_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/tenant/teacher-assignments/{id}/status` | `updateStatus` | perm:TEACHER_SUBJECT_ASSIGNMENT_STATUS_UPDATE,TEACHER_SUBJECT_ASSIGNMENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/teacher-assignments/{id}` | `deleteAssignment` | perm:TEACHER_SUBJECT_ASSIGNMENT_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StudentAttendanceController` (attendance)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/attendance` | `markAttendance` | perm:ATTENDANCE_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| POST | `/api/v1/attendance/bulk` | `markBulkAttendance` | perm:ATTENDANCE_BULK_MARK roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/attendance/{id}` | `getAttendance` | perm:ATTENDANCE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| PUT | `/api/v1/attendance/{id}` | `updateAttendance` | perm:ATTENDANCE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| PATCH | `/api/v1/attendance/{id}/status` | `updateAttendanceStatus` | perm:ATTENDANCE_STATUS_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| DELETE | `/api/v1/attendance/{id}` | `deleteAttendance` | perm:ATTENDANCE_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/attendance/students/{studentId}` | `getStudentAttendanceHistory` | perm:ATTENDANCE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/attendance/students/{studentId}/summary` | `getAttendanceSummary` | perm:ATTENDANCE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/attendance/sections/{sectionId}/daily` | `getDailySectionAttendance` | perm:ATTENDANCE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/attendance/sections/{sectionId}` | `getSectionAttendanceHistory` | perm:ATTENDANCE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/attendance/missing` | `getMissingAttendance` | perm:ATTENDANCE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |

#### `CalendarController` (calendar)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| GET | `/api/v1/calendar/config` | `getWorkingDayConfig` | perm:CALENDAR_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/calendar/config` | `updateWorkingDayConfig` | perm:CALENDAR_MANAGE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/calendar/days` | `getDaysStatus` | perm:CALENDAR_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/calendar/working-days/count` | `countWorkingDays` | perm:CALENDAR_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `HolidayController` (calendar)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/holidays` | `createHoliday` | perm:CALENDAR_MANAGE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/holidays/{id}` | `getHolidayById` | perm:CALENDAR_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/holidays` | `searchHolidays` | perm:CALENDAR_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/holidays/{id}` | `updateHoliday` | perm:CALENDAR_MANAGE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/holidays/{id}` | `deleteHoliday` | perm:CALENDAR_MANAGE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `ExamScheduleController` (examination)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/exams/schedules` | `createExamSchedule` | perm:EXAM_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/exams/schedules/{id}` | `getExamScheduleById` | perm:EXAM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/exams/schedules` | `searchSchedules` | perm:EXAM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| POST | `/api/v1/exams/schedules/{id}/subjects` | `addSubjectToSchedule` | perm:EXAM_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/exams/schedules/{id}/subjects` | `getScheduleSubjects` | perm:EXAM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| DELETE | `/api/v1/exams/schedules/{id}/subjects/{subjectConfigId}` | `removeSubjectFromSchedule` | perm:EXAM_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/exams/schedules/{id}/publish` | `publishSchedule` | perm:EXAM_PUBLISH roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/exams/schedules/{id}` | `deleteExamSchedule` | perm:EXAM_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `ExamTermController` (examination)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/exams/terms` | `createExamTerm` | perm:EXAM_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/exams/terms/{id}` | `getExamTermById` | perm:EXAM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/exams/terms/academic-year/{academicYearId}` | `getExamTermsByAcademicYear` | perm:EXAM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/exams/terms` | `searchExamTerms` | perm:EXAM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| PUT | `/api/v1/exams/terms/{id}` | `updateExamTerm` | perm:EXAM_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/exams/terms/{id}/status` | `updateTermStatus` | perm:EXAM_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/exams/terms/{id}` | `deleteExamTerm` | perm:EXAM_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `GradingScaleController` (examination)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/exams/grading-scales` | `createGradingScale` | perm:GRADING_SCALE_MANAGE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/exams/grading-scales/{id}` | `getGradingScaleById` | perm:GRADING_SCALE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/exams/grading-scales/default` | `getDefaultGradingScale` | perm:GRADING_SCALE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/exams/grading-scales` | `listGradingScales` | perm:GRADING_SCALE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| PATCH | `/api/v1/exams/grading-scales/{id}/default` | `setDefaultScale` | perm:GRADING_SCALE_MANAGE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/exams/grading-scales/{id}` | `deleteGradingScale` | perm:GRADING_SCALE_MANAGE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StudentMarkController` (examination)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/exams/marks/subject-config/{configId}` | `submitStudentMark` | perm:MARKS_ENTRY roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| POST | `/api/v1/exams/marks/bulk` | `submitBulkMarks` | perm:MARKS_ENTRY roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/exams/marks/{id}` | `getMarkById` | perm:MARKS_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/exams/marks/subject-config/{configId}` | `searchMarks` | perm:MARKS_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| GET | `/api/v1/exams/marks/student/{studentId}` | `getMarksByStudent` | perm:MARKS_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN,TEACHER |
| POST | `/api/v1/exams/marks/subject-config/{configId}/lock` | `lockMarks` | perm:MARKS_LOCK roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StaffController` (staff)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/staff` | `createStaff` | perm:STAFF_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/staff` | `listStaff` | perm:STAFF_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/staff/{id}` | `getStaffById` | perm:STAFF_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/staff/code/{staffCode}` | `getStaffByCode` | perm:STAFF_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/staff/tenant-user/{tenantUserId}` | `getStaffByTenantUserId` | perm:STAFF_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/staff/{id}` | `updateStaff` | perm:STAFF_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/staff/{id}/status` | `updateStaffStatus` | perm:STAFF_STATUS_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/staff/{id}` | `deleteStaff` | perm:STAFF_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `ClassStudentController` (student)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| GET | `/` | `getSectionStudents` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/` | `getClassStudents` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StudentAdmissionController` (student) — mapped under 4 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/admissions` | `admitStudent` | perm:STUDENT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StudentController` (student) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/students` | `createStudent` | perm:STUDENT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/{id}` | `getStudentById` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/code/{studentCode}` | `getStudentByCode` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/{id}/profile` | `getStudentProfile` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students` | `listStudents` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/students/{id}` | `updateStudent` | perm:STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/tenant/students/{id}/status` | `updateStudentStatus` | perm:STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/students/{id}` | `deleteStudent` | perm:STUDENT_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StudentEnrollmentController` (student)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/students/{studentId}/enrollments` | `createEnrollment` | perm:STUDENT_ENROLL,STUDENT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/{studentId}/enrollments` | `getStudentEnrollments` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/{studentId}/current-enrollment` | `getCurrentEnrollment` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/enrollments/{id}` | `getEnrollmentById` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/enrollments/code/{code}` | `getEnrollmentByCode` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/enrollments/{id}` | `updateEnrollment` | perm:STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/tenant/enrollments/{id}/withdraw` | `withdrawStudent` | perm:STUDENT_WITHDRAW,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/tenant/enrollments/{id}/transfer` | `transferStudent` | perm:STUDENT_TRANSFER,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/enrollments` | `searchEnrollments` | perm:STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StudentGuardianController` (student) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| GET | `/api/v1/tenant/students/{studentId}/guardians` | `getGuardians` | perm:STUDENT_GUARDIAN_READ,STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/tenant/students/{studentId}/guardians` | `createGuardian` | perm:STUDENT_GUARDIAN_CREATE,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/students/{studentId}/guardians/{guardianId}` | `getGuardianById` | perm:STUDENT_GUARDIAN_READ,STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/students/{studentId}/guardians/{guardianId}` | `updateGuardian` | perm:STUDENT_GUARDIAN_UPDATE,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/students/{studentId}/guardians/{guardianId}` | `deleteGuardian` | perm:STUDENT_GUARDIAN_DELETE,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `StudentSubjectEnrollmentController` (student) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| GET | `/api/v1/tenant/enrollments/{enrollmentId}/subjects` | `getEnrolledSubjects` | perm:STUDENT_SUBJECT_READ,STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/tenant/enrollments/{enrollmentId}/subjects` | `enrollSubject` | perm:STUDENT_SUBJECT_ASSIGN,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/tenant/enrollments/{enrollmentId}/subjects/batch` | `batchEnrollSubjects` | perm:STUDENT_SUBJECT_ASSIGN,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/enrollments/{enrollmentId}/subjects/{subjectEnrollmentId}` | `getSubjectEnrollmentById` | perm:STUDENT_SUBJECT_READ,STUDENT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/enrollments/{enrollmentId}/subjects/{subjectEnrollmentId}` | `updateSubjectEnrollment` | perm:STUDENT_SUBJECT_UPDATE,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/enrollments/{enrollmentId}/subjects/{subjectEnrollmentId}` | `removeSubjectEnrollment` | perm:STUDENT_SUBJECT_DELETE,STUDENT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `CurriculumController` (subject) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/curricula` | `createCurriculum` | perm:CURRICULUM_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/curricula` | `searchCurricula` | perm:CURRICULUM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/curricula/{id}` | `getCurriculum` | perm:CURRICULUM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/curricula/code/{curriculumCode}` | `getCurriculumByCode` | perm:CURRICULUM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/curricula/{id}` | `updateCurriculum` | perm:CURRICULUM_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/tenant/curricula/{id}/status` | `updateStatus` | perm:CURRICULUM_STATUS_UPDATE,CURRICULUM_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/curricula/{id}` | `deleteCurriculum` | perm:CURRICULUM_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `CurriculumSubjectController` (subject) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/curricula/{curriculumId}/subjects` | `addSubjectToCurriculum` | perm:CURRICULUM_SUBJECT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/curricula/{curriculumId}/subjects` | `listCurriculumSubjects` | perm:CURRICULUM_SUBJECT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/curricula/{curriculumId}/subjects/{curriculumSubjectId}` | `getCurriculumSubject` | perm:CURRICULUM_SUBJECT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/curricula/{curriculumId}/subjects/{curriculumSubjectId}` | `updateCurriculumSubject` | perm:CURRICULUM_SUBJECT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/curricula/{curriculumId}/subjects/{curriculumSubjectId}` | `removeSubjectFromCurriculum` | perm:CURRICULUM_SUBJECT_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `SubjectController` (subject) — mapped under 2 path aliases

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/subjects` | `createSubject` | perm:SUBJECT_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/subjects` | `searchSubjects` | perm:SUBJECT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/subjects/{id}` | `getSubject` | perm:SUBJECT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/subjects/code/{subjectCode}` | `getSubjectByCode` | perm:SUBJECT_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/subjects/{id}` | `updateSubject` | perm:SUBJECT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/tenant/subjects/{id}/status` | `updateStatus` | perm:SUBJECT_STATUS_UPDATE,SUBJECT_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/subjects/{id}` | `deleteSubject` | perm:SUBJECT_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `BellScheduleController` (timetable)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/bell-schedules` | `createBellSchedule` | perm:BELL_SCHEDULE_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/bell-schedules` | `searchBellSchedules` | perm:BELL_SCHEDULE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/bell-schedules/default` | `getDefaultSchedule` | perm:BELL_SCHEDULE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/bell-schedules/{id}` | `getBellScheduleById` | perm:BELL_SCHEDULE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/bell-schedules/{id}` | `updateBellSchedule` | perm:BELL_SCHEDULE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/bell-schedules/{id}/status` | `updateBellScheduleStatus` | perm:BELL_SCHEDULE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/bell-schedules/{id}/set-default` | `setDefaultSchedule` | perm:BELL_SCHEDULE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/bell-schedules/{id}/clone` | `cloneSchedule` | perm:BELL_SCHEDULE_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/bell-schedules/{id}` | `deleteBellSchedule` | perm:BELL_SCHEDULE_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/bell-schedules/{id}/periods` | `getPeriodsByScheduleId` | perm:BELL_SCHEDULE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/bell-schedules/{id}/periods` | `addPeriod` | perm:BELL_SCHEDULE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/bell-schedules/{id}/periods/{periodId}` | `updatePeriod` | perm:BELL_SCHEDULE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/bell-schedules/{id}/periods/{periodId}` | `deletePeriod` | perm:BELL_SCHEDULE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/bell-schedules/{id}/periods` | `bulkReplacePeriods` | perm:BELL_SCHEDULE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `RoomController` (timetable)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/rooms` | `createRoom` | perm:ROOM_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/rooms` | `searchRooms` | perm:ROOM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/rooms/{id}` | `getRoomById` | perm:ROOM_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/rooms/{id}` | `updateRoom` | perm:ROOM_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/rooms/{id}/status` | `updateRoomStatus` | perm:ROOM_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/rooms/{id}` | `deleteRoom` | perm:ROOM_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `TimetableController` (timetable)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/timetables` | `createTimetable` | perm:TIMETABLE_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables` | `searchTimetables` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{id}` | `getTimetableById` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/timetables/{id}` | `updateTimetable` | perm:TIMETABLE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/timetables/{id}/status` | `updateTimetableStatus` | perm:TIMETABLE_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/timetables/{id}` | `deleteTimetable` | perm:TIMETABLE_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/timetables/{id}/validate` | `validateTimetable` | perm:TIMETABLE_VALIDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/timetables/{id}/publish` | `publishTimetable` | perm:TIMETABLE_PUBLISH roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/timetables/{id}/clone` | `cloneTimetable` | perm:TIMETABLE_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/timetables/{timetableId}/sections/{sectionId}/entries` | `bulkUpsertSectionEntries` | perm:TIMETABLE_ENTRY_UPDATE,TIMETABLE_ENTRY_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| POST | `/api/v1/timetables/{timetableId}/entries` | `createEntry` | perm:TIMETABLE_ENTRY_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/entries` | `searchEntries` | perm:TIMETABLE_ENTRY_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/entries/{id}` | `getEntryById` | perm:TIMETABLE_ENTRY_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/timetables/{timetableId}/entries/{id}` | `updateEntry` | perm:TIMETABLE_ENTRY_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/timetables/{timetableId}/entries/{id}` | `deleteEntry` | perm:TIMETABLE_ENTRY_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/class/{academicClassId}` | `getClassTimetableGrid` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/sections/{sectionId}/grid` | `getSectionTimetableGrid` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/teachers/{teacherId}/grid` | `getTeacherTimetableGrid` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/sections/{sectionId}/day` | `getSectionDaySchedule` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/teachers/{teacherId}/day` | `getTeacherDaySchedule` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/rooms/{roomId}/grid` | `getRoomTimetableGrid` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/rooms/{roomId}/occupancy` | `getRoomOccupancy` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/timetables/{timetableId}/teachers/{teacherId}/load` | `getTeacherLoad` | perm:TIMETABLE_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `TimetableSubstitutionController` (timetable)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/substitutions` | `createSubstitution` | perm:SUBSTITUTION_MANAGE |
| GET | `/api/v1/substitutions/{id}` | `getSubstitutionById` | perm:SUBSTITUTION_READ |
| GET | `/api/v1/substitutions` | `searchSubstitutions` | perm:SUBSTITUTION_READ |
| PUT | `/api/v1/substitutions/{id}` | `updateSubstitution` | perm:SUBSTITUTION_MANAGE |
| PATCH | `/api/v1/substitutions/{id}/cancel` | `cancelSubstitution` | perm:SUBSTITUTION_MANAGE |
| DELETE | `/api/v1/substitutions/{id}` | `deleteSubstitution` | perm:SUBSTITUTION_MANAGE |

#### `TenantUserController` (user)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| POST | `/api/v1/tenant/users` | `createTenantUser` | perm:USER_CREATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/users/{id}` | `getTenantUserById` | perm:USER_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| GET | `/api/v1/tenant/users` | `listTenantUsers` | perm:USER_READ roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PUT | `/api/v1/tenant/users/{id}` | `updateTenantUser` | perm:USER_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| PATCH | `/api/v1/tenant/users/{id}/status` | `updateTenantUserStatus` | perm:USER_UPDATE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |
| DELETE | `/api/v1/tenant/users/{id}` | `deleteTenantUser` | perm:USER_DELETE roles:S_ADMIN,S_OWNER,P_ADMIN,P_SUPER_ADMIN |

#### `WebPageController` (web)

| Method | Path | Handler | Authorization |
|---|---|---|---|
| GET | `/` | `index` | public (HTML) |
| GET | `/schools/{slug}` | `schoolPortal` | public (HTML) |
| GET | `/llms.txt` | `getLlmsTxt` | public (HTML) |
| GET | `/llms-full.txt` | `getLlmsFullTxt` | public (HTML) |
| GET | `/robots.txt` | `getRobotsTxt` | public (HTML) |
| GET | `/sitemap.xml` | `getSitemapXml` | public (HTML) |
