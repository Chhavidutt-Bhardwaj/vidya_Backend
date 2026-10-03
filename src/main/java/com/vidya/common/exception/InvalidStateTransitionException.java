package com.vidya.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a state machine transition is not allowed.
 *
 * <p>Maps to HTTP 422 Unprocessable Content.
 * Used by onboarding, tenant provisioning, subscription, and school status state machines.
 */
@Getter
@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class InvalidStateTransitionException extends RuntimeException {

    private final String entity;
    private final String fromState;
    private final String toState;

    public InvalidStateTransitionException(String entity, String fromState, String toState) {
        super(String.format(
                "Invalid state transition for %s: cannot move from [%s] to [%s]",
                entity, fromState, toState));
        this.entity = entity;
        this.fromState = fromState;
        this.toState = toState;
    }
}
