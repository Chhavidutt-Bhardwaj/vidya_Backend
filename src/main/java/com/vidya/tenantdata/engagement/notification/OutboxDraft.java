package com.vidya.tenantdata.engagement.notification;

import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.operations.enums.NotificationChannel;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A message to queue. {@code dedupeKey} identifies it: queuing the same key twice sends it once.
 *
 * @param recipientUserId tenant_user (IN_APP)
 * @param address         email or phone (the other channels)
 */
public record OutboxDraft(String eventCode, NotificationChannel channel, UUID recipientUserId, String recipientName, String address,
                          String subject, String body, AnnouncementPriority priority, String refType, UUID refId, LocalDate refDate,
                          String dedupeKey) {
}
