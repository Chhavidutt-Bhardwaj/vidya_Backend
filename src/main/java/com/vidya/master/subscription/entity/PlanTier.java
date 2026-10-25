package com.vidya.master.subscription.entity;

/**
 * Standard tier classification for subscription plans on Vidya ERP.
 * Supports packaged tiers (FREE, STARTER, PRO, MAX, TIER_1/2/3) and bespoke CUSTOM plans.
 */
public enum PlanTier {
    FREE,
    STARTER,
    PRO,
    MAX,
    TIER_1,
    TIER_2,
    TIER_3,
    CUSTOM
}
