package com.vidya.tenant.datasource;

import lombok.Getter;

/**
 * Thrown when a credential reference key cannot be resolved.
 */
@Getter
public class CredentialResolutionException extends RuntimeException {

    private final String referenceKey;

    public CredentialResolutionException(String referenceKey) {
        super("Cannot resolve credential for reference key: " + referenceKey
                + " — ensure the environment variable or secret is configured");
        this.referenceKey = referenceKey;
    }
}
