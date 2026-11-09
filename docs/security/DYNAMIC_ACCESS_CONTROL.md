# Dynamic access control, sessions and login history

Roles, permissions, role assignments and per-school feature switches are stored in the master database.
Adding a row (or calling the admin API) changes access without a deploy.

- Schema and seed: `src/main/resources/db/migration/master/V29__dynamic_access_control_sessions_and_login_history.sql`
- Code: `com.vidya.security.access` (roles and permissions), `com.vidya.security.session` (sessions and login history)

---

## 1. Model

```
platform_user.platform_role ──► access_role (system role, same code)   ← the account type
user_role_assignment ─────────► access_role (extra roles, global or school custom)
access_role ──(access_role_permission)──► access_permission ──feature_code──► subscription_feature
user_permission_override (GRANT / DENY, per user)
```

| Table | Purpose |
|---|---|
| `access_permission_matrix_cell` | Frontend `permissionsMatrix` cells a permission lights up (see §6). |
| `access_permission` | Catalog. `permission_code` is the exact string checked by `@PreAuthorize("hasAuthority('CODE')")`. `scope` is PLATFORM / SCHOOL / COMMUNITY. `feature_code` (optional) ties it to a subscription module. |
| `access_role` | `school_id IS NULL` means a global role. Otherwise it is that school's custom role. `is_system` marks the 17 account-type roles (they can't be deleted). `grants_all_permissions` exists only on `PLATFORM_SUPER_ADMIN`. |
| `access_role_permission` | Role → permission grants. |
| `user_role_assignment` | Extra roles per user, optionally time-boxed (`valid_from` / `valid_until`), e.g. an acting principal for a term. |
| `user_permission_override` | One-off GRANT or DENY for one user. DENY always wins. |
| `user_session` | One row per login (device). |
| `login_history` | Append-only audit of logins, failures, lockouts, logouts, refreshes, revocations and token reuse. |

`platform_user.platform_role` is still an enum, but it only means **account type and scope** (PLATFORM, SCHOOL, SCHOOL_PORTAL, COMMUNITY). It holds no permissions anymore.

### How a user's permissions are computed (`EffectiveAccessService`)

1. Roles = the active system role whose code equals the account type, plus every effective assignment.
   - A school custom role counts only for users of that school.
   - The role's scope must fit the account: a SCHOOL account may also hold SCHOOL_PORTAL roles.
2. Permissions = the active grants of those roles. A `grants_all_permissions` role gets every active permission.
3. GRANT overrides are added, then DENY overrides are removed.
4. School-bound accounts (SCHOOL, SCHOOL_PORTAL) then lose:
   - every PLATFORM-scope permission;
   - every permission whose `feature_code` is not enabled for their school. "Enabled" means it's included in the active plan or switched on by an override. A catalog feature with `status = 'INACTIVE'` is off for everyone.

Only global roles become `ROLE_*` authorities, so a school can't create a role named `PLATFORM_ADMIN` to pass `hasRole(...)` checks. The DB also blocks custom roles from using the 17 account-type codes.

### When changes take effect

| How you change it | Takes effect |
|---|---|
| Admin APIs below | Immediately (cache evicted after commit) on the instance that served the call. Other instances pick it up within the TTL. |
| Direct SQL | Within `vidya.security.access.cache-ttl-seconds` (default 60s), or immediately after `POST /api/v1/admin/access/cache/evict`. |
| New permission used in a new `@PreAuthorize` | Needs a deploy for the annotation itself; the grant is data. |

Config (`application.yml`):

```yaml
vidya.security.access.cache-ttl-seconds: 60       # VIDYA_ACCESS_CACHE_TTL_SECONDS
vidya.security.access.feature-gating-enabled: true # VIDYA_ACCESS_FEATURE_GATING
vidya.security.session.max-active-per-user: 10     # VIDYA_MAX_SESSIONS_PER_USER
```

---

## 2. Recipes (no deploy)

### Give an existing role an existing permission

```sql
INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM access_role r, access_permission p
WHERE r.role_code = 'TEACHER' AND r.school_id IS NULL AND p.permission_code = 'FEE_READ'
ON CONFLICT DO NOTHING;
```

The same call through the API is `POST /api/v1/admin/access/roles/{roleId}/permissions` with `{"permissionCodes":["FEE_READ"]}`.

### Launch a new module, e.g. Hostel, sold as an add-on

```sql
-- 1. The sellable feature (subscription catalog)
INSERT INTO subscription_feature (feature_code, name, description, category, status,
                                  parent_module_code, sub_category, base_price, is_addon, display_order)
VALUES ('HOSTEL', 'Hostel Management', 'Rooms, allotment, mess, warden', 'OTHER', 'ACTIVE',
        'HOSTEL', 'OPERATIONS', 500.00, TRUE, 90);

-- 2. Its permissions, gated by the feature
INSERT INTO access_permission (permission_code, name, module_code, scope, feature_code) VALUES
  ('HOSTEL_READ',   'View hostel',   'HOSTEL', 'SCHOOL', 'HOSTEL'),
  ('HOSTEL_MANAGE', 'Manage hostel', 'HOSTEL', 'SCHOOL', 'HOSTEL');

-- 3. Who gets them by default
INSERT INTO access_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM access_role r JOIN access_permission p
  ON p.permission_code IN ('HOSTEL_READ', 'HOSTEL_MANAGE')
WHERE r.school_id IS NULL AND r.role_code IN ('SCHOOL_OWNER', 'SCHOOL_ADMIN')
ON CONFLICT DO NOTHING;
```

The endpoints still need `@PreAuthorize("hasAuthority('HOSTEL_MANAGE') and @tenantAuthorizationService.canAccessSchool(authentication, #schoolId)")`. That annotation is the only part that needs code.

### Turn a module on or off for one school

Include it in the school's plan (`subscription_plan_feature`), or override per school (platform admin, `SUBSCRIPTION_MANAGE`):

```
PUT /api/v1/schools/{schoolId}/subscriptions/feature-overrides
{ "featureCode": "HOSTEL", "isEnabled": true, "addonPrice": 500, "notes": "Custom plan" }
```

`DELETE …/feature-overrides/HOSTEL` returns the school to its plan's setting. Both calls evict the access cache, so users of that school gain or lose the HOSTEL permissions right away.

Turn a module off for **every** school: `UPDATE subscription_feature SET status = 'INACTIVE' WHERE feature_code = 'HOSTEL';`

### A school creates its own role (self-service)

```
POST /api/v1/schools/{schoolId}/access/roles
{ "code": "HOSTEL_WARDEN", "name": "Hostel Warden", "permissionCodes": ["HOSTEL_READ", "HOSTEL_MANAGE"] }

POST /api/v1/schools/{schoolId}/access/users/{userId}/roles
{ "roleId": "<id>", "validUntil": "2027-03-31T23:59:59Z", "notes": "Term 2" }
```

### Block one person from one thing

```
PUT /api/v1/admin/access/users/{userId}/permission-overrides
{ "permissionCode": "FEE_MANAGE", "effect": "DENY", "reason": "Under audit" }
```

### Sign someone out everywhere (staff left, lost phone)

- `POST /api/v1/admin/users/{userId}/sessions/revoke-all` (platform)
- `POST /api/v1/schools/{schoolId}/users/{userId}/sessions/revoke-all` (school admin)

This takes effect on the user's very next request, because every request checks the session.

---

## 3. Guard rails

- **No self-escalation.** A non-super-user can only grant permissions they hold themselves. This covers putting a permission in a role, assigning a role, and creating a GRANT override.
- **Super-user role.** Only a super user can assign or edit the `grants_all_permissions` role, and it can't be deactivated.
- **Scope rules.**
  - School and portal roles may contain only SCHOOL permissions.
  - COMMUNITY roles may not contain PLATFORM permissions.
  - School accounts can never hold PLATFORM permissions, even through an override.
- **School isolation.** School APIs only see the school's own custom roles, read-only global school roles, and the school's own users. Anything else returns 404.
- **System roles** can't be deleted. Their permissions can be edited (platform only).

---

## 4. API reference

### Platform: `/api/v1/admin/access` (ACCESS_CONTROL_READ / ACCESS_CONTROL_MANAGE)

| Method | Path | |
|---|---|---|
| GET / POST | `/permissions` | List / create permission |
| PUT | `/permissions/{id}` | Update (name, module, scope, feature gate, active) |
| GET / POST | `/roles` | List all (`?includePermissions=true`) / create global role |
| GET / PUT / DELETE | `/roles/{id}` | |
| PUT / POST | `/roles/{id}/permissions` | Replace / add `{permissionCodes:[…]}` |
| DELETE | `/roles/{id}/permissions/{code}` | |
| GET / POST | `/users/{userId}/roles` | List (includes the account-type role, `primary=true`) / assign |
| DELETE | `/users/{userId}/roles/{assignmentId}` | |
| GET / PUT | `/users/{userId}/permission-overrides` | |
| DELETE | `/users/{userId}/permission-overrides/{id}` | |
| GET | `/users/{userId}/effective-access` | Roles, permissions, enabled features right now |
| POST | `/cache/evict` | Apply direct SQL edits immediately (this instance) |

### School: `/api/v1/schools/{schoolId}/access` (SCHOOL_ROLE_READ / SCHOOL_ROLE_MANAGE + own school)

`GET /permissions` (assignable), `GET|POST /roles`, `GET|PUT|DELETE /roles/{id}`, `PUT /roles/{id}/permissions`,
`GET /users`, `GET|POST /users/{userId}/roles`, `DELETE /users/{userId}/roles/{assignmentId}`, `GET /users/{userId}/effective-access`.

### Sessions and login history

| Who | Endpoints |
|---|---|
| Me (any signed-in user) | `GET /api/v1/auth/sessions?activeOnly=true`, `DELETE /api/v1/auth/sessions/{id}`, `POST /api/v1/auth/sessions/revoke-others`, `POST /api/v1/auth/logout-all`, `GET /api/v1/auth/login-history` |
| Platform (SESSION_READ / SESSION_MANAGE) | `GET /api/v1/admin/users/{userId}/sessions`, `DELETE …/sessions/{id}`, `POST …/sessions/revoke-all`, `GET /api/v1/admin/users/{userId}/login-history` |
| School (SCHOOL_SESSION_MANAGE + own school) | `GET /api/v1/schools/{schoolId}/users/{userId}/sessions`, `POST …/sessions/revoke-all`, `GET /api/v1/schools/{schoolId}/login-history` |

---

## 5. Sessions: how they work

- Login creates a `user_session` row and puts its id in the `sid` claim of both tokens. The refresh token row stores `session_id`.
- **Every request** checks that the session is still ACTIVE. Logout, revocation and expiry therefore apply immediately, not when the access token expires. `last_seen_at` is updated at most once a minute.
- Refresh needs an active session; it rotates the refresh token and slides the session's expiry.
- **Refresh-token reuse** (a revoked token presented again) is treated as theft. All of the user's sessions and tokens are revoked, and `TOKEN_REUSE_DETECTED` is logged.
- Logging in on more than `max-active-per-user` devices ends the oldest session.
- Each session records IP, user agent, device type, browser and OS for the "your devices" screen.

---

## 6. The three layers: RBAC, permissions, data scope

| Layer | Decides | Where it lives | Enforced by |
|---|---|---|---|
| RBAC | which permissions a role gives | `access_role`, `access_role_permission`, `user_role_assignment` | `EffectiveAccessService` |
| Permission-based | which operations are allowed | `access_permission` (+ overrides, feature gating) | `@PreAuthorize("hasAuthority('X')")` |
| Data-level | which records the user may touch | `<DOMAIN>_SCOPE_ALL` permissions + tenant links | `@PreAuthorize("… and @dataAccess.…")` |

### Data-level access (V31, tenant V19)

For each area (`STUDENT`, `ATTENDANCE`, `MARKS`; `TIMETABLE`, `STAFF`, `FEE`, `CHAT` are reported but not enforced yet), a user's scope is:

- **ALL**: every record of their school. This applies to platform and community accounts, whose school access is checked by tenant resolution, and to school users holding `<DOMAIN>_SCOPE_ALL`.
- **OWN**: the default for every school user, so a new custom role never sees the whole school by accident. OWN means:
  - *Student account*: their own `student` row (`student.tenant_user_id → tenant_user.master_user_id = login`).
  - *Parent account*: students whose `student_guardian.guardian_user_id` is the parent's `tenant_user`. Set it with `guardianUserId` on the guardian create/update API. Tenant V19 back-filled it by exact, unique email match.
  - *Teacher / staff*: students currently enrolled (`ADMITTED`/`ACTIVE`) in sections where they are an active class teacher or subject teacher. A subject assignment without a section covers the whole class.

Seeded ALL holders:

| Role | STUDENT | ATTENDANCE | MARKS |
|---|---|---|---|
| School owner / admin | ALL | ALL | ALL |
| Principal | ALL | ALL | ALL |
| Coordinator | ALL | ALL | ALL |
| Accounts | ALL | own | own |
| Teacher, subject teacher, school staff, parent, student | own | own | own |

Give a role school-wide reach with data only, e.g. `ATTENDANCE_SCOPE_ALL` on a custom `EXAM_CELL` role.

What is enforced, and how:

| Endpoint | OWN users |
|---|---|
| `GET /students` (search), `GET /classes/{id}/students` | ALL only, so OWN users get 403. Use **`GET /api/v1/students/mine`** (self, children or taught students). |
| `GET/PUT/PATCH/DELETE /students/{id}`, `/students/code/{code}`, `/students/{id}/profile`, `/students/{studentId}/guardians/**` | only linked students |
| `GET /classes/{c}/sections/{s}/students`, `/attendance/sections/{id}[/daily]`, `/attendance/missing` | only sections they teach (never parents or students) |
| `POST /attendance`, `/attendance/bulk` | section they teach **and** every student in the request linked |
| `/attendance/{id}`, `/attendance/students/{studentId}[/summary]` | the record's student must be linked |
| `/exams/marks/student/{studentId}`, `/exams/marks/{id}` | the student must be linked |
| `/exams/marks/subject-config/{id}` (read, submit, lock), `/exams/marks/bulk` | they teach that subject in that class (+ every student linked) |

The checks live in `com.vidya.tenantdata.security.DataAccessService` (bean `dataAccess`). Own records are resolved once per request from the tenant DB. To protect a new endpoint:

```java
@PreAuthorize("hasAuthority('HOSTEL_READ') and @dataAccess.canAccessStudent(authentication, #studentId, 'STUDENT')")
```

`PreAuthorizeExpressionsTest` fails the build if an expression references a parameter or `@dataAccess` method that doesn't exist.

---

## 7. Access in the JWT (for the frontend)

The **database stays the source of truth**. The access token carries a **snapshot** of effective access so the UI can drive menus, routes, buttons and data views from one place:

```jsonc
{
  "sub": "…user id…", "sid": "…session…", "type": "ACCESS", "exp": …,
  "role": "TEACHER", "schoolId": "…", "tenantCode": "TNT-001",
  "perms": ["ATTENDANCE_BULK_MARK", "ATTENDANCE_READ", "STUDENT_READ", …], // sorted
  "su": true,                      // only for super users, instead of "perms"
  "roles": ["TEACHER", "HOSTEL_WARDEN"],
  "scope": "SCHOOL",               // PLATFORM | SCHOOL | SCHOOL_PORTAL | COMMUNITY
  "features": ["ATTENDANCE", "EXAMINATION", …],
  "ds": { "STUDENT": "OWN", "ATTENDANCE": "OWN", "MARKS": "OWN", … },
  "av": "3f9c0a1b2c3d4e5f"         // access version
}
```

- **The server never trusts these claims.** Every request re-resolves access from the DB (cached ≤ 60s) and checks the session. A tampered or stale snapshot can hide or show a button, but can't grant anything.
- **Staleness.** Every authenticated response has an `X-Access-Version` header with the current fingerprint. When it differs from the token's `av`, call `POST /api/v1/auth/refresh`; the new access token carries the new snapshot. Access tokens also expire every 30 minutes.
- **Size.** A school admin's token is about 4 KB and a platform admin's about 4.5 KB. Super users get `su: true` instead of the full list. If proxies limit header size, set `VIDYA_JWT_EMBED_ACCESS=false` and read the same data from `/auth/me`.
- `/auth/me` returns the same data (`permissions`, `roles`, `enabledFeatures`, `dataScopes`, `accessVersion`).

### Frontend: one central permission layer

Never compare role names in components. Read the snapshot once and expose helpers:

```ts
// access.ts — the only place that knows where access comes from
import { jwtDecode } from 'jwt-decode';

type Scope = 'ALL' | 'OWN';
interface AccessClaims {
  perms?: string[]; su?: boolean; roles: string[]; scope: string;
  features: string[]; ds: Record<string, Scope>; av: string;
}

let claims: AccessClaims | null = null;
let perms = new Set<string>();

export function setToken(accessToken: string) {
  claims = jwtDecode<AccessClaims>(accessToken);
  perms = new Set(claims.perms ?? []);
}

export const can = (p: string) => !!claims && (claims.su === true || perms.has(p));
export const canAny = (...p: string[]) => p.some(can);
export const canAll = (...p: string[]) => p.every(can);
export const hasFeature = (f: string) => !!claims?.features.includes(f);
export const dataScope = (domain: string): Scope => claims?.su ? 'ALL' : claims?.ds[domain] ?? 'OWN';

// http interceptor: refresh when the server says access changed
export function onResponse(res: Response) {
  const v = res.headers.get('X-Access-Version');
  if (v && claims && v !== claims.av) refreshTokens(); // POST /auth/refresh, then setToken(newAccessToken)
}
```

```tsx
// routes / menus / buttons all go through the same helpers
{ path: '/attendance', element: <Guard perm="ATTENDANCE_READ"><Attendance/></Guard> }
{can('STUDENT_CREATE') && <Button>Add student</Button>}
const students = dataScope('STUDENT') === 'ALL' ? api.searchStudents(q) : api.myStudents(); // GET /students/mine
```

Keep a `PERMISSIONS` constant file on the frontend that mirrors the permission codes the screens use. New permissions only need adding there when a new screen uses them.

---

## 8. Frontend notes

`GET /api/v1/auth/me` (and the `user` object in the login/refresh response) now includes:

```jsonc
{
  "platformRole": "TEACHER",         // account type
  "accountScope": "SCHOOL",
  "roles": ["TEACHER", "HOSTEL_WARDEN"],
  "permissions": ["STUDENT_READ", "ATTENDANCE_MARK", "HOSTEL_MANAGE", ...],
  "enabledFeatures": ["ATTENDANCE", "STUDENT_MANAGEMENT", "HOSTEL", ...],
  "dataScopes": { "STUDENT": "OWN", "ATTENDANCE": "OWN", "MARKS": "OWN", ... },
  "accessVersion": "3f9c0a1b2c3d4e5f",
  "sessionId": "…"
}
```

- Show or hide menus and buttons by `permissions` (or the token's `perms`, see §7), not by role names. Roles are editable data now.
- `enabledFeatures` tells you which modules the school has. Use it for an "upgrade to unlock" state; permissions already account for it.
- After an admin changes someone's access, it applies on the server within a minute. The `X-Access-Version` header then changes, and the UI refreshes its token (§7).
- A 401 on any call can now mean "session revoked". Clear tokens and send the user to login.

---

### `permissionsMatrix` (legacy numeric matrix)

`/auth/me` still returns `permissionsMatrix` (section code → action codes) for screens that already use it. It is now **built from the same effective permissions** (V30): each permission lights up cells listed in `access_permission_matrix_cell`. Roles, assignments, overrides and feature gating therefore apply to it, and it can't disagree with the API. Community members also get their custom community role's cells, as before.

| Section | Code | | Action | Code |
|---|---|---|---|---|
| Students | 4 | | VIEW | -9 |
| Staff | 11 | | EDIT | -8 |
| Attendance | 12 | | DELETE | -7 |
| Fees | 13 | | APPROVE | -6 |
| Marks / exams | 14 | | SCOPED (own records) | -5 |
| Timetable | 15 | | FULL (all records) | -4 |
| Chat | 16 | | | |
| Library | 17 | | | |
| Community | 20 | | | |

- Show a new section, or move an action, with data only:
  ```sql
  INSERT INTO access_permission_matrix_cell (permission_id, section_code, action_code)
  SELECT id, 21, -9 FROM access_permission WHERE permission_code = 'HOSTEL_READ';
  ```
  Or call `PUT /api/v1/admin/access/permissions/{id}` with `"matrixCells": [{"sectionCode": 21, "actionCode": -9}]`.
- FULL comes from the `*_SCOPE_ALL` permissions, which are also what the server enforces for STUDENT, ATTENDANCE and MARKS (§6). SCOPED comes from the `*_SCOPE_OWN` marker permissions. OWN is the default anyway; the markers only light up the matrix cell.
- V30 also adds `FEE_*`, `LIBRARY_*` and `CHAT_*` permissions, so those menus can be controlled before their APIs exist.
- New UI code should check `permissions` strings instead. The matrix is kept for compatibility.

## 9. Upgrade notes (deploying master V29–V31, tenant V19)

- **Everyone must log in again.** Old access tokens have no `sid` and are rejected; V29 revokes all existing refresh tokens.
- Feature gating now applies to student, staff, attendance, timetable and exam permissions. A school with no active plan that includes those features loses them. Check that every live school has an active subscription before deploying, or deploy with `VIDYA_ACCESS_FEATURE_GATING=false` and switch it on after.
- The `hasRole('SCHOOL_ADMIN')`-style bypasses in `@PreAuthorize` were removed. The DB grants are the only source of truth; the seed copies the old enum grants, including what the bypasses used to allow.
- `platform_user.platform_role` now accepts all 17 account types. Before V29, TEACHER, PRINCIPAL and the others were rejected by a check constraint.
- Community roles (`community_role_definition`) are unchanged.
- `permissionsMatrix` is no longer hardcoded per account type (V30). Where the old matrix promised an action the API rejects, the matrix now matches the API. For example, principals no longer see Delete on students, and field agents only see the community section if they hold `COMMUNITY_READ` or a custom community role grants it. To keep a section visible for a role, grant that role the permission.
- **Data-level access is now enforced (V31).** Teachers, subject teachers, school staff and portal users only reach students they are linked to. Before deploying:
  - check that class-teacher and subject-teacher assignments are filled in;
  - check that parent logins are linked to guardians via `guardianUserId` (V19 links exact email matches);
  - check that students' `tenant_user` rows point at their logins (`master_user_id`).
  
  Otherwise those users will get 403 on student, attendance and marks screens. `SCHOOL_STAFF` has no scope permissions; grant `STUDENT_SCOPE_ALL` if office staff need the full student list.
- The full student search (`GET /students`) and class lists now need STUDENT scope ALL. Portal and teacher screens should use `GET /students/mine`.
