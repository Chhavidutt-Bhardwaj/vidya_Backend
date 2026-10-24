# 14. Hub Hierarchy, School RM & 3-Tier Escalation Workflow

This technical guide provides frontend engineers with specifications and integration patterns for:
1. **Regional Hub Hierarchy**: Regional grouping (`Platform Users` &rarr; `Hub Manager / Sub-Manager` &rarr; `Community` &rarr; `Schools`).
2. **School Relationship Management (RM)**: Decoupled onboarding attribution vs. servicing relationship manager.
3. **Workload Balancing & Capacity Limits**: Per-community query and school limits configured by Hub Managers.
4. **3-Tier Query Escalation Pipeline**: Level 1 (Community RM) &rarr; Level 2 (Hub Manager / Deputy) &rarr; Level 3 (Platform Admin).
5. **Decoupled Dual Commission Accounting**: Onboarding Bounty (Community A) vs. Query Resolution Commission (Community B).

---

## 1. Architectural Chain of Command

Vidya ERP structures regional operations into hubs (e.g., Delhi Hub, Maharashtra Hub) grouping all state communities.

```mermaid
graph TD
    PU[Platform Users / Super Admin / Tech Ops]
    HM[Hub Manager / Sub-Manager (Lead Community)]
    COM_A[Community A (Onboarding Agent)]
    COM_B[Community B (Servicing Relationship Manager)]
    SCH[School XYZ]

    PU -->|Level 3 Escalations & System Governance| HM
    HM -->|Level 2 Escalations, Workload Limits & RM Assignment| COM_B
    COM_A -->|Brought / Onboarded School| SCH
    COM_B -->|Level 1 Service Desk & Relationship Management| SCH

    subgraph Dual Commission Ledger
        COM_A -.->|Onboarding Bounty (e.g. ₹5,000)| LEDGER[(Commission Ledger)]
        COM_B -.->|Query Resolution Commission (e.g. ₹250/ticket)| LEDGER
    end
```

### Core Hierarchy Principles:
1. **Hub Hierarchy**: A Hub binds all communities within a state or geographic cluster. Each Hub designates:
   - **Lead Community**: The primary representative community for the region.
   - **Hub Manager**: Primary operational head for all communities in the hub.
   - **Hub Sub-Manager (Deputy)**: Has identical operational rights and acts in the manager's absence.
2. **Decoupled Attribution**:
   - `onboarding_community_id` (Community A): Permanently tracks who brought the school. Receives the **Onboarding Bounty**.
   - `assigned_community_id` (Community B): The active Relationship Manager. Receives the **Query Resolution Commission**.
3. **Proximity & Workload-Aware Auto-Assignment**:
   - When a school joins directly (without referral), the system auto-assigns the nearest community in the state/hub.
   - The Hub Manager configures workload limits (e.g., `maxActiveQueryLimit = 10`). Communities at capacity are automatically bypassed to balance load.
4. **3-Tier Support Escalation**:
   - Level 1: `COMMUNITY_RM` (Assigned Community).
   - Level 2: `HUB_MANAGER` (Regional Lead).
   - Level 3: `PLATFORM_ADMIN` (Central Platform Engineering/Finance).
   - Strict step-by-step audit trail (`COMMUNITY_RM` &rarr; `HUB_MANAGER` &rarr; `PLATFORM_ADMIN`). Direct jumps are blocked.

---

## 2. API Endpoints Overview

All endpoints operate directly on `vidya_master_db`. Frontend requests require only `Authorization: Bearer <token>` (**no `X-Tenant-ID` header required**).

### A. Hub Management (`/api/v1/hubs`)
| Method | Endpoint | Description | Guard / Role |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/hubs` | Create a regional hub | `PLATFORM_SUPER_ADMIN` |
| `GET` | `/api/v1/hubs` | Paginated search of hubs | Platform & Hub Managers |
| `GET` | `/api/v1/hubs/{id}` | Get hub details by ID | Platform & Hub Managers |
| `PUT` | `/api/v1/hubs/{id}` | Update hub basic details | `PLATFORM_SUPER_ADMIN`, Hub Manager |
| `PUT` | `/api/v1/hubs/{id}/managers` | Assign/update Hub Manager & Deputy | `PLATFORM_SUPER_ADMIN` |
| `POST` | `/api/v1/hubs/{id}/communities/{communityId}` | Attach a community to hub | `PLATFORM_SUPER_ADMIN`, Hub Manager |
| `DELETE` | `/api/v1/hubs/{id}/communities/{communityId}` | Remove community from hub | `PLATFORM_SUPER_ADMIN` |

### B. School Relationship & Workload Balancing
| Method | Endpoint | Description | Guard / Role |
| :--- | :--- | :--- | :--- |
| `PUT` | `/api/v1/schools/{schoolId}/relationship/assign` | Manually assign/reassign RM Community | Hub Manager, Platform Admin |
| `POST` | `/api/v1/schools/{schoolId}/relationship/auto-assign` | Auto-assign nearest community based on state & workload | System / Hub Manager |
| `GET` | `/api/v1/schools/{schoolId}/relationship` | Get school RM status, onboarding community & hub | Authenticated User |
| `GET` | `/api/v1/communities/{communityId}/assigned-schools` | List schools managed by community | Community Members, Hub Manager |
| `PUT` | `/api/v1/communities/{communityId}/workload-limits` | Set max query & school balance limits | Hub Manager, Platform Admin |

### C. 3-Tier Support Query & Escalation (`/api/v1/support/queries`)
| Method | Endpoint | Description | Guard / Role |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/support/queries` | School raises a support query (starts at Level 1) | School Principal/Admin |
| `GET` | `/api/v1/support/queries` | Search queries filtered by status, level, community, school | All Stakeholders |
| `GET` | `/api/v1/support/queries/{id}` | Get ticket details with escalation history & comments | All Stakeholders |
| `POST` | `/api/v1/support/queries/{id}/escalate` | Escalate query to next tier (`L1` &rarr; `L2` or `L2` &rarr; `L3`) | Community RM, Hub Manager |
| `POST` | `/api/v1/support/queries/{id}/resolve` | Resolve query & credit resolution commission to RM | Handling Tier Assignee |
| `POST` | `/api/v1/support/queries/{id}/comments` | Add internal/public threaded discussion comment | Handling Tier Assignee |

### D. Dual Commission Ledger (`/api/v1/communities/{id}/commission-ledger`)
| Method | Endpoint | Description | Guard / Role |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/communities/{id}/commission-ledger` | View paginated commission history (onboarding vs resolution) | Community Admin, Platform Finance |

---

## 3. Detailed Request & Response Specifications

### 3.1. Hub Creation & Manager Assignment

#### Create Hub: `POST /api/v1/hubs`
```json
{
  "hubCode": "HUB-DEL-01",
  "name": "Delhi Central & NCR Hub",
  "state": "Delhi",
  "region": "National Capital Region",
  "leadCommunityId": "71a2e3f4-5678-90ab-cdef-1234567890ab",
  "hubManagerMemberId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "hubSubManagerMemberId": "8ba85f64-5717-4562-b3fc-2c963f66afb7"
}
```

#### Assign Hub Manager & Deputy: `PUT /api/v1/hubs/{id}/managers`
```json
{
  "hubManagerMemberId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "hubSubManagerMemberId": "8ba85f64-5717-4562-b3fc-2c963f66afb7",
  "leadCommunityId": "71a2e3f4-5678-90ab-cdef-1234567890ab"
}
```

---

### 3.2. School RM Assignment & Workload Balance

#### Set Community Workload Limits: `PUT /api/v1/communities/{communityId}/workload-limits`
Hub Managers can throttle community assignment to prevent burn-out:
```json
{
  "maxActiveQueryLimit": 10,
  "maxAssignedSchoolsLimit": 25
}
```

#### Assign School RM: `PUT /api/v1/schools/{schoolId}/relationship/assign`
Allows platform users or hub managers to assign Community B as Relationship Manager:
```json
{
  "communityId": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
  "hubId": "d0e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a",
  "assignedRmMemberId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "notes": "Assigned based on proximity to South Delhi campus."
}
```

#### Auto-Assign Nearest Community: `POST /api/v1/schools/{schoolId}/relationship/auto-assign`
- Matches the school's state against communities with active addresses in that state.
- **Filters out communities that have reached their `maxActiveQueryLimit`** (e.g. &ge; 10 active tickets).
- Selects the least loaded community.

#### School Relationship Response: `GET /api/v1/schools/{schoolId}/relationship`
```json
{
  "code": "SUCCESS",
  "message": "School relationship fetched successfully",
  "data": {
    "schoolId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "schoolCode": "SCH-1002",
    "schoolName": "Modern Public School",
    "onboardingCommunityId": "71a2e3f4-5678-90ab-cdef-1234567890ab",
    "onboardingCommunityName": "Delhi Education Foundation",
    "onboardingCommunityCode": "COM-1001",
    "onboardedAt": "2026-08-15T10:30:00",
    "assignedCommunityId": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
    "assignedCommunityName": "South Delhi Social Society",
    "assignedCommunityCode": "COM-1002",
    "hubId": "d0e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a",
    "hubName": "Delhi Central & NCR Hub",
    "rmAssignmentType": "DIRECT_MANUAL",
    "rmAssignedAt": "2026-10-24T18:00:00"
  }
}
```

---

### 3.3. 3-Tier Support Query Lifecycle & Escalation

#### Step 1: School Raises Query: `POST /api/v1/support/queries`
```json
{
  "schoolId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "category": "PAYMENT_BILLING",
  "priority": "HIGH",
  "title": "Payment gateway fee mismatch in term 2 fee collection",
  "description": "Parents report 2.5% surcharge instead of configured 1.2% in school payment gateway."
}
```
*Result*: Ticket starts at `currentLevel: "COMMUNITY_RM"` (Level 1) assigned automatically to Community B.

#### Step 2: Escalate Ticket: `POST /api/v1/support/queries/{id}/escalate`
If Community RM cannot resolve within their authority (e.g. software bug or gateway reconciliation required):
```json
{
  "escalateTo": "HUB_MANAGER",
  "reason": "Gateway integration credentials require regional merchant re-verification by Hub Lead.",
  "targetAssigneeMemberId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```
*If Hub Manager also cannot resolve*:
```json
{
  "escalateTo": "PLATFORM_ADMIN",
  "reason": "Payment webhook timeout bug identified in core platform. Requires engineering patch."
}
```

#### Step 3: Resolve Ticket: `POST /api/v1/support/queries/{id}/resolve`
```json
{
  "resolutionNotes": "Gateway webhook timeout patched and verified. ₹12,500 difference credited to school ledger."
}
```
*Result*:
- Status transitions to `RESOLVED`.
- **System automatically calculates & logs ₹250 `QUERY_RESOLUTION` commission** to Community B's ledger.

---

### 3.4. Dual Commission Ledger: `GET /api/v1/communities/{id}/commission-ledger`
Returns complete ledger history separating Onboarding Bounties vs. Query Resolution Commissions:

```json
{
  "code": "SUCCESS",
  "message": "Commission ledger retrieved successfully",
  "data": [
    {
      "id": "f1a2b3c4-d5e6-7a8b-9c0d-1e2f3a4b5c6d",
      "communityId": "71a2e3f4-5678-90ab-cdef-1234567890ab",
      "commissionType": "ONBOARDING",
      "amount": 5000.00,
      "schoolId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "schoolName": "Modern Public School",
      "status": "APPROVED",
      "description": "Onboarding referral bonus for school SCH-1002",
      "createdAt": "2026-08-15T11:00:00"
    },
    {
      "id": "e2b3c4d5-e6f7-8a9b-0c1d-2e3f4a5b6c7d",
      "communityId": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
      "commissionType": "QUERY_RESOLUTION",
      "amount": 250.00,
      "queryId": "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f",
      "schoolId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "schoolName": "Modern Public School",
      "status": "PENDING",
      "description": "Resolution commission for query TKT-20261024-0001",
      "createdAt": "2026-10-24T21:30:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

---

## 4. TypeScript Frontend Interfaces

```typescript
export type EscalationLevel = 'COMMUNITY_RM' | 'HUB_MANAGER' | 'PLATFORM_ADMIN';
export type QueryCategory = 'SOFTWARE_ISSUE' | 'PAYMENT_BILLING' | 'ACADEMIC_SETUP' | 'HARDWARE_INFRA' | 'GENERAL_INQUIRY';
export type QueryPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type QueryStatus = 'OPEN' | 'IN_PROGRESS' | 'ESCALATED' | 'RESOLVED' | 'CLOSED';
export type CommissionType = 'ONBOARDING' | 'QUERY_RESOLUTION' | 'RELATIONSHIP_MANAGEMENT';

export interface HubResponse {
  id: string;
  hubCode: string;
  name: string;
  state: string;
  region?: string;
  leadCommunityId?: string;
  leadCommunityName?: string;
  hubManagerMemberId?: string;
  hubManagerName?: string;
  hubSubManagerMemberId?: string;
  hubSubManagerName?: string;
  status: 'ACTIVE' | 'INACTIVE';
  totalCommunities: number;
}

export interface SchoolRelationshipResponse {
  schoolId: string;
  schoolCode: string;
  schoolName: string;
  onboardingCommunityId?: string;
  onboardingCommunityName?: string;
  onboardingCommunityCode?: string;
  onboardedAt?: string;
  assignedCommunityId?: string;
  assignedCommunityName?: string;
  assignedCommunityCode?: string;
  hubId?: string;
  hubName?: string;
  rmAssignmentType?: 'REFERRAL_ONBOARDED' | 'DIRECT_AUTO' | 'DIRECT_MANUAL';
  rmAssignedAt?: string;
}

export interface SupportQueryDetailResponse {
  id: string;
  ticketNumber: string;
  schoolId: string;
  schoolName: string;
  schoolCode: string;
  category: QueryCategory;
  priority: QueryPriority;
  status: QueryStatus;
  currentLevel: EscalationLevel;
  assignedCommunityId?: string;
  assignedCommunityName?: string;
  assignedHubId?: string;
  title: string;
  description: string;
  resolutionNotes?: string;
  resolvedAt?: string;
  escalationLogs: Array<{
    id: string;
    fromLevel: EscalationLevel;
    toLevel: EscalationLevel;
    reason: string;
    escalatedByUserId: string;
    escalatedAt: string;
  }>;
  comments: Array<{
    id: string;
    authorUserId: string;
    authorRole: string;
    comment: string;
    isInternal: boolean;
    createdAt: string;
  }>;
}

export interface CommissionLedgerEntry {
  id: string;
  communityId: string;
  commissionType: CommissionType;
  amount: number;
  schoolId?: string;
  schoolName?: string;
  queryId?: string;
  status: 'PENDING' | 'APPROVED' | 'DISBURSED' | 'REJECTED';
  description?: string;
  createdAt: string;
}
```

---

## 5. UI Integration Workflows

### 1. Hub Operations Dashboard
- Display Hub Cards (e.g. **Delhi Central Hub**, **Maharashtra West Hub**).
- Show statistics: `Total Communities`, `Active Schools`, `Open Support Queries`, `Hub Manager & Deputy`.
- Quick-action buttons to reassign Deputy or adjust community capacity limits.

### 2. School Relationship Card (In School Profile / CRM)
- Header with two distinct badge tags:
  - **Onboarded By**: `Community A (Delhi Education Foundation)`
  - **Servicing RM**: `Community B (South Delhi Social Society)`
- **Change RM Modal**: Allows Hub Manager to transfer RM servicing to another community while preserving the immutable Onboarding attribution.

### 3. Support Query Escalation Action
- In the Query Detail page, when `currentLevel === 'COMMUNITY_RM'`, display an **"Escalate to Hub Lead"** button.
- When `currentLevel === 'HUB_MANAGER'`, display an **"Escalate to Platform Engineering"** button.
- Disable direct jump from `COMMUNITY_RM` &rarr; `PLATFORM_ADMIN` with tooltip: *"Queries must be reviewed by the Hub Manager before central engineering escalation."*

### 4. Community Earnings & Commission Tab
- Card 1: **Onboarding Bounties Earned** (Total ₹ from schools onboarded by this community).
- Card 2: **Query Resolution Bounties Earned** (Total ₹ from tickets resolved as Relationship Manager).
- Table displaying transaction rows with filter by type (`ONBOARDING` vs `QUERY_RESOLUTION`).
