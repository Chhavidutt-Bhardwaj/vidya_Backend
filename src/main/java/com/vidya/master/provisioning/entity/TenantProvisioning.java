package com.vidya.master.provisioning.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * State machine record for tenant database provisioning.
 *
 * <p>Each provisioning attempt is represented by ONE row.
 * The row tracks current step, last completed step, and failure reason
 * to enable safe retry and resume.
 *
 * <p><strong>Retry semantics:</strong>
 * <ul>
 *   <li>On retry: {@code attempt_count} is incremented, {@code status} reset to REQUESTED,
 *       and {@code last_completed_step} preserved so completed steps are skipped.</li>
 *   <li>On re-provisioning from scratch: a new row is created instead.</li>
 * </ul>
 *
 * <p><strong>Important:</strong> There is no {@code @Version} field here because
 * the provisioning state machine explicitly controls updates and retry.
 * Optimistic locking would interfere with the state machine's single-writer guarantee.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenant_provisioning",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tenant_provisioning_code",
                columnNames = "provisioning_code"))
public class TenantProvisioning {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provisioning_code", nullable = false, length = 50)
    private String provisioningCode;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "REQUESTED";

    @Column(name = "current_step", length = 50)
    private String currentStep;

    @Column(name = "last_completed_step", length = 50)
    private String lastCompletedStep;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static TenantProvisioning create(UUID tenantId, String provisioningCode) {
        TenantProvisioning p = new TenantProvisioning();
        p.tenantId         = tenantId;
        p.provisioningCode = provisioningCode;
        p.status           = "REQUESTED";
        p.currentStep      = "REQUESTED";
        p.attemptCount     = 1;
        p.startedAt        = Instant.now();
        return p;
    }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    public void advanceTo(String step) {
        this.currentStep = step;
        this.status      = step;
        this.updatedAt   = Instant.now();
    }

    public void completeStep(String step) {
        this.lastCompletedStep = step;
        this.updatedAt         = Instant.now();
    }

    public void markCompleted() {
        this.status      = "COMPLETED";
        this.currentStep = "COMPLETED";
        this.completedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void markFailed(String reason) {
        this.status        = "FAILED";
        this.failureReason = reason;
        this.updatedAt     = Instant.now();
    }

    public void prepareForRetry() {
        this.status        = "REQUESTED";
        this.currentStep   = "REQUESTED";
        this.failureReason = null;
        this.attemptCount  = this.attemptCount + 1;
        this.startedAt     = Instant.now();
        this.updatedAt     = Instant.now();
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(this.status);
    }

    public boolean isFailed() {
        return "FAILED".equals(this.status);
    }
}
