package com.vidya.tenant.provisioning;

import lombok.Getter;

/**
 * Ordered steps of the tenant provisioning state machine.
 *
 * <p>Each step has a numeric order allowing the machine to determine
 * which steps are complete and which must still run on a retry.
 *
 * <p><strong>Idempotency contract:</strong> A step is never re-executed
 * if it has already completed. The {@code TenantProvisioningService} reads
 * {@code last_completed_step} from the {@code tenant_provisioning} table
 * and skips all steps with {@code order ≤ lastCompletedOrder}.
 */
@Getter
public enum ProvisioningStep {

    /** Initial state — provisioning has been requested. */
    REQUESTED(0),

    /** Creating the PostgreSQL database for this tenant. */
    DATABASE_CREATING(1),

    /** Database creation complete; now running Flyway migrations. */
    MIGRATION_RUNNING(2),

    /** Flyway migrations completed; initializing seed data. */
    INITIALIZATION_RUNNING(3),

    /** All steps complete; tenant is ready for activation. */
    COMPLETED(4),

    /** A step failed; safe to retry from last_completed_step. */
    FAILED(-1);

    private final int order;

    ProvisioningStep(int order) {
        this.order = order;
    }

    /** Returns true if this step comes after the given step in the sequence. */
    public boolean isAfter(ProvisioningStep other) {
        return this.order > other.order;
    }

    /** Returns true if this step has been completed relative to the given last step. */
    public boolean isCompletedGiven(ProvisioningStep lastCompleted) {
        if (lastCompleted == null) return false;
        return this.order <= lastCompleted.order;
    }
}
