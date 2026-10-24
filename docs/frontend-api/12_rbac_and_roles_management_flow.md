# 12. Role-Based Access Control (RBAC) & Dynamic Roles Management (Platform, School & Community)

This comprehensive guide provides end-to-end specifications for frontend developers building the **Universal RBAC & Role Management Module** in Vidya ERP. It documents all operational tiers—**Platform Roles, School Institutional Roles, and Dynamic Custom Roles**—covering user creation, role assignment, status toggling (`ACTIVE`, `INACTIVE`, `SUSPENDED`), deletion safeguards, the **Universal Integer Permission Matrix**, and the **SuperUser Dynamic Inheritance Guarantee**.

---

## 1. Architectural Overview & Persona Taxonomy

Vidya ERP classifies operational personas into three distinct architectural tiers:

```mermaid
graph TD
    Super[PLATFORM_SUPER_ADMIN<br/>Dynamic Wildcard: Automatically Inherits ALL Current & Future Roles]

    subgraph Tier 1: Platform Administrative Roles (Master DB)
        Super -.-> PAdmin[PLATFORM_ADMIN]
        Super -.-> CAdmin[COMMUNITY_ADMIN]
        Super -.-> CRManager[COMMUNITY_ROLE_MANAGER]
        Super -.-> CManager[COMMUNITY_MANAGER]
        Super -.-> FAgent[FIELD_AGENT]
    end

    subgraph Tier 2: School Scoped Roles (School Tenant DB)
        Super -.-> SOwner[SCHOOL_OWNER]
        Super -.-> SAdmin[SCHOOL_ADMIN]
        Super -.-> Principal[PRINCIPAL]
        Super -.-> Coord[COORDINATOR]
        Super -.-> Teacher[TEACHER / CLASS_TEACHER]
        Super -.-> STeacher[SUBJECT_TEACHER]
        Super -.-> Accounts[ACCOUNTS / BURSAR]
        Super -.-> Parent[PARENT / GUARDIAN]
        Super -.-> Student[STUDENT]
    end

    subgraph Tier 3: Dynamic Custom Roles (Configurable Matrix)
        Super -.-> CRole1[NGO_LEAD_COORDINATOR]
        Super -.-> CRole2[FIELD_SURVEYOR]
        Super -.-> CRole3[CUSTOM_SCHOOL_ROLE]
    end
```

### 1.1 SuperUser Dynamic Role Inheritance Guarantee
In Vidya ERP, the SuperUser (`PLATFORM_SUPER_ADMIN`) is engineered with **future-proof, zero-maintenance role inheritance**:
- **Automatic Runtime Ingestion**: When the SuperUser authenticates, `PlatformUserDetails` automatically iterates over `PlatformRole.values()` and binds `ROLE_<role>` for **every role declared in the platform**.
- **Zero-Migration Extensibility**: If engineering adds a new role to the backend enum (e.g. `LIBRARIAN`, `TRANSPORT_MANAGER`), the SuperUser receives it automatically at boot without needing a database migration or manual role assignment.
- **Universal Permission Wildcard**: SuperUser automatically holds `EnumSet.allOf(PlatformPermission.class)` and action wildcard `-4` across sections `1` to `25`.
- **System Role Protection**: System roles (e.g. `PLATFORM_SUPER_ADMIN`, `PLATFORM_ADMIN`, `SCHOOL_ADMIN`) cannot be deleted or deactivated.

---

## 2. Universal Integer Permission Matrix

Instead of brittle boolean columns, Vidya uses a high-performance **Integer Permission Matrix**:
- **`sectionCode` (Positive Integer)**: Represents a functional module or dashboard view.
- **`actionCode` (Negative Integer)**: Represents an operational capability verb.
- **`granted` (Boolean)**: `true` to allow, `false` to deny.

### 2.1 Standard Section Codes
| Code | Section Name | Domain Scope |
| :---: | :--- | :--- |
| `1` | **Dashboard / Analytics** | Executive KPI widgets, overview metrics |
| `2` | **School Registry** | School profiles, campus branches, affiliation |
| `3` | **Academic Management** | Academic years, terms, classes, sections |
| `4` | **Students & Admissions** | Student registry, 360 profiles, enrollment |
| `5` | **Staff & Faculty** | Teachers, non-teaching staff, departments |
| `6` | **Attendance & Calendar** | Daily attendance sheets, leaves, holidays |
| `7` | **Timetable & Bell Schedules** | Period grids, room assignments, substitutions |
| `8` | **Subscriptions & Billing** | School subscription tiers, invoicing, receipts |
| `9` | **Curriculum & Subjects** | Course catalog, subject groups, syllabi |
| `10` | **Exams & Assessments** | Grading scales, exam terms, marks entry |
| `11` | **Classroom Assignment** | Class teacher and subject teacher allocations |
| `12` | **Fee Management** | Fee schedules, payments, receipts, dues |
| `13` | **Notice Board & Circulars** | Announcements, broadcast messages, alerts |
| `14` | **Library Management** | Book inventory, issuance, returns, fines |
| `15` | **Transport & Fleet** | Bus routes, vehicle fleet, driver records |
| `16` | **Hostel & Dormitory** | Hostel rooms, bed allocations, wardens |
| `17` | **Inventory & Assets** | School assets, lab equipment, stock ledger |
| `18` | **Parent Portal** | Parent 360 view, ward performance |
| `19` | **Student Portal** | Student assignments, report cards, class timetable |
| `20` | **Community Registry** | Partner organizations, NGOs, field agencies |
| `21` | **Field Operations** | School onboarding pipeline, agent visits, surveys |
| `22` | **Payroll & Commission** | Agent commissions, staff salary structures |
| `23` | **Roles & Permissions** | Custom community roles, RBAC matrix management |

### 2.2 Standard Action Codes
| Action Code | Action Verb | Description |
| :---: | :--- | :--- |
| `-9` | **View (Read-Only)** | View section overview, read records, export lists |
| `-8` | **Edit (Update / Create)** | Modify existing records, add records, save drafts |
| `-7` | **Delete (Remove)** | Soft-delete, archive, or purge records |
| `-6` | **Approve / Authorize** | Verify documents, approve marks, sanction leaves |
| `-5` | **Scoped Access** | Restricts viewing/editing to assigned class/department |
| `-4` | **Full Admin / Wildcard**| Unrestricted super-user administrative control |

---

## 3. Concrete Permission Matrix by Persona

Below is the exact permission matrix resolved dynamically upon login (`/api/v1/auth/login` or `/api/v1/auth/me`):

| Persona Role | Section 1 (Dash) | Section 4 (Students) | Section 5 (Staff) | Section 6 (Attendance) | Section 7 (Timetable) | Section 10 (Exams) | Section 12 (Fees) | Section 20 (Comm) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **PLATFORM_SUPER_ADMIN** | `-4` (All) | `-4` (All) | `-4` (All) | `-4` (All) | `-4` (All) | `-4` (All) | `-4` (All) | `-4` (All) |
| **SCHOOL_OWNER / ADMIN** | `[-9]` | `[-9, -8, -7, -6, -4]` | `[-9, -8, -7, -6, -4]` | `[-9, -8, -7, -6, -4]` | `[-9, -8, -7, -6, -4]` | `[-9, -8, -6]` | `[-9, -8, -6]` | None |
| **PRINCIPAL** | `[-9]` | `[-9, -8, -7, -6, -4]` | `[-9, -8, -6]` | `[-9]` | `[-9, -8, -6]` | `[-9, -6]` (Lock) | `[-9, -6]` | None |
| **COORDINATOR** | `[-9]` | `[-9, -8]` | `[-9]` | `[-9, -6]` | `[-9, -8]` | `[-9, -6]` | None | None |
| **CLASS_TEACHER** | `[-9]` | `[-9, -5]` (Scoped) | `[-9]` | `[-9, -8, -5]` | `[-9, -5]` | `[-9, -8, -5]` | None | None |
| **SUBJECT_TEACHER** | `[-9]` | `[-9, -5]` (Scoped) | None | `[-9, -8, -5]` | `[-9, -5]` | `[-9, -8, -5]` | None | None |
| **ACCOUNTS** | `[-9]` | `[-9]` | None | None | None | None | `[-9, -8, -6]` | None |
| **PARENT** | None | `[-9, -5]` (Ward) | None | `[-9, -5]` (Ward) | `[-9, -5]` | `[-9, -5]` | `[-9, -8]` (Pay) | None |
| **STUDENT** | None | None | None | `[-9, -5]` (Self) | `[-9, -5]` | `[-9, -5]` (Self) | None | None |
| **FIELD_AGENT** | `[-9]` | `[-9, -8]` | None | None | None | None | None | `[-9, -8, -6]` |

> **Subscription Intersection Rule**: If a school is subscribed only to the Basic Plan (which excludes Timetable or Exams), those sections are automatically stripped from the user's matrix during login regardless of their role.

---

## 4. School-Level User & Role Lifecycle APIs

School operational users (Teachers, Principals, Staff, Students, Parents) are managed within the school's tenant database via `/api/v1/tenant/users`.

Base URL: `/api/v1/tenant/users`  
*(Note: Uses Bearer token only. Automatic token-based tenant routing ensures zero headers needed!)*

| Method | Endpoint | Description | Required Authority |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/tenant/users` | Create new school user (Teacher, Staff, Student, etc.) | `USER_CREATE` or `SCHOOL_ADMIN` |
| `GET` | `/api/v1/tenant/users/{id}` | Get school operational user by UUID | `USER_READ` or `SCHOOL_ADMIN` |
| `GET` | `/api/v1/tenant/users` | Paginated listing of school users (filter by `status`, `userType`) | `USER_READ` or `SCHOOL_ADMIN` |
| `PUT` | `/api/v1/tenant/users/{id}` | Update school user details & contact info | `USER_UPDATE` or `SCHOOL_ADMIN` |
| `PATCH` | `/api/v1/tenant/users/{id}/status` | Activate, deactivate, or suspend school user | `USER_UPDATE` or `SCHOOL_ADMIN` |
| `DELETE` | `/api/v1/tenant/users/{id}` | Soft delete school user | `USER_DELETE` or `SCHOOL_ADMIN` |

---

### 4.1 Create School Operational User (`POST /api/v1/tenant/users`)

Creates a new school user with specific operational role.

#### Request Headers:
```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

#### Request Payload (`CreateTenantUserRequest`):
```json
{
  "email": "teacher.vikram@delhiheritage.edu.in",
  "phone": "+919876543220",
  "userType": "TEACHER",
  "firstName": "Vikram",
  "lastName": "Mehta",
  "gender": "MALE",
  "designation": "Senior PGT Mathematics",
  "department": "Science & Mathematics",
  "employeeId": "EMP-2026-084"
}
```

#### Response (HTTP 201 Created):
```json
{
  "success": true,
  "code": "TENANT_USER_CREATED",
  "message": "Tenant user successfully created",
  "data": {
    "id": "8b2e1a34-7c9f-4d5e-8812-9c3f4e5a6b7c",
    "userCode": "USR-TCH-00189",
    "email": "teacher.vikram@delhiheritage.edu.in",
    "phone": "+919876543220",
    "userType": "TEACHER",
    "status": "ACTIVE",
    "firstName": "Vikram",
    "lastName": "Mehta",
    "designation": "Senior PGT Mathematics",
    "department": "Science & Mathematics",
    "createdAt": "2026-10-24T16:35:00Z"
  }
}
```

---

### 4.2 List School Users with Role & Status Filters (`GET /api/v1/tenant/users`)

#### Query Parameters:
- `userType`: `ADMIN`, `TEACHER`, `STAFF`, `STUDENT`, `PARENT` (optional)
- `status`: `ACTIVE`, `INACTIVE`, `SUSPENDED` (optional)
- `page`: Page index (default: `0`)
- `size`: Page size (default: `20`)
- `sort`: Sort property (default: `createdAt,desc`)

#### Example Request:
```http
GET /api/v1/tenant/users?userType=TEACHER&status=ACTIVE&page=0&size=10
Authorization: Bearer <accessToken>
```

#### Response (HTTP 200 OK):
```json
{
  "success": true,
  "code": "TENANT_USERS_FETCHED",
  "message": "Tenant users retrieved successfully",
  "data": [
    {
      "id": "8b2e1a34-7c9f-4d5e-8812-9c3f4e5a6b7c",
      "userCode": "USR-TCH-00189",
      "email": "teacher.vikram@delhiheritage.edu.in",
      "phone": "+919876543220",
      "userType": "TEACHER",
      "status": "ACTIVE",
      "fullName": "Vikram Mehta",
      "createdAt": "2026-10-24T16:35:00Z"
    }
  ],
  "page": {
    "number": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 4.3 Update School User Status (`PATCH /api/v1/tenant/users/{id}/status`)

Enables activating, inactivating, or suspending a teacher, staff, or student.

#### Request Payload:
```json
{
  "status": "INACTIVE",
  "reason": "On sabbatical leave for academic research"
}
```

#### Response (HTTP 200 OK):
```json
{
  "success": true,
  "code": "TENANT_USER_STATUS_UPDATED",
  "message": "Tenant user status successfully updated",
  "data": {
    "id": "8b2e1a34-7c9f-4d5e-8812-9c3f4e5a6b7c",
    "userCode": "USR-TCH-00189",
    "status": "INACTIVE"
  }
}
```

---

### 4.4 Soft Delete School User (`DELETE /api/v1/tenant/users/{id}`)

Soft deletes an operational user, removing active login authority while preserving historical attendance and marks records.

#### Response (HTTP 200 OK):
```json
{
  "success": true,
  "code": "TENANT_USER_DELETED",
  "message": "Tenant user successfully deleted",
  "data": null
}
```

---

## 5. Staff Academic Role Bindings

In a school, teachers gain contextual operational authority through two specialized bindings:

### 5.1 Class Teacher Binding (`POST /api/v1/class-teacher-assignments`)
Grants scoped authority (`-5`) over daily attendance marking, student 360 overview, and consolidated report card remarks for a specific section.

```http
POST /api/v1/class-teacher-assignments
Content-Type: application/json

{
  "staffId": "8b2e1a34-7c9f-4d5e-8812-9c3f4e5a6b7c",
  "academicYearId": "2026-2027-UUID",
  "sectionId": "section-10A-UUID",
  "isPrimary": true
}
```

### 5.2 Teacher-Subject Binding (`POST /api/v1/teacher-subject-assignments`)
Grants authority to enter paper marks (`-8`) and view timetables for specific subjects in specific sections.

```http
POST /api/v1/teacher-subject-assignments
Content-Type: application/json

{
  "staffId": "8b2e1a34-7c9f-4d5e-8812-9c3f4e5a6b7c",
  "sectionId": "section-10A-UUID",
  "subjectId": "subject-math-UUID"
}
```

---

## 6. Dynamic Custom Roles Lifecycle APIs

Base URL: `/api/v1/communities/roles`

| Method | Endpoint | Description | Guard / Permission |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/communities/roles` | Create new custom role with permission matrix | Section `23`, Action `-8` |
| `GET` | `/api/v1/communities/roles/{id}` | Fetch role definition by UUID | Section `23`, Action `-9` |
| `GET` | `/api/v1/communities/roles/type/{communityType}` | List all roles for a community type (`NGO`, `FIELD_AGENCY`) | Section `23`, Action `-9` |
| `PUT` | `/api/v1/communities/roles/{id}` | Update role details and modify permission matrix | Section `23`, Action `-8` |
| `PATCH` | `/api/v1/communities/roles/{id}/status?active={true\|false}` | Activate or deactivate role | Section `23`, Action `-8` |
| `DELETE` | `/api/v1/communities/roles/{id}` | Delete custom non-system role | Section `23`, Action `-7` |
| `POST` | `/api/v1/communities/roles/members/assign` | Assign custom role to a community member | Section `23`, Action `-8` |
| `GET` | `/api/v1/communities/roles/my-permissions` | Returns current caller's active permissions matrix | Authenticated |
| `GET` | `/api/v1/communities/roles/{id}/audit-logs` | Fetch audit trail of changes made to role | Section `23`, Action `-9` |

---

### 6.1 Create Custom Role (`POST /api/v1/communities/roles`)

#### Request Payload (`CreateCommunityRoleRequest`):
```json
{
  "roleCode": "NGO_SENIOR_COORDINATOR",
  "roleName": "Senior Program Coordinator",
  "roleDescription": "Oversees school onboarding pipeline, surveys, and student admissions in North Zone",
  "communityType": "NGO",
  "level": 2,
  "permissions": [
    { "sectionCode": 4, "actionCode": -9, "granted": true },
    { "sectionCode": 4, "actionCode": -8, "granted": true },
    { "sectionCode": 4, "actionCode": -6, "granted": true },
    { "sectionCode": 20, "actionCode": -9, "granted": true },
    { "sectionCode": 21, "actionCode": -9, "granted": true },
    { "sectionCode": 21, "actionCode": -8, "granted": true },
    { "sectionCode": 21, "actionCode": -6, "granted": true }
  ]
}
```

---

### 6.2 Toggle Role Status (`PATCH /api/v1/communities/roles/{id}/status?active={true|false}`)

Activates or deactivates a custom role. System roles are protected against deactivation.

#### Response:
```json
{
  "success": true,
  "code": "COMMUNITY_ROLE_UPDATED",
  "message": "Role status updated successfully",
  "data": {
    "id": "77372134-8c85-4df3-bc53-a3d24ea26198",
    "roleCode": "NGO_SENIOR_COORDINATOR",
    "isActive": false
  }
}
```

---

### 6.3 Delete Role (`DELETE /api/v1/communities/roles/{id}`)

#### Safeguards:
1. **System Role Protection**: Cannot delete if `isSystemRole == true` (returns `400 Bad Request`).
2. **Active Member Guard**: Cannot delete if assigned to active members (returns `400 Bad Request` with "Cannot delete role because it is currently assigned to active members. Reassign members first.").

---

## 7. Frontend UI Integration & Guards

### 7.1 Universal Permission Check Utility (`src/utils/permissions.ts`)

```typescript
export type PermissionsMatrix = Record<number, number[]>;

/**
 * Checks if the user holds authority to execute an action on a target section.
 *
 * @param matrix The user's permissionsMatrix from auth state
 * @param sectionCode The integer code of the target section (e.g. 4 for Students, 6 for Attendance)
 * @param actionCode The required action code (e.g. -9 for View, -8 for Edit, -6 for Approve)
 */
export function hasPermission(
  matrix: PermissionsMatrix | undefined | null,
  sectionCode: number,
  actionCode: number
): boolean {
  if (!matrix) return false;
  const actions = matrix[sectionCode];
  if (!actions || !Array.isArray(actions)) return false;

  // Wildcard full admin (-4) automatically grants all actions
  if (actions.includes(-4)) return true;

  // Exact action match or scoped match
  return actions.includes(actionCode);
}
```

---

### 7.2 Reusable React `<PermissionGuard>` Component

```tsx
import React from 'react';
import { useAuth } from '../context/AuthContext';
import { hasPermission } from '../utils/permissions';

interface PermissionGuardProps {
  section: number;
  action: number;
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

export const PermissionGuard: React.FC<PermissionGuardProps> = ({
  section,
  action,
  fallback = null,
  children,
}) => {
  const { user } = useAuth();
  const allowed = hasPermission(user?.permissionsMatrix, section, action);

  if (!allowed) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
};
```

---

### 7.3 School Staff User Table with Role Badges & Status Toggle

```tsx
import React from 'react';
import { PermissionGuard } from './PermissionGuard';

interface SchoolUserRow {
  id: string;
  fullName: string;
  email: string;
  userType: string;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
}

export const SchoolUserTable: React.FC<{
  users: SchoolUserRow[];
  onToggleStatus: (userId: string, currentStatus: string) => void;
  onEdit: (userId: string) => void;
  onDelete: (userId: string) => void;
}> = ({ users, onToggleStatus, onEdit, onDelete }) => {
  return (
    <table className="table table-hover align-middle">
      <thead className="table-light">
        <tr>
          <th>Name & Email</th>
          <th>Role / Type</th>
          <th>Status</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {users.map((u) => (
          <tr key={u.id}>
            <td>
              <div className="fw-bold">{u.fullName}</div>
              <small className="text-muted">{u.email}</small>
            </td>
            <td>
              <span className={`badge ${u.userType === 'TEACHER' ? 'bg-primary' : 'bg-info'}`}>
                {u.userType}
              </span>
            </td>
            <td>
              <div className="form-check form-switch">
                <input
                  type="checkbox"
                  className="form-check-input"
                  checked={u.status === 'ACTIVE'}
                  onChange={() => onToggleStatus(u.id, u.status)}
                />
                <span className={`badge ${u.status === 'ACTIVE' ? 'bg-success' : 'bg-secondary'}`}>
                  {u.status}
                </span>
              </div>
            </td>
            <td>
              <PermissionGuard section={5} action={-8}>
                <button className="btn btn-sm btn-outline-secondary me-2" onClick={() => onEdit(u.id)}>
                  Edit
                </button>
              </PermissionGuard>
              <PermissionGuard section={5} action={-7}>
                <button className="btn btn-sm btn-outline-danger" onClick={() => onDelete(u.id)}>
                  Delete
                </button>
              </PermissionGuard>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
};
```
