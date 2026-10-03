package com.vidya.tenant.datasource;

/**
 * Resolves actual database credentials from reference keys.
 *
 * <p>The {@code databaseUsernameRef} and {@code databasePasswordRef} stored in
 * the {@code tenant} table are NOT the actual credentials — they are reference
 * keys pointing to the real value in an environment variable or secrets manager.
 *
 * <p>Example:
 * <pre>
 *   databaseUsernameRef = "TENANT_ABC123_DB_USER"
 *   databasePasswordRef = "TENANT_ABC123_DB_PASS"
 *
 *   resolve("TENANT_ABC123_DB_USER") → "abc123_app_user"
 *   resolve("TENANT_ABC123_DB_PASS") → "s3cur3p@ss!"
 * </pre>
 *
 * <p>This abstraction allows switching from environment variables to
 * HashiCorp Vault, AWS Secrets Manager, or GCP Secret Manager
 * without changing any tenant infrastructure code.
 */
public interface CredentialResolver {

    /**
     * Resolves a credential reference key to its actual value.
     *
     * @param referenceKey the env var name or vault path key
     * @return the actual credential value
     * @throws CredentialResolutionException if the key cannot be resolved
     */
    String resolve(String referenceKey);
}
