package com.vidya.tenantdata.engagement.notification;

/** School-level sender details from the communication settings. */
public record DeliveryContext(String senderName, String replyToEmail, String smsSenderId) {
}
