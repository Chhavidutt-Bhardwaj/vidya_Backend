package com.vidya.tenant.provisioning;

import com.vidya.common.exception.TenantProvisioningException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.provisioning.entity.TenantProvisioning;
import com.vidya.master.provisioning.repository.TenantProvisioningRepository;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.tenant.datasource.CredentialResolver;
import com.vidya.tenant.datasource.TenantDataSourceFactory;
import com.vidya.tenant.datasource.TenantDataSourceManager;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Orchestrates the full tenant provisioning workflow.
 *
 * <p><strong>This service is the single entry point for provisioning.</strong>
 * All provisioning flows (initial, retry) go through here.
 *
 * <p><strong>Important — NOT one ACID transaction:</strong>
 * Master DB writes, PostgreSQL DDL, and Flyway migrations cannot be wrapped
 * in a single transaction. Instead, each step is committed independently,
 * and the {@link TenantProvisioning} state machine row records progress.
 * A failed step leaves an accurate audit trail and allows safe retry.
 *
 * <p><strong>Retry semantics:</strong>
 * <ul>
 *   <li>Each retry increments {@code attempt_count}</li>
 *   <li>The {@code last_completed_step} is preserved from the previous attempt</li>
 *   <li>Steps with order ≤ lastCompleted are skipped (idempotent)</li>
 * </ul>
 *
 * <p><strong>Provisioning flow:</strong>
 * <pre>
 * REQUESTED
 *     ↓
 * DATABASE_CREATING  → TenantDatabaseCreator.createDatabase()
 *     ↓
 * MIGRATION_RUNNING  → TenantMigrationService.migrate()
 *     ↓
 * INITIALIZATION_RUNNING → TenantInitializationService.initialize()
 *     ↓
 * COMPLETED → Tenant.status = PROVISIONED
 * </pre>
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final TenantRepository              tenantRepository;
    private final TenantProvisioningRepository  provisioningRepository;
    private final TenantDatabaseCreator         databaseCreator;
    private final TenantMigrationService        migrationService;
    private final TenantInitializationService   initializationService;
    private final TenantDataSourceFactory       dataSourceFactory;
    private final TenantDataSourceManager       dataSourceManager;
    private final TenantRegistryService         registryService;
    private final CredentialResolver            credentialResolver;

    public TenantProvisioningService(
            TenantRepository tenantRepository,
            TenantProvisioningRepository provisioningRepository,
            TenantDatabaseCreator databaseCreator,
            TenantMigrationService migrationService,
            TenantInitializationService initializationService,
            TenantDataSourceFactory dataSourceFactory,
            TenantDataSourceManager dataSourceManager,
            TenantRegistryService registryService,
            CredentialResolver credentialResolver) {
        this.tenantRepository      = tenantRepository;
        this.provisioningRepository = provisioningRepository;
        this.databaseCreator        = databaseCreator;
        this.migrationService       = migrationService;
        this.initializationService  = initializationService;
        this.dataSourceFactory      = dataSourceFactory;
        this.dataSourceManager      = dataSourceManager;
        this.registryService        = registryService;
        this.credentialResolver     = credentialResolver;
    }

    // -------------------------------------------------------------------------
    // Start Provisioning
    // -------------------------------------------------------------------------

    /**
     * Starts provisioning for a tenant that has never been provisioned before.
     *
     * <p>Idempotent — if a provisioning record already exists and is completed,
     * the method returns immediately. If it is failed, use {@link #retryProvisioning(UUID)}.
     *
     * @param tenantId the tenant's UUID
     * @throws IllegalStateException if tenant is already COMPLETED
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public TenantProvisioning startProvisioning(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Idempotency check
        if (provisioningRepository.existsByTenantId(tenantId)) {
            TenantProvisioning existing = provisioningRepository.findByTenantId(tenantId).get();
            if (existing.isCompleted()) {
                log.info("[Provisioning][{}] Already completed — skipping", tenant.getTenantCode());
                return existing;
            }
            log.info("[Provisioning][{}] Provisioning record exists with status [{}]; use retryProvisioning()",
                    tenant.getTenantCode(), existing.getStatus());
            return existing;
        }

        // Create provisioning record
        String provisioningCode = CodeGenerator.provisioningCode();
        TenantProvisioning provisioning = TenantProvisioning.create(tenantId, provisioningCode);
        provisioningRepository.save(provisioning);

        // Update tenant status
        tenant.markProvisioning();
        tenantRepository.save(tenant);

        log.info("[Provisioning][{}] Provisioning started. Code: {}", tenant.getTenantCode(), provisioningCode);
        return provisioning;
    }

    // -------------------------------------------------------------------------
    // Execute Provisioning
    // -------------------------------------------------------------------------

    /**
     * Executes the provisioning steps for the given tenant.
     *
     * <p>This method is designed to be called asynchronously or from a
     * background job after {@link #startProvisioning(UUID)} has created the record.
     *
     * <p><strong>Do NOT wrap this method in a single @Transactional.</strong>
     * Each step commits independently to maintain accurate state machine records.
     *
     * @param tenantId the tenant to provision
     */
    public void executeProvisioning(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        TenantProvisioning provisioning = provisioningRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantProvisioningException(
                        tenant.getTenantCode(), "REQUESTED",
                        "No provisioning record found. Call startProvisioning() first."));

        if (provisioning.isCompleted()) {
            log.info("[Provisioning][{}] Already completed. Nothing to do.", tenant.getTenantCode());
            return;
        }

        String tenantCode = tenant.getTenantCode();
        ProvisioningStep lastCompleted = parseStep(provisioning.getLastCompletedStep());

        log.info("[Provisioning][{}] Executing provisioning. Last completed step: {}",
                tenantCode, lastCompleted);

        try {
            // ── Step 1: Create Database ────────────────────────────────────────
            if (!ProvisioningStep.DATABASE_CREATING.isCompletedGiven(lastCompleted)) {
                advanceStep(provisioning, "DATABASE_CREATING");

                String dbUsername = credentialResolver.resolve(tenant.getDatabaseUsernameRef());
                String dbPassword = credentialResolver.resolve(tenant.getDatabasePasswordRef());

                databaseCreator.createDatabase(
                        tenant.getDatabaseName(),
                        dbUsername,
                        dbPassword,
                        tenantCode);

                completeStep(provisioning, "DATABASE_CREATING");
            } else {
                log.info("[Provisioning][{}] DATABASE_CREATING already done — skipping", tenantCode);
            }

            // Build a DataSource for the tenant DB (for migration + initialization)
            TenantRegistration registration = registryService.findByTenantCode(tenantCode)
                    .orElseThrow(() -> new TenantProvisioningException(tenantCode, "MIGRATION_RUNNING",
                            "Tenant not found in registry after DB creation"));

            DataSource tenantDs = dataSourceFactory.create(registration);

            try {
                // ── Step 2: Run Flyway ─────────────────────────────────────────
                if (!ProvisioningStep.MIGRATION_RUNNING.isCompletedGiven(lastCompleted)) {
                    advanceStep(provisioning, "MIGRATION_RUNNING");
                    migrationService.migrate(tenantDs, tenantCode);
                    completeStep(provisioning, "MIGRATION_RUNNING");
                } else {
                    log.info("[Provisioning][{}] MIGRATION_RUNNING already done — skipping", tenantCode);
                }

                // ── Step 3: Initialize Seed Data ───────────────────────────────
                if (!ProvisioningStep.INITIALIZATION_RUNNING.isCompletedGiven(lastCompleted)) {
                    advanceStep(provisioning, "INITIALIZATION_RUNNING");
                    initializationService.initialize(tenantDs, tenantCode);
                    completeStep(provisioning, "INITIALIZATION_RUNNING");
                } else {
                    log.info("[Provisioning][{}] INITIALIZATION_RUNNING already done — skipping", tenantCode);
                }

            } finally {
                // Close the temporary DataSource used only for provisioning
                dataSourceFactory.close(tenantDs, tenantCode + "-provisioning");
            }

            // ── Complete ───────────────────────────────────────────────────────
            markCompleted(provisioning, tenant);

            // Warm up the DataSource cache for the first request
            dataSourceManager.evict(tenantCode); // clear any partial cache
            registryService.evictCache(tenantCode);

            log.info("[Provisioning][{}] ✅ Provisioning COMPLETED successfully", tenantCode);

        } catch (Exception e) {
            log.error("[Provisioning][{}] ❌ Provisioning FAILED at step [{}]: {}",
                    tenantCode, provisioning.getCurrentStep(), e.getMessage(), e);
            markFailed(provisioning, tenant, e.getMessage());
            throw e instanceof TenantProvisioningException
                    ? (TenantProvisioningException) e
                    : new TenantProvisioningException(tenantCode,
                            provisioning.getCurrentStep(), e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Retry
    // -------------------------------------------------------------------------

    /**
     * Retries a previously failed provisioning process.
     *
     * <p>Resumes from the step after the last successfully completed step.
     * Payment is NOT re-charged — retry is purely a technical recovery.
     *
     * @param tenantId the tenant to retry
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public TenantProvisioning retryProvisioning(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        TenantProvisioning provisioning = provisioningRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantProvisioningException(
                        tenant.getTenantCode(), "REQUESTED", "No provisioning record found"));

        if (provisioning.isCompleted()) {
            throw new IllegalStateException("Provisioning is already completed for tenant: " + tenantId);
        }

        if (!provisioning.isFailed()) {
            throw new IllegalStateException(
                    "Can only retry FAILED provisioning. Current status: " + provisioning.getStatus());
        }

        provisioning.prepareForRetry();
        provisioning = provisioningRepository.save(provisioning);

        tenant.markProvisioning();
        tenantRepository.save(tenant);

        log.info("[Provisioning][{}] Retry #{} started from last completed step: {}",
                tenant.getTenantCode(), provisioning.getAttemptCount(),
                provisioning.getLastCompletedStep());

        return provisioning;
    }

    // -------------------------------------------------------------------------
    // Private helpers — each commits independently
    // -------------------------------------------------------------------------

    @Transactional(transactionManager = "masterTransactionManager")
    protected void advanceStep(TenantProvisioning provisioning, String step) {
        provisioning.advanceTo(step);
        provisioningRepository.save(provisioning);
        log.info("[Provisioning][{}] → Step: {}", provisioning.getTenantId(), step);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    protected void completeStep(TenantProvisioning provisioning, String step) {
        provisioning.completeStep(step);
        provisioningRepository.save(provisioning);
        log.info("[Provisioning][{}] ✓ Completed: {}", provisioning.getTenantId(), step);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    protected void markCompleted(TenantProvisioning provisioning, Tenant tenant) {
        provisioning.markCompleted();
        provisioningRepository.save(provisioning);
        tenant.markProvisioned();
        tenantRepository.save(tenant);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    protected void markFailed(TenantProvisioning provisioning, Tenant tenant, String reason) {
        provisioning.markFailed(reason);
        provisioningRepository.save(provisioning);
        tenant.markProvisioningFailed();
        tenantRepository.save(tenant);
    }

    private ProvisioningStep parseStep(String stepName) {
        if (stepName == null) return null;
        try {
            return ProvisioningStep.valueOf(stepName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
