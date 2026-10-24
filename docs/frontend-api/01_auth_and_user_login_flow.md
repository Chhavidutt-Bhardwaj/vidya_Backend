# 1. Authentication & Unified Login Flow (Platform & School Users)

This document provides frontend engineers with end-to-end technical specifications for integrating the **Unified Authentication System** in Vidya ERP. It details user authentication for both **Platform Users** (Super Admins, Community Admins, Field Agents) and **School Users** (Principals, Teachers, School Admins, Accounts, Students, Parents), token lifecycle, zero-header automatic tenant resolution, and account security.

---

## 1. Architectural Overview & Tenant Binding

Vidya ERP employs a unified authentication entry point backed by the **Master Database** (`vidya_master_db`). Regardless of whether a user is an executive platform super-user, an NGO community coordinator, or a classroom teacher in a specific school, all authentication requests flow through `/api/v1/auth/login`.

```mermaid
graph TD
    UserClient([Client App Web / Mobile])
    LoginAPI["POST /api/v1/auth/login<br/>Master DB: platform_user"]
    JWT["JWT Access Token<br/>(claims: role, tenantCode, schoolId)"]
    TenantFilter[TenantResolutionFilter]
    MasterDB[(vidya_master_db)]
    TenantDB[(vidya_tenant_db<br/>School Scoped)]

    UserClient -->|1. Submit email + password| LoginAPI
    LoginAPI -->|2. Verify credentials & account status| LoginAPI
    LoginAPI -->|3. Issue JWT with embedded tenantCode claim| JWT
    JWT -->|4. Store in Auth Store| UserClient

    UserClient -->|5. Subsequent API Requests<br/>(Bearer token, NO X-Tenant-ID header)| TenantFilter
    TenantFilter -->|Case A: schoolId is NULL (Platform/Community)| MasterDB
    TenantFilter -->|Case B: schoolId != NULL (School User - Auto Resolved)| TenantDB
```

### Key Architectural Tenets:
1. **Unified Credential Store**: All users authenticate using email and password against the master identity table `platform_user`.
2. **Zero-Header Multi-Tenancy**:
   - For **School Users** (teachers, principals, school admins), their assigned `tenantCode` is embedded directly into the JWT access token claims. The backend `TenantResolutionFilter` resolves the school tenant automatically from the token. **The frontend does NOT need to pass the `X-Tenant-ID` header.**
   - For **Platform & Community Users** (`schoolId == null`), `tenantCode` is `null`. They operate directly on the Master DB with zero tenant header requirement.
3. **Dynamic Permissions Matrix**: The login response includes a pre-computed `permissionsMatrix` mapping integer section codes to action codes, automatically intersected with the school's active subscription modules.
4. **Brute-Force & Lockout Protection**: Accounts are automatically locked for 15 minutes after 5 consecutive failed login attempts.

---

## 2. API Endpoints Reference

Base URL: `/api/v1/auth`

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/v1/auth/login` | Authenticate platform or school user; returns JWT & permissions matrix | No |
| `POST` | `/api/v1/auth/refresh` | Refresh expired access token using rotating refresh token | No |
| `POST` | `/api/v1/auth/logout` | Revoke active refresh token session | Yes (`Bearer`) |
| `GET` | `/api/v1/auth/me` | Fetch authenticated user profile & updated permissions matrix | Yes (`Bearer`) |
| `POST` | `/api/v1/auth/change-password` | Update account password with old password verification | Yes (`Bearer`) |

---

## 3. Login Flow & Payload Specifications

### 3.1 User Login (`POST /api/v1/auth/login`)

Authenticates any platform or school user.

#### Request Headers:
```http
Content-Type: application/json
```

#### Request Payload:
```json
{
  "email": "teacher.anita@delhiheritage.edu.in",
  "password": "Password@123"
}
```

---

#### Case A: School User Login (Teacher / Principal / School Admin)
When a school user logs in, `schoolId` and `tenantCode` are present in the response.

#### Response (HTTP 200 OK):
```json
{
  "success": true,
  "code": "LOGIN_SUCCESS",
  "message": "Authentication successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "4a7f05b1-e7c6-48c9-9441-ec96e7fe20da",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "e41ea4bf-a879-4b4d-9441-ec96e7fe20da",
      "userCode": "USR-TCH-00142",
      "email": "teacher.anita@delhiheritage.edu.in",
      "firstName": "Anita",
      "lastName": "Sharma",
      "phone": "+919876543210",
      "role": "TEACHER",
      "status": "ACTIVE",
      "schoolId": "f0000000-0000-0000-0000-000000000001",
      "tenantCode": "SCH-DELHIPUB01",
      "permissionsMatrix": {
        "4": [-9, -5],
        "6": [-9, -8, -5],
        "7": [-9, -5],
        "10": [-9, -8]
      }
    }
  },
  "timestamp": "2026-10-24T16:15:00Z"
}
```

---

#### Case B: SuperUser & Platform User Login (Super Admin / Community Agent)
When a platform user logs in, `schoolId` and `tenantCode` are `null`. SuperUser has unrestricted access across all platform sections.

#### Response (HTTP 200 OK):
```json
{
  "success": true,
  "code": "LOGIN_SUCCESS",
  "message": "Authentication successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "bf0ad88c-03a4-4347-ad84-10d20af7993f",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "a0000000-0000-0000-0000-000000000000",
      "userCode": "USR-SUPERADMIN01",
      "email": "superuser@vidya.io",
      "firstName": "Super",
      "lastName": "User",
      "phone": "+919999999999",
      "role": "PLATFORM_SUPER_ADMIN",
      "status": "ACTIVE",
      "schoolId": null,
      "tenantCode": null,
      "permissionsMatrix": {
        "1": [-9, -8, -7, -6, -5, -4],
        "2": [-9, -8, -7, -6, -5, -4],
        "3": [-9, -8, -7, -6, -5, -4],
        "4": [-9, -8, -7, -6, -5, -4],
        "5": [-9, -8, -7, -6, -5, -4],
        "6": [-9, -8, -7, -6, -5, -4],
        "7": [-9, -8, -7, -6, -5, -4],
        "8": [-9, -8, -7, -6, -5, -4],
        "9": [-9, -8, -7, -6, -5, -4],
        "10": [-9, -8, -7, -6, -5, -4],
        "20": [-9, -8, -7, -6, -5, -4],
        "21": [-9, -8, -7, -6, -5, -4],
        "22": [-9, -8, -7, -6, -5, -4],
        "23": [-9, -8, -7, -6, -5, -4]
      }
    }
  },
  "timestamp": "2026-10-24T16:15:00Z"
}
```

---

### 3.2 Token Refresh (`POST /api/v1/auth/refresh`)

When an access token expires, use the refresh token to obtain a new pair.

#### Request Payload:
```json
{
  "refreshToken": "4a7f05b1-e7c6-48c9-9441-ec96e7fe20da"
}
```

#### Response (HTTP 200 OK):
```json
{
  "success": true,
  "code": "TOKEN_REFRESHED",
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new...",
    "refreshToken": "8b8e05c2-f8d7-49d0-a552-fd07f8gf31eb",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```
> **Security Notice**: Refresh tokens are rotated on each use. If an old, already-rotated refresh token is re-submitted, the backend detects reuse and immediately revokes all active tokens for that user account.

---

### 3.3 User Profile (`GET /api/v1/auth/me`)

Fetches the current user profile, role, and active permissions matrix.

#### Headers:
```http
Authorization: Bearer <accessToken>
```

#### Response (HTTP 200 OK):
```json
{
  "success": true,
  "code": "USER_PROFILE_RETRIEVED",
  "message": "User profile fetched successfully",
  "data": {
    "id": "e41ea4bf-a879-4b4d-9441-ec96e7fe20da",
    "userCode": "USR-TCH-00142",
    "email": "teacher.anita@delhiheritage.edu.in",
    "firstName": "Anita",
    "lastName": "Sharma",
    "phone": "+919876543210",
    "role": "TEACHER",
    "status": "ACTIVE",
    "schoolId": "f0000000-0000-0000-0000-000000000001",
    "tenantCode": "SCH-DELHIPUB01",
    "permissionsMatrix": {
      "4": [-9, -5],
      "6": [-9, -8, -5],
      "7": [-9, -5],
      "10": [-9, -8]
    }
  }
}
```

---

### 3.4 Change Password (`POST /api/v1/auth/change-password`)

Allows an authenticated user to change their password.

#### Headers:
```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

#### Request Payload:
```json
{
  "currentPassword": "Password@123",
  "newPassword": "NewSecurePassword@456"
}
```

#### Password Rules:
- Minimum 8 characters, maximum 100 characters.
- Must contain at least 1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special symbol (`@$!%*?&`).
- Cannot be identical to the current password.

---

### 3.5 Logout (`POST /api/v1/auth/logout`)

Revokes the active refresh token.

#### Headers:
```http
Authorization: Bearer <accessToken>
```

#### Response (HTTP 200 OK):
```json
{
  "success": true,
  "code": "LOGOUT_SUCCESS",
  "message": "User logged out successfully"
}
```

---

## 4. TypeScript Definitions & Frontend Store

### 4.1 TypeScript Interfaces (`src/types/auth.ts`)

```typescript
export type PermissionsMatrix = Record<number, number[]>;

export type UserRole =
  | 'PLATFORM_SUPER_ADMIN'
  | 'PLATFORM_ADMIN'
  | 'COMMUNITY_ADMIN'
  | 'COMMUNITY_ROLE_MANAGER'
  | 'COMMUNITY_MANAGER'
  | 'FIELD_AGENT'
  | 'SCHOOL_OWNER'
  | 'SCHOOL_ADMIN'
  | 'PRINCIPAL'
  | 'COORDINATOR'
  | 'TEACHER'
  | 'SUBJECT_TEACHER'
  | 'ACCOUNTS'
  | 'PARENT'
  | 'STUDENT';

export interface UserProfile {
  id: string;
  userCode: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  role: UserRole;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
  schoolId?: string | null;
  tenantCode?: string | null;
  permissionsMatrix: PermissionsMatrix;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface LoginResponseData extends AuthTokens {
  user: UserProfile;
}

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}
```

---

### 4.2 Axios Client with Automatic Token Interceptor (`src/api/client.ts`)

```typescript
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8085';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request Interceptor: Attach Access Token
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('accessToken');
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // Notice: NO X-Tenant-ID header needed! The backend auto-resolves tenant from the token.
  return config;
});

// Response Interceptor: Handle Silent Refresh on 401
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value?: unknown) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: AxiosError | null, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else {
      promise.resolve(token);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('refreshToken');
      if (!refreshToken) {
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      try {
        const { data } = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, {
          refreshToken,
        });

        const newAccessToken = data.data.accessToken;
        const newRefreshToken = data.data.refreshToken;

        localStorage.setItem('accessToken', newAccessToken);
        localStorage.setItem('refreshToken', newRefreshToken);

        apiClient.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`;
        processQueue(null, newAccessToken);

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        }
        return apiClient(originalRequest);
      } catch (refreshErr) {
        processQueue(refreshErr as AxiosError, null);
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
```

---

## 5. Security & Account Lockout Rules

| Scenario | HTTP Status | Response Code | System Action | Frontend Handling |
| :--- | :---: | :--- | :--- | :--- |
| Invalid Credentials | `401` | `INVALID_CREDENTIALS` | Increments `failed_login_attempts` | Display "Invalid email or password" toast |
| Account Locked (5 failed attempts) | `423` | `ACCOUNT_LOCKED` | Locks user for 15 minutes (`locked_until`) | Display "Account locked due to multiple failed attempts. Please retry in 15 minutes." |
| Inactive / Suspended Account | `403` | `ACCOUNT_INACTIVE` | Rejects login | Display "Your account is deactivated. Contact administrator." |
| Expired / Invalid Refresh Token | `401` | `TOKEN_EXPIRED` | Rejects token refresh | Clear storage, redirect to `/login` |
| Token Reuse Detected | `401` | `SECURITY_ALERT` | Revokes ALL user tokens | Force logout, display security warning |
