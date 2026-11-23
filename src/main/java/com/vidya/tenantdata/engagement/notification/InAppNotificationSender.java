package com.vidya.tenantdata.engagement.notification;

import com.vidya.tenantdata.engagement.entity.NotificationOutbox;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import org.springframework.stereotype.Component;

/** In-app messages live in the outbox itself; once SENT they show in the recipient's inbox. */
@Component
public class InAppNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void send(NotificationOutbox message, DeliveryContext context) {
        // nothing to hand over: the inbox reads SENT in-app rows
    }
}
