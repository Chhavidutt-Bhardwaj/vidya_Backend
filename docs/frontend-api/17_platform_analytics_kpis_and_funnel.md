# Module 17: Platform Analytics, Executive KPIs & Revenue Trends

## 1. Overview & Capabilities

The **Platform Analytics Engine** powers executive summaries, performance KPI widgets, revenue trends, and school onboarding conversion funnels for SuperUsers and Platform Administrators:

- **Executive KPIs (`/api/v1/analytics/kpis`)**: High-level institutional metrics including total/active schools, total communities, platform users, active subscriptions, Monthly Recurring Revenue (MRR), Annual Recurring Revenue (ARR), total settled payments, and open/escalated support tickets.
- **Revenue Trends (`/api/v1/analytics/revenue-trends`)**: Month-by-month time-series metrics tracking gross settled revenue, transaction volume, and new subscription acquisitions.
- **Onboarding Conversion Funnel (`/api/v1/analytics/onboarding-funnel`)**: Funnel analysis from inbound submissions (`SUBMITTED`, `VERIFIED`, `APPROVED`, `PROVISIONED`, `ACTIVE`, `REJECTED`) with conversion percentages and acquisition channel breakdowns (`WEBSITE`, `COMMUNITY`, `REFERRAL`, `DIRECT`).

---

## 2. Zero-Header & Master DB Architecture

- **Scope**: Analytics operate directly on `vidya_master_db`.
- **Zero-Header Contract**: No `X-Tenant-ID` header required. Simply pass your Bearer token in the `Authorization` header.

```mermaid
graph TD
    UI[Platform Admin Web Dashboard] -->|Bearer Token only| AnalyticsCtrl[/api/v1/analytics/*]
    AnalyticsCtrl --> AnalyticsSvc[AnalyticsService]
    
    subgraph Master Database
        AnalyticsSvc --> SchoolRepo[SchoolRepository]
        AnalyticsSvc --> SubRepo[SchoolSubscriptionRepository]
        AnalyticsSvc --> PayRepo[PaymentTransactionRepository]
        AnalyticsSvc --> OnbRepo[SchoolOnboardingRepository]
        AnalyticsSvc --> SupportRepo[SchoolSupportQueryRepository]
        AnalyticsSvc --> UserRepo[PlatformUserRepository]
    end
```

---

## 3. REST API Endpoint Reference

### 3.1 Executive KPIs
`GET /api/v1/analytics/kpis`

- **Security**: Authenticated (`PLATFORM_SUPER_ADMIN`, `PLATFORM_ADMIN`, `HUB_MANAGER`)
- **Response (`200 OK`)**:
```json
{
  "status": 200,
  "success": true,
  "code": "KPIS_FOUND",
  "message": "Platform executive KPIs retrieved successfully",
  "data": {
    "totalSchools": 48,
    "activeSchools": 39,
    "draftSchools": 9,
    "pendingOnboardings": 6,
    "totalCommunities": 12,
    "activeCommunities": 11,
    "totalUsers": 184,
    "activeSubscriptions": 39,
    "monthlyRecurringRevenue": 248500.00,
    "annualRecurringRevenue": 2982000.00,
    "totalSettledRevenue": 3450000.00,
    "totalTransactions": 142,
    "openSupportQueries": 8,
    "escalatedSupportQueries": 2,
    "resolvedSupportQueries": 94
  }
}
```

---

### 3.2 Revenue Trends Time-Series
`GET /api/v1/analytics/revenue-trends?months=6`

- **Query Parameters**:
  - `months` (optional, integer, default: `12`, max: `36`): Number of past months to retrieve.
- **Response (`200 OK`)**:
```json
{
  "status": 200,
  "success": true,
  "code": "REVENUE_TRENDS_FOUND",
  "message": "Revenue trends retrieved successfully",
  "data": [
    {
      "month": "2026-05",
      "monthName": "May 2026",
      "revenue": 185000.00,
      "transactionCount": 14,
      "newSubscriptions": 4
    },
    {
      "month": "2026-06",
      "monthName": "Jun 2026",
      "revenue": 210000.00,
      "transactionCount": 18,
      "newSubscriptions": 6
    },
    {
      "month": "2026-07",
      "monthName": "Jul 2026",
      "revenue": 275000.00,
      "transactionCount": 22,
      "newSubscriptions": 8
    },
    {
      "month": "2026-08",
      "monthName": "Aug 2026",
      "revenue": 310000.00,
      "transactionCount": 26,
      "newSubscriptions": 7
    },
    {
      "month": "2026-09",
      "monthName": "Sep 2026",
      "revenue": 340000.00,
      "transactionCount": 31,
      "newSubscriptions": 9
    },
    {
      "month": "2026-10",
      "monthName": "Oct 2026",
      "revenue": 395000.00,
      "transactionCount": 35,
      "newSubscriptions": 11
    }
  ]
}
```

---

### 3.3 Onboarding Funnel Analysis
`GET /api/v1/analytics/onboarding-funnel`

- **Response (`200 OK`)**:
```json
{
  "status": 200,
  "success": true,
  "code": "ONBOARDING_FUNNEL_FOUND",
  "message": "School onboarding funnel metrics retrieved successfully",
  "data": {
    "totalApplications": 60,
    "activeSchools": 39,
    "conversionRate": 65.0,
    "stageCounts": {
      "DRAFT": 2,
      "SUBMITTED": 5,
      "VERIFICATION_PENDING": 3,
      "VERIFIED": 2,
      "PLAN_SELECTED": 1,
      "PAYMENT_PENDING": 2,
      "PAYMENT_COMPLETED": 1,
      "APPROVAL_PENDING": 1,
      "APPROVED": 2,
      "PROVISIONING": 0,
      "PROVISIONED": 2,
      "ACTIVE": 37,
      "REJECTED": 2,
      "CANCELLED": 0,
      "FAILED": 0,
      "PROVISIONING_FAILED": 0
    },
    "channelDistribution": {
      "COMMUNITY": 34,
      "WEBSITE": 16,
      "REFERRAL": 8,
      "DIRECT": 2
    }
  }
}
```

---

## 4. TypeScript Client Interfaces

```typescript
export interface PlatformKpiData {
  totalSchools: number;
  activeSchools: number;
  draftSchools: number;
  pendingOnboardings: number;
  totalCommunities: number;
  activeCommunities: number;
  totalUsers: number;
  activeSubscriptions: number;
  monthlyRecurringRevenue: number;
  annualRecurringRevenue: number;
  totalSettledRevenue: number;
  totalTransactions: number;
  openSupportQueries: number;
  escalatedSupportQueries: number;
  resolvedSupportQueries: number;
}

export interface RevenueTrendItem {
  month: string;      // "2026-10"
  monthName: string;  // "Oct 2026"
  revenue: number;
  transactionCount: number;
  newSubscriptions: number;
}

export interface OnboardingFunnelData {
  totalApplications: number;
  activeSchools: number;
  conversionRate: number;
  stageCounts: Record<string, number>;
  channelDistribution: Record<string, number>;
}
```
