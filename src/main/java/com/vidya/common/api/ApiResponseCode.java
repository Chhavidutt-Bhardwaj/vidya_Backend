package com.vidya.common.api;

/**
 * Application-level response codes.
 *
 * <p>These are semantic codes returned in the {@code code} field of
 * {@link ApiResponse}. They are NOT HTTP status codes.
 *
 * <p>Naming convention: {@code DOMAIN_VERB} or {@code DOMAIN_STATE}.
 * Example: {@code SCHOOL_CREATED}, {@code TENANT_PROVISIONING_FAILED}.
 */
public final class ApiResponseCode {

    private ApiResponseCode() {}

    // ── Generic ─────────────────────────────────────────────────────────────
    public static final String SUCCESS                       = "SUCCESS";
    public static final String VALIDATION_ERROR             = "VALIDATION_ERROR";
    public static final String RESOURCE_NOT_FOUND           = "RESOURCE_NOT_FOUND";
    public static final String DUPLICATE_RESOURCE           = "DUPLICATE_RESOURCE";
    public static final String UNAUTHORIZED                  = "UNAUTHORIZED";
    public static final String FORBIDDEN                     = "FORBIDDEN";
    public static final String INTERNAL_ERROR               = "INTERNAL_ERROR";
    public static final String BUSINESS_ERROR               = "BUSINESS_ERROR";
    public static final String INVALID_STATE_TRANSITION     = "INVALID_STATE_TRANSITION";
    public static final String INVALID_ARGUMENT              = "INVALID_ARGUMENT";

    // ── Authentication & User ────────────────────────────────────────────────
    public static final String LOGIN_SUCCESS                = "LOGIN_SUCCESS";
    public static final String LOGOUT_SUCCESS               = "LOGOUT_SUCCESS";
    public static final String TOKEN_REFRESHED              = "TOKEN_REFRESHED";
    public static final String INVALID_CREDENTIALS          = "INVALID_CREDENTIALS";
    public static final String TOKEN_EXPIRED                = "TOKEN_EXPIRED";
    public static final String TOKEN_INVALID                = "TOKEN_INVALID";
    public static final String USER_CREATED                 = "USER_CREATED";
    public static final String USER_FOUND                   = "USER_FOUND";

    // ── Tenant ───────────────────────────────────────────────────────────────
    public static final String TENANT_NOT_FOUND             = "TENANT_NOT_FOUND";
    public static final String TENANT_ACCESS_DENIED         = "TENANT_ACCESS_DENIED";
    public static final String TENANT_SUSPENDED             = "TENANT_SUSPENDED";
    public static final String TENANT_PROVISIONING_STARTED  = "TENANT_PROVISIONING_STARTED";
    public static final String TENANT_PROVISIONING_COMPLETED = "TENANT_PROVISIONING_COMPLETED";
    public static final String TENANT_PROVISIONING_FAILED   = "TENANT_PROVISIONING_FAILED";
    public static final String TENANT_PROVISIONING_RETRIED  = "TENANT_PROVISIONING_RETRIED";
    public static final String TENANT_ACTIVATED             = "TENANT_ACTIVATED";
    public static final String TENANT_SUSPENDED_OK          = "TENANT_SUSPENDED_OK";

    // ── Community ────────────────────────────────────────────────────────────
    public static final String COMMUNITY_CREATED            = "COMMUNITY_CREATED";
    public static final String COMMUNITY_UPDATED            = "COMMUNITY_UPDATED";
    public static final String COMMUNITY_FOUND              = "COMMUNITY_FOUND";
    public static final String COMMUNITIES_FOUND            = "COMMUNITIES_FOUND";
    public static final String COMMUNITY_STATUS_UPDATED     = "COMMUNITY_STATUS_UPDATED";
    public static final String COMMUNITY_MEMBER_ADDED       = "COMMUNITY_MEMBER_ADDED";
    public static final String COMMUNITY_MEMBER_FOUND       = "COMMUNITY_MEMBER_FOUND";
    public static final String COMMUNITY_MEMBERS_FOUND      = "COMMUNITY_MEMBERS_FOUND";
    public static final String COMMUNITY_MEMBER_REMOVED     = "COMMUNITY_MEMBER_REMOVED";

    // ── Community Section Details ─────────────────────────────────────────────
    public static final String COMMUNITY_SECTION_UPDATED    = "COMMUNITY_SECTION_UPDATED";
    public static final String COMMUNITY_SECTION_FOUND      = "COMMUNITY_SECTION_FOUND";
    public static final String COMMUNITY_BANK_DETAIL_VERIFIED = "COMMUNITY_BANK_DETAIL_VERIFIED";

    // ── Community Role Management ─────────────────────────────────────────────
    public static final String COMMUNITY_ROLE_CREATED       = "COMMUNITY_ROLE_CREATED";
    public static final String COMMUNITY_ROLE_UPDATED       = "COMMUNITY_ROLE_UPDATED";
    public static final String COMMUNITY_ROLE_DELETED       = "COMMUNITY_ROLE_DELETED";
    public static final String COMMUNITY_ROLE_FOUND         = "COMMUNITY_ROLE_FOUND";
    public static final String COMMUNITY_ROLES_FOUND        = "COMMUNITY_ROLES_FOUND";
    public static final String COMMUNITY_ROLE_ASSIGNED      = "COMMUNITY_ROLE_ASSIGNED";
    public static final String COMMUNITY_PERMISSIONS_FOUND  = "COMMUNITY_PERMISSIONS_FOUND";
    public static final String COMMUNITY_PERMISSIONS_UPDATED = "COMMUNITY_PERMISSIONS_UPDATED";
    public static final String COMMUNITY_ROLE_AUDIT_FOUND   = "COMMUNITY_ROLE_AUDIT_FOUND";

    // ── Community Login History ───────────────────────────────────────────────
    public static final String COMMUNITY_LOGIN_HISTORY_FOUND = "COMMUNITY_LOGIN_HISTORY_FOUND";

    // ── Community Payroll ─────────────────────────────────────────────────────
    public static final String COMMUNITY_PAYROLL_FOUND      = "COMMUNITY_PAYROLL_FOUND";
    public static final String COMMUNITY_PAYROLL_UPDATED    = "COMMUNITY_PAYROLL_UPDATED";

    // ── Community Commission ──────────────────────────────────────────────────
    public static final String COMMUNITY_COMMISSION_FOUND   = "COMMUNITY_COMMISSION_FOUND";
    public static final String COMMUNITY_COMMISSION_CREATED = "COMMUNITY_COMMISSION_CREATED";
    public static final String COMMUNITY_COMMISSION_UPDATED = "COMMUNITY_COMMISSION_UPDATED";

    // ── School ───────────────────────────────────────────────────────────────
    public static final String SCHOOL_FOUND                 = "SCHOOL_FOUND";
    public static final String SCHOOLS_FOUND               = "SCHOOLS_FOUND";
    public static final String SCHOOL_ACTIVATED            = "SCHOOL_ACTIVATED";
    public static final String SCHOOL_SUSPENDED            = "SCHOOL_SUSPENDED";

    // ── Onboarding ───────────────────────────────────────────────────────────
    public static final String ONBOARDING_CREATED          = "ONBOARDING_CREATED";
    public static final String ONBOARDING_FOUND            = "ONBOARDING_FOUND";
    public static final String ONBOARDING_STEP_SAVED       = "ONBOARDING_STEP_SAVED";
    public static final String ONBOARDING_SUBMITTED        = "ONBOARDING_SUBMITTED";
    public static final String ONBOARDING_VERIFIED         = "ONBOARDING_VERIFIED";
    public static final String ONBOARDING_APPROVED         = "ONBOARDING_APPROVED";
    public static final String ONBOARDING_REJECTED         = "ONBOARDING_REJECTED";
    public static final String ONBOARDING_PLAN_SELECTED    = "ONBOARDING_PLAN_SELECTED";

    // ── Subscription ─────────────────────────────────────────────────────────
    public static final String PLAN_CREATED                = "PLAN_CREATED";
    public static final String PLAN_UPDATED                = "PLAN_UPDATED";
    public static final String PLAN_FOUND                  = "PLAN_FOUND";
    public static final String PLANS_FOUND                 = "PLANS_FOUND";
    public static final String FEATURE_CREATED             = "FEATURE_CREATED";
    public static final String FEATURES_FOUND              = "FEATURES_FOUND";
    public static final String SUBSCRIPTION_CREATED        = "SUBSCRIPTION_CREATED";
    public static final String SUBSCRIPTION_FOUND          = "SUBSCRIPTION_FOUND";

    // ── Payment ──────────────────────────────────────────────────────────────
    public static final String PAYMENT_INITIATED           = "PAYMENT_INITIATED";
    public static final String PAYMENT_VERIFIED            = "PAYMENT_VERIFIED";
    public static final String PAYMENT_WEBHOOK_RECEIVED    = "PAYMENT_WEBHOOK_RECEIVED";
    public static final String PAYMENT_FAILED              = "PAYMENT_FAILED";

    // ── Hub & Regional Hierarchy ─────────────────────────────────────────────
    public static final String HUB_CREATED                 = "HUB_CREATED";
    public static final String HUB_UPDATED                 = "HUB_UPDATED";
    public static final String HUB_FOUND                   = "HUB_FOUND";
    public static final String HUBS_FOUND                  = "HUBS_FOUND";
    public static final String HUB_MANAGER_ASSIGNED        = "HUB_MANAGER_ASSIGNED";
    public static final String HUB_COMMUNITY_ASSIGNED      = "HUB_COMMUNITY_ASSIGNED";
    public static final String HUB_COMMUNITY_REMOVED       = "HUB_COMMUNITY_REMOVED";

    // ── School Relationship Management ───────────────────────────────────────
    public static final String SCHOOL_RM_ASSIGNED          = "SCHOOL_RM_ASSIGNED";
    public static final String SCHOOL_RM_AUTO_ASSIGNED     = "SCHOOL_RM_AUTO_ASSIGNED";
    public static final String SCHOOL_RM_FOUND             = "SCHOOL_RM_FOUND";
    public static final String SCHOOL_RM_LIST_FOUND        = "SCHOOL_RM_LIST_FOUND";

    // ── School Support Query & Escalation ────────────────────────────────────
    public static final String QUERY_CREATED               = "QUERY_CREATED";
    public static final String QUERY_ESCALATED             = "QUERY_ESCALATED";
    public static final String QUERY_RESOLVED              = "QUERY_RESOLVED";
    public static final String QUERY_COMMENT_ADDED         = "QUERY_COMMENT_ADDED";
    public static final String QUERY_FOUND                 = "QUERY_FOUND";
    public static final String QUERIES_FOUND               = "QUERIES_FOUND";
}
