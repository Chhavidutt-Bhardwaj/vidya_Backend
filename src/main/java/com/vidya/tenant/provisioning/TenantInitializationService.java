package com.vidya.tenant.provisioning;

import com.vidya.common.exception.TenantProvisioningException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

/**
 * Seeds initial data into a freshly migrated tenant database.
 *
 * <p>After Flyway has run, this service inserts the {@code tenant_metadata} row
 * that proves the tenant is initialized and identifies which platform tenant
 * this database belongs to.
 *
 * <p><strong>Idempotency:</strong> Checks if the tenant_metadata row already
 * exists before inserting. Safe to call on retry.
 */
@Component
public class TenantInitializationService {

    private static final Logger log = LoggerFactory.getLogger(TenantInitializationService.class);

    @Value("${spring.application.version:0.0.1-SNAPSHOT}")
    private String appVersion;

    /**
     * Inserts the {@code tenant_metadata} seed row into the tenant database.
     *
     * @param tenantDataSource the tenant's DataSource
     * @param tenantCode       the tenant's unique code
     * @throws TenantProvisioningException if initialization fails
     */
    public void initialize(DataSource tenantDataSource, String tenantCode) {
        log.info("[Provisioning][{}] Initializing tenant database", tenantCode);

        try (Connection conn = tenantDataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                if (metadataExists(conn, tenantCode)) {
                    log.info("[Provisioning][{}] tenant_metadata already exists — skipping initialization", tenantCode);
                    return;
                }

                insertTenantMetadata(conn, tenantCode);
                conn.commit();

                log.info("[Provisioning][{}] Tenant database initialized successfully", tenantCode);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }

        } catch (TenantProvisioningException e) {
            throw e;
        } catch (Exception e) {
            throw new TenantProvisioningException(tenantCode, "INITIALIZATION_RUNNING",
                    "Failed to initialize tenant database: " + e.getMessage(), e);
        }
    }

    private boolean metadataExists(Connection conn, String tenantCode) throws Exception {
        String sql = "SELECT COUNT(*) FROM tenant_metadata WHERE tenant_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private void insertTenantMetadata(Connection conn, String tenantCode) throws Exception {
        String sql = "INSERT INTO tenant_metadata " +
                "(tenant_code, schema_initialized_at, app_version) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantCode);
            ps.setObject(2, Instant.now());
            ps.setString(3, appVersion);
            ps.executeUpdate();
            log.info("[Provisioning][{}] Inserted tenant_metadata", tenantCode);
        }
    }
}
