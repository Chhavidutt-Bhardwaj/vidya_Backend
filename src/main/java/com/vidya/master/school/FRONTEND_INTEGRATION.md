# School Module: Frontend Integration Guide

How to call the school APIs from the frontend: auth, response envelope, every endpoint, payloads, enums, errors and UI rules.

Source of truth: `controller/SchoolController.java`, `controller/SchoolDetailController.java`, `controller/SchoolRelationshipController.java` and the records in `dto/`. Swagger UI is at `/swagger-ui.html` (spec at `/v3/api-docs`).

---

## 1. Basics

| Item | Value |
|---|---|
| Base path | `/api/v1` |
| Auth | `Authorization: Bearer <accessToken>` on every call except login/refresh/logout |
| Content type | `application/json` |
| Tenant header | **Not needed** for `/api/v1/schools/**`, `/api/v1/communities/**` and `/api/v1/hubs/**`. These are master-DB routes. (Tenant routes such as `/api/v1/academic-years` resolve the tenant from the JWT, or from `X-Tenant-ID` when it is sent.) |
| Correlation | Optional `X-Correlation-ID` request header. It is echoed as `correlationId` in error bodies, so log it when reporting bugs. |
| IDs | UUID strings |
| Dates | `LocalDate` → `"2026-04-01"`; `Instant` → ISO-8601 UTC `"2026-11-09T10:15:30Z"` |
| Enums | Sent and received as upper-case strings (`"CBSE"`, `"ACTIVE"`) |

### 1.1 Login and session

```http
POST /api/v1/auth/login
{ "email": "admin@school.edu", "password": "••••" }
```

`data` in the response:

```ts
interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;           // seconds
  user: PlatformUser;
}

interface PlatformUser {
  id: string;
  userCode: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  phone?: string;
  status: string;
  platformRole: string;        // e.g. PLATFORM_ADMIN, SCHOOL_ADMIN, SCHOOL_OWNER
  schoolId?: string;           // set for school users, so use it as :id in the school routes
  tenantCode?: string;
  accountScope: 'PLATFORM' | 'SCHOOL' | 'SCHOOL_PORTAL' | 'COMMUNITY';
  roles: string[];             // account-type role + any extra/custom roles
  permissions: string[];       // e.g. ["SCHOOL_READ", "SCHOOL_UPDATE"]; use these to show/hide UI
  enabledFeatures: string[];   // modules the school has (plan + overrides)
  dataScopes: Record<string, 'ALL' | 'OWN'>; // which records per area (STUDENT, ATTENDANCE, MARKS, ...)
  accessVersion: string;       // equals the JWT "av" claim; compare with the X-Access-Version response header
  sessionId: string;
  /** Deprecated: section code -> action codes, built from `permissions` (see docs/security/DYNAMIC_ACCESS_CONTROL.md). */
  permissionsMatrix: Record<number, number[]>;
  lastLoginAt?: string;
  createdAt: string;
}
```

- Refresh: `POST /api/v1/auth/refresh` with `{ "refreshToken": "..." }`, which returns a new `AuthResponse`.
- Logout: `POST /api/v1/auth/logout` with `{ "refreshToken": "..." }`.
- The access token also carries this access snapshot (`perms`, `roles`, `features`, `ds`, `av`); build one permission helper from it. See `docs/security/DYNAMIC_ACCESS_CONTROL.md` §7. Refresh the token when the `X-Access-Version` response header differs from `av`.
- Current user: `GET /api/v1/auth/me`. Call it on app load to restore `schoolId`, `platformRole` and `permissions`.

**School dashboard rule:** a school user always works on `user.schoolId`. Requests for any other school id fail with **403**, because `tenantAuthorizationService.canAccessSchool` checks that the user can access that school.

### 1.2 Response envelope

Every response uses this envelope. Read the payload from `data`.

```ts
interface ApiResponse<T> {
  success: boolean;
  code: string;               // machine code, e.g. "SCHOOL_FOUND", "VALIDATION_ERROR"
  message: string;            // human-readable
  data?: T;                   // omitted when null (for example on DELETE)
  timestamp: string;
  correlationId?: string;
}

interface PagedApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T[];
  page: {
    number: number;           // 0-based
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
  };
  timestamp: string;
  correlationId?: string;
}
```

Paged endpoints accept the Spring query params `?page=0&size=20&sort=schoolName,asc`.

### 1.3 Errors

| HTTP | `code` | When | UI handling |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean-validation failure | Show inline field errors from `message` |
| 401 | (auth entry point) | Missing or expired token | Try refresh once, then redirect to login |
| 403 | access denied / `TENANT_ACCESS_DENIED` | Missing permission or wrong school | Show a "not allowed" state and hide the action |
| 404 | `RESOURCE_NOT_FOUND` | Bad id, or a child record that belongs to another school | Show a not-found state |
| 409 | `DUPLICATE_RESOURCE` | School email already used | Show error on the email field |
| 409 | (optimistic lock) | Someone else saved first | Reload and ask the user to retry |
| 422 | `INVALID_STATE_TRANSITION` / business codes | Rule violation | Show `message` as a toast |

---

## 2. Permissions and what to show

`SR` = `SCHOOL_READ`, `SU` = `SCHOOL_UPDATE`, `OU` = `ONBOARDING_UPDATE`, `OV` = `ONBOARDING_VERIFY`, `SC` = `SCHOOL_CREATE`. "Platform" means role `PLATFORM_ADMIN` or `PLATFORM_SUPER_ADMIN`. "Own school" means the user can access that school id.

| Screen / action | Required |
|---|---|
| View school, branches, contacts, addresses, boards, documents, 360 profile | `SR` + own school |
| Edit school core, add/edit branches | `SU` + own school |
| Add/edit/delete contacts, addresses, boards, documents; save 360 profile | (`SU` or `OU`) + own school |
| Verify/reject documents | `OV` + own school (platform side) |
| Create school, change school status, list all schools, master stats | Platform role (+ `SC` / `SU` where noted) |

Use `user.permissions` to hide buttons. The backend enforces the same rules, so hiding is only for UX.

---

## 3. Endpoints

### 3.1 School core (`SchoolController`, `/api/v1/schools`)

| Method | Path | Body / params | Returns | Who |
|---|---|---|---|---|
| POST | `/schools` | `CreateSchoolRequest` | `SchoolResponse` (201) | Platform + `SC` |
| GET | `/schools/{id}` | | `SchoolResponse` | `SR` + own |
| GET | `/schools/code/{code}` | | `SchoolResponse` | `SR` + own |
| GET | `/schools/tenant/{tenantId}` | | `SchoolResponse` | `SR` + own |
| GET | `/schools/stats` | | `MasterStatsResponse` | Platform |
| GET | `/schools` | `status?`, `board?`, `search?`, paging (default sort `schoolName`, size 20) | `Paged<SchoolResponse>` | Platform |
| PUT | `/schools/{id}` | `UpdateSchoolRequest` (full replace of core fields) | `SchoolResponse` | `SU` + own |
| PATCH | `/schools/{id}/status?status=ACTIVE` | query param only | `SchoolResponse` | Platform + `SU` |
| POST | `/schools/{id}/branches` | `CreateBranchRequest` | `SchoolBranchResponse` (201) | `SU` + own |
| GET | `/schools/{id}/branches` | paging | `Paged<SchoolBranchResponse>` | `SR` + own |
| GET | `/schools/{id}/branches/{branchId}` | | `SchoolBranchResponse` | `SR` + own |
| PUT | `/schools/{id}/branches/{branchId}` | `UpdateBranchRequest` | `SchoolBranchResponse` | `SU` + own |

### 3.2 School details (`SchoolDetailController`, `/api/v1/schools/{id}`)

| Method | Path | Body / params | Returns |
|---|---|---|---|
| POST / GET | `/contacts` | `SchoolContactRequest` | `SchoolContactResponse` / `SchoolContactResponse[]` |
| PUT / DELETE | `/contacts/{contactId}` | `SchoolContactRequest` | `SchoolContactResponse` / no data |
| POST / GET | `/addresses` | `SchoolAddressRequest` | `SchoolAddressResponse` / `[]` |
| PUT / DELETE | `/addresses/{addressId}` | `SchoolAddressRequest` | `SchoolAddressResponse` / no data |
| POST / GET | `/boards` | `SchoolBoardAffiliationRequest` | `SchoolBoardAffiliationResponse` / `[]` |
| PUT / DELETE | `/boards/{affiliationId}` | `SchoolBoardAffiliationRequest` | `SchoolBoardAffiliationResponse` / no data |
| POST / GET | `/documents` | `SchoolDocumentRequest` | `SchoolDocumentResponse` / `[]` |
| DELETE | `/documents/{documentId}` | | no data |
| POST | `/documents/{documentId}/verify?verifiedByUserId=<uuid>` | query | `SchoolDocumentResponse` (`OV`) |
| POST | `/documents/{documentId}/reject?rejectedByUserId=<uuid>&reason=...` | query | `SchoolDocumentResponse` (`OV`) |
| GET | `/complete-profile` | | `CompleteSchoolProfileResponse` |
| PUT | `/complete-profile` | `CompleteSchoolProfileRequest` | `CompleteSchoolProfileResponse` |

List endpoints in this controller are **not paged**. They return plain arrays inside `data`.

### 3.3 Relationship manager (`SchoolRelationshipController`, `/api/v1`)

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/schools/{schoolId}/relationship` | | `SchoolRelationshipResponse` |
| POST | `/schools/{schoolId}/relationship/assign-rm` | `AssignSchoolRmRequest` | `SchoolRelationshipResponse` |
| POST | `/schools/{schoolId}/relationship/auto-assign-nearest` | | `SchoolRelationshipResponse` |
| GET | `/communities/{communityId}/assigned-schools` | paging | `Paged<SchoolRelationshipResponse>` |
| GET | `/hubs/{hubId}/assigned-schools` | paging | `Paged<SchoolRelationshipResponse>` |
| PATCH | `/communities/{communityId}/workload-limits` | `UpdateCommunityWorkloadLimitRequest` | no data |

> ⚠️ These endpoints have no `@PreAuthorize`, so any logged-in user can call them. The school dashboard should only call the **GET relationship** endpoint (to show "Your Relationship Manager"). Assign and workload calls belong to the platform/hub UI.

---

## 4. TypeScript types

### 4.1 Enums

```ts
type SchoolStatus = "DRAFT" | "PENDING" | "VERIFIED" | "ACTIVE" | "SUSPENDED" | "DEACTIVATED" | "ARCHIVED";
type SchoolBoard = "CBSE" | "ICSE" | "HBSE" | "STATE" | "IB" | "IGCSE" | "OTHER";            // school.board
type BoardAffiliationType = "CBSE" | "ICSE" | "HBSE" | "STATE_BOARD" | "IB" | "CAMBRIDGE_IGCSE" | "OTHER"; // /boards
type BoardAffiliationLevel = "PRIMARY" | "MIDDLE" | "SECONDARY" | "SENIOR_SECONDARY" | "ALL_LEVELS";
type BoardAffiliationStatus = "ACTIVE" | "PENDING_RENEWAL" | "EXPIRED" | "PROVISIONAL";
type SchoolType = "PRIMARY" | "SECONDARY" | "SENIOR_SECONDARY" | "ALL" | "OTHER";
type BranchStatus = "ACTIVE" | "INACTIVE";
type SchoolAddressType = "CAMPUS_PRIMARY" | "REGISTERED_OFFICE" | "CORRESPONDENCE" | "BRANCH_CAMPUS";
type SchoolContactType =
  | "PRINCIPAL" | "VICE_PRINCIPAL" | "ADMINISTRATIVE_HEAD" | "BILLING_FINANCE" | "ACADEMIC_DIRECTOR"
  | "EXAM_COORDINATOR" | "ADMISSIONS_INCHARGE" | "LEGAL_COUNSEL" | "OTHER";
type SchoolDocumentType =
  | "BOARD_AFFILIATION_LETTER" | "SOCIETY_TRUST_REGISTRATION" | "RECOGNITION_CERTIFICATE" | "FIRE_SAFETY_NOC"
  | "BUILDING_SAFETY_CERTIFICATE" | "LAND_TITLE_DEED" | "WATER_SANITATION_CERTIFICATE" | "SECTION_12A_80G"
  | "MINORITY_STATUS_CERTIFICATE" | "OTHER";
type DocumentVerificationStatus = "PENDING" | "VERIFIED" | "REJECTED";
type RmAssignmentType = "ONBOARDED_BY_COMMUNITY" | "NEAREST_GEO_ALLOCATION" | "MANUAL_PLATFORM_OVERRIDE";
```

> ⚠️ **Two board enums.** `SchoolBoard` (on the school record) uses `STATE` and `IGCSE`. `BoardAffiliationType` (on `/boards`) uses `STATE_BOARD` and `CAMBRIDGE_IGCSE`. Keep separate dropdown option lists for each.

### 4.2 Requests (`*` = required)

```ts
interface CreateSchoolRequest {
  schoolName: string;          // * max 300
  legalName?: string;          // max 300
  registrationNumber?: string; // max 100
  board?: SchoolBoard;
  schoolType?: SchoolType;
  email: string;               // * valid email, max 255, unique
  phone?: string;              // max 20
  website?: string;            // max 500
  mainBranch?: CreateBranchRequest;
}

type UpdateSchoolRequest = Omit<CreateSchoolRequest, "mainBranch">; // schoolName*, email* required; send ALL fields (unsent ones become null)

interface CreateBranchRequest {
  branchName: string;          // * max 300
  isMainBranch: boolean;       // defaults to false if omitted
  addressLine1?: string; addressLine2?: string;   // max 255
  city?: string; state?: string; country?: string; // max 100
  pincode?: string;            // max 20
  contactEmail?: string;       // valid email
  contactPhone?: string;       // max 20
}

interface UpdateBranchRequest extends CreateBranchRequest {
  status?: BranchStatus;       // use INACTIVE to "remove" a branch (there is no DELETE)
}

interface SchoolContactRequest {
  branchId?: string;           // null = school-level contact
  contactType: SchoolContactType; // *
  fullName: string;            // * max 200
  designation?: string;        // max 100
  email: string;               // * valid
  phone: string;               // * max 20
  alternatePhone?: string;
  isPrimary?: boolean;
}

interface SchoolAddressRequest {
  branchId?: string;
  addressType?: SchoolAddressType;
  addressLine1: string;        // *
  addressLine2?: string;
  city: string;                // *
  state: string;               // *
  pincode: string;             // *
  country?: string;
  landmark?: string;           // max 200
  latitude?: number; longitude?: number;
  isPrimary?: boolean;
}

interface SchoolBoardAffiliationRequest {
  boardType: BoardAffiliationType; // *
  boardName?: string;              // max 150
  affiliationNumber: string;       // * max 100
  affiliationLevel?: BoardAffiliationLevel;
  validFrom?: string; validTo?: string;  // yyyy-MM-dd
  isPrimaryBoard?: boolean;
  affiliationLetterUrl?: string;   // max 500
  status?: BoardAffiliationStatus;
}

interface SchoolDocumentRequest {
  documentType: SchoolDocumentType; // *
  documentName: string;             // * max 200
  documentNumber?: string;
  fileUrl: string;                  // * max 500. Upload the file first (see 5.4)
  fileSizeBytes?: number;
  mimeType?: string;
  issuedBy?: string;
  issuedDate?: string; expiryDate?: string; // yyyy-MM-dd
}

interface CompleteSchoolProfileRequest {
  schoolName?: string; legalName?: string; registrationNumber?: string;
  primaryBoard?: SchoolBoard; schoolType?: SchoolType;
  email?: string; phone?: string; website?: string;
  branches?: CreateBranchRequest[];
  contacts?: SchoolContactRequest[];
  addresses?: SchoolAddressRequest[];
  boards?: SchoolBoardAffiliationRequest[];
  documents?: SchoolDocumentRequest[];
}

interface AssignSchoolRmRequest {
  communityId: string;         // *
  onboardingCommunityId?: string;
  rmMemberId?: string;
  rmMemberName?: string;
  assignmentType?: RmAssignmentType;
}
```

### 4.3 Responses

```ts
interface SchoolResponse {
  id: string; schoolCode: string; tenantId?: string;
  schoolName: string; legalName?: string; registrationNumber?: string;
  board?: SchoolBoard; schoolType?: SchoolType;
  email: string; phone?: string; website?: string;
  status: SchoolStatus;
  createdAt: string; updatedAt: string;
  branchCount: number;
  mainBranch?: SchoolBranchResponse;
}

interface SchoolBranchResponse {
  id: string; schoolId: string; schoolCode: string; branchCode: string;
  branchName: string; isMainBranch: boolean;
  addressLine1?: string; addressLine2?: string; city?: string; state?: string; pincode?: string; country?: string;
  contactEmail?: string; contactPhone?: string;
  status: BranchStatus; createdAt: string; updatedAt: string;
}

interface SchoolContactResponse  { id: string; schoolId: string; branchId?: string; contactType: SchoolContactType; fullName: string; designation?: string; email: string; phone: string; alternatePhone?: string; isPrimary: boolean; }
interface SchoolAddressResponse  { id: string; schoolId: string; branchId?: string; addressType?: SchoolAddressType; addressLine1: string; addressLine2?: string; city: string; state: string; pincode: string; country?: string; landmark?: string; latitude?: number; longitude?: number; isPrimary: boolean; }
interface SchoolBoardAffiliationResponse { id: string; schoolId: string; boardType: BoardAffiliationType; boardName?: string; affiliationNumber: string; affiliationLevel?: BoardAffiliationLevel; validFrom?: string; validTo?: string; isPrimaryBoard: boolean; affiliationLetterUrl?: string; status?: BoardAffiliationStatus; }
interface SchoolDocumentResponse { id: string; schoolId: string; documentType: SchoolDocumentType; documentName: string; documentNumber?: string; fileUrl: string; fileSizeBytes?: number; mimeType?: string; issuedBy?: string; issuedDate?: string; expiryDate?: string; verificationStatus: DocumentVerificationStatus; verifiedByUserId?: string; verifiedAt?: string; rejectionReason?: string; }

interface SchoolRelationshipResponse {
  schoolId: string; schoolCode: string; schoolName: string; schoolEmail: string; schoolPhone?: string; schoolCity?: string; schoolState?: string;
  onboardingCommunityId?: string; onboardingCommunityCode?: string; onboardingCommunityName?: string; onboardedAt?: string;
  assignedCommunityId?: string; assignedCommunityCode?: string; assignedCommunityName?: string; assignedCommunityType?: string;
  rmCommunityActiveQueries: number; rmCommunityMaxQueryLimit: number;
  assignedRmMemberId?: string; rmMemberName?: string;
  hubId?: string; hubCode?: string; hubName?: string; hubState?: string;
  hubManagerUserId?: string; hubManagerName?: string; hubSubManagerUserId?: string; hubSubManagerName?: string;
  rmAssignmentType?: RmAssignmentType; rmAssignedAt?: string;
}

interface CompleteSchoolProfileResponse {
  school: SchoolResponse;
  relationship: SchoolRelationshipResponse;
  branches: SchoolBranchResponse[];
  contacts: SchoolContactResponse[];
  addresses: SchoolAddressResponse[];
  boards: SchoolBoardAffiliationResponse[];
  documents: SchoolDocumentResponse[];
}

interface MasterStatsResponse { totalSchools: number; activeSchools: number; draftSchools: number; suspendedSchools: number; totalBranches: number; totalTenants: number; activeTenants: number; totalOnboardings: number; }
```

> Boolean names such as `isMainBranch` and `isPrimary` come straight from the Java record components. Check them once against `/v3/api-docs` when you wire the API client.

---

## 5. Behaviour the UI must respect

1. **Only one "primary" or "main" at a time.** If you set `isMainBranch`, `isPrimary` (contact/address) or `isPrimaryBoard` to true, the server clears the flag on the previous record. Refetch the list after saving; don't patch local state optimistically.
2. **`PUT /schools/{id}` replaces every core field.** Any field you don't send becomes `null`. Prefill the form from `GET` and send the whole object.
3. **`PUT /complete-profile` only adds children.** It updates core fields that are non-null, then *appends* the branches, contacts, addresses, boards and documents in the payload. It does not replace or deduplicate them. Use it once, for the first profile wizard submit. For later edits, use the per-entity CRUD endpoints, or you will create duplicates. Also, branches created through it do **not** reset the existing main branch.
4. **Files.** The document APIs take a `fileUrl`, not a multipart upload. Upload the file to storage first, then post the URL. There is no upload endpoint in this module yet.
5. **Branches cannot be deleted.** Set `status: "INACTIVE"` via PUT instead.
6. **School status** (`PATCH /status`) is a platform-only action and the server does not validate transitions. The school dashboard should only *display* the status (for example a banner when `SUSPENDED`).
7. **Codes** (`schoolCode`, `branchCode`) are generated by the server and are read-only in the UI.
8. **Document verification** sends the verifier's id as a query param. Pass the logged-in `user.id`.

---

## 6. Suggested school-dashboard screens → APIs

| Screen | Load | Actions |
|---|---|---|
| Dashboard home | `GET /auth/me`, then `GET /schools/{schoolId}/complete-profile` (one call loads the header, counts and RM card) | |
| School profile | `GET /schools/{id}` | `PUT /schools/{id}` |
| Branches | `GET /schools/{id}/branches?page=0&size=20` | POST / PUT branch |
| Contacts | `GET /schools/{id}/contacts` | POST / PUT / DELETE |
| Addresses | `GET /schools/{id}/addresses` | POST / PUT / DELETE |
| Boards & affiliations | `GET /schools/{id}/boards` | POST / PUT / DELETE |
| Compliance documents | `GET /schools/{id}/documents` (show `verificationStatus` and `rejectionReason`) | POST / DELETE |
| My Relationship Manager | `GET /schools/{id}/relationship` | |

Academic year planning (create the year, setup checklist, year-end promotion) is covered in `tenantdata/academic/ACADEMIC_SETUP.md`.
