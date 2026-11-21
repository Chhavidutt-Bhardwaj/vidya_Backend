package com.vidya.tenantdata.engagement.notification;

import com.vidya.tenantdata.engagement.entity.NotificationOutbox;
import com.vidya.tenantdata.operations.enums.NotificationChannel;

/**
 * Delivers outbox messages on one channel. Add a Spring bean to plug in a provider (an SMS gateway, the
 * WhatsApp Business API, a push service); a channel without a sender is marked SKIPPED.
 *
 * <p>Throw {@link ChannelUnavailableException} when the provider is not set up (no retry); any other
 * exception is retried with back-off.
 */
public interface NotificationSender {

    NotificationChannel channel();

    void send(NotificationOutbox message, DeliveryContext context) throws Exception;
}
