package com.vidya.tenant.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Environment-variable-based credential resolver.
 *
 * <p>Resolves credentials from system environment variables.
 * The reference key stored in the database is the environment variable name.
 *
 * <p>Example:
 * <pre>
 *   tenant.databaseUsernameRef = "TENANT_ABC123_DB_USER"
 *   System.getenv("TENANT_ABC123_DB_USER") → "abc123_app_user"
 * </pre>
 *
 * <p><strong>Production deployment:</strong> Environment variables should be
 * injected via Kubernetes Secrets, AWS Parameter Store, or similar.
 * This implementation works correctly in any environment where secrets are
 * exposed as environment variables.
 *
 * <p><strong>Future:</strong> Replace with a Vault or Secrets Manager
 * implementation by implementing {@link CredentialResolver} and registering
 * the new bean as {@code @Primary}.
 */
@Component
public class EnvironmentCredentialResolver implements CredentialResolver {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentCredentialResolver.class);

    @Override
    public String resolve(String referenceKey) {
        if (referenceKey == null || referenceKey.isBlank()) {
            throw new CredentialResolutionException("(blank reference key)");
        }

        String value = System.getenv(referenceKey);

        if (value == null || value.isBlank()) {
            // Log the missing key name (safe) — NEVER log the value
            log.error("Credential reference key [{}] not found in environment. " +
                    "Ensure this environment variable is set before provisioning.", referenceKey);
            throw new CredentialResolutionException(referenceKey);
        }

        return value;
    }
}
