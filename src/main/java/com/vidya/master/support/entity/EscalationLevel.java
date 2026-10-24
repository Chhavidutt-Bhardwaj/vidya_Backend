package com.vidya.master.support.entity;

/**
 * 3-Tier Escalation Hierarchy:
 * <ol>
 *   <li>{@link #COMMUNITY_RM}: Assigned Community acting as Relationship Manager.</li>
 *   <li>{@link #HUB_MANAGER}: Regional Hub Manager (or Sub-Manager in absence).</li>
 *   <li>{@link #PLATFORM_ADMIN}: Central Vidya Platform Team (Engineering/Product/Gateways).</li>
 * </ol>
 */
public enum EscalationLevel {
    /** Level 1: Local community relationship manager who onboarded or manages the school */
    COMMUNITY_RM,

    /** Level 2: Regional Hub Manager or Sub-Manager coordinating the state */
    HUB_MANAGER,

    /** Level 3: Platform Super Admin / Tech Support / Billing specialists */
    PLATFORM_ADMIN
}
