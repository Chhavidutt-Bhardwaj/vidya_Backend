package com.vidya.tenant.provisioning;

import com.vidya.common.exception.TenantProvisioningException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Creates the PostgreSQL database and user for a new tenant.
 *
 * <p>This class uses a privileged admin connection (not the master app connection)
 * to execute DDL commands ({@code CREATE DATABASE}, {@code CREATE USER}, {@code GRANT}).
 *
 * <p><strong>Why a separate admin connection?</strong>
 * The master app connection is a low-privilege user that cannot create databases.
 * A dedicated provisioning admin (e.g., a DBA account or a superuser) is used
 * only during provisioning, then released.
 *
 * <p><strong>Idempotency:</strong> The method checks for database existence before
 * creating. If the database already exists, it is a no-op (idempotent for retry).
 *
 * <p><strong>Security:</strong>
 * <ul>
 *   <li>Uses parameterized validation for database/user names before injection</li>
 *   <li>Names are validated against {@code [a-z0-9_]} pattern</li>
 *   <li>Admin credentials come from environment variables, never from the tenant table</li>
 * </ul>
 */
@Component
public class TenantDatabaseCreator {

    private static final Logger log = LoggerFactory.getLogger(TenantDatabaseCreator.class);

    /** Only lowercase alphanumeric + underscore allowed in DB/user names */
    private static final String SAFE_NAME_PATTERN = "^[a-z][a-z0-9_]{1,62}$";

    @Value("${vidya.provisioning.admin.url}")
    private String adminJdbcUrl;

    @Value("${vidya.provisioning.admin.username}")
    private String adminUsername;

    @Value("${vidya.provisioning.admin.password}")
    private String adminPassword;

    /**
     * Creates the tenant's PostgreSQL database and dedicated application user.
     *
     * <p>Idempotent — safe to call multiple times. If database/user already
     * exist, the operation succeeds without error.
     *
     * @param databaseName the name to give the new database (e.g. {@code vidya_tenant_abc12345})
     * @param dbUsername   the application username for this tenant
     * @param dbPassword   the application password for this tenant
     * @param tenantCode   used for logging only
     * @throws TenantProvisioningException if an unexpected error occurs
     */
    public void createDatabase(String databaseName,
                               String dbUsername,
                               String dbPassword,
                               String tenantCode) {

        validateName(databaseName, tenantCode, "database name");
        validateName(dbUsername,   tenantCode, "database username");

        log.info("[Provisioning][{}] Creating database [{}] and user [{}]",
                tenantCode, databaseName, dbUsername);

        try (Connection conn = DriverManager.getConnection(adminJdbcUrl, adminUsername, adminPassword)) {
            conn.setAutoCommit(true); // DDL cannot run in a transaction in PostgreSQL

            // 1. Create user if not exists (idempotent)
            if (!userExists(conn, dbUsername)) {
                createUser(conn, dbUsername, dbPassword, tenantCode);
            } else {
                log.info("[Provisioning][{}] User [{}] already exists — skipping", tenantCode, dbUsername);
            }

            // 2. Create database if not exists (idempotent)
            if (!databaseExists(conn, databaseName)) {
                createDatabase(conn, databaseName, dbUsername, tenantCode);
            } else {
                log.info("[Provisioning][{}] Database [{}] already exists — skipping", tenantCode, databaseName);
            }

            // 3. Ensure grants (idempotent)
            grantPrivileges(conn, databaseName, dbUsername, tenantCode);

            log.info("[Provisioning][{}] Database [{}] ready for tenant", tenantCode, databaseName);

        } catch (TenantProvisioningException e) {
            throw e;
        } catch (Exception e) {
            throw new TenantProvisioningException(tenantCode, "DATABASE_CREATING",
                    "Failed to create database [" + databaseName + "]", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean databaseExists(Connection conn, String databaseName) throws Exception {
        String sql = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, databaseName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean userExists(Connection conn, String username) throws Exception {
        String sql = "SELECT 1 FROM pg_roles WHERE rolname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void createUser(Connection conn, String username, String password,
                            String tenantCode) throws Exception {
        // Name is pre-validated — safe to interpolate
        String sql = "CREATE USER " + username + " WITH PASSWORD '" +
                password.replace("'", "''") + "'";  // escape single quotes in password
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[Provisioning][{}] Created user [{}]", tenantCode, username);
        }
    }

    private void createDatabase(Connection conn, String databaseName, String owner,
                                String tenantCode) throws Exception {
        // Name is pre-validated — safe to interpolate
        String sql = "CREATE DATABASE " + databaseName + " OWNER " + owner
                + " ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8'";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[Provisioning][{}] Created database [{}]", tenantCode, databaseName);
        }
    }

    private void grantPrivileges(Connection conn, String databaseName, String username,
                                 String tenantCode) throws Exception {
        String sql = "GRANT ALL PRIVILEGES ON DATABASE " + databaseName + " TO " + username;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[Provisioning][{}] Granted privileges on [{}] to [{}]",
                    tenantCode, databaseName, username);
        }
    }

    /**
     * Validates that a name contains only safe characters to prevent SQL injection.
     * DDL statements cannot be parameterized, so names are validated before interpolation.
     */
    private void validateName(String name, String tenantCode, String context) {
        if (name == null || !name.matches(SAFE_NAME_PATTERN)) {
            throw new TenantProvisioningException(tenantCode, "DATABASE_CREATING",
                    "Invalid " + context + " [" + name + "]: must match pattern " + SAFE_NAME_PATTERN);
        }
    }
}
