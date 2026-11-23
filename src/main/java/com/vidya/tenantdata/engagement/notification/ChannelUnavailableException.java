package com.vidya.tenantdata.engagement.notification;

/** The channel cannot deliver on this server (no provider configured). The message is SKIPPED, not retried. */
public class ChannelUnavailableException extends RuntimeException {

    public ChannelUnavailableException(String message) {
        super(message);
    }
}
