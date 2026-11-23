package com.vidya.tenantdata.engagement.notification;

import com.vidya.tenantdata.engagement.repository.DailyReportQueries.GuardianContact;
import com.vidya.tenantdata.operations.enums.NotificationChannel;

/** Where a guardian is reached on a channel; null when the guardian has no such contact. */
public final class RecipientTargets {

    private RecipientTargets() {
    }

    public static String target(NotificationChannel channel, GuardianContact g) {
        return switch (channel) {
            case IN_APP -> g.guardianUserId() == null ? null : g.guardianUserId().toString();
            case EMAIL -> email(g.email());
            case SMS, WHATSAPP -> phone(g.phone());
        };
    }

    /** One person across their children's guardian rows: login, else email, else phone, else the row. */
    public static String person(GuardianContact g) {
        if (g.guardianUserId() != null) {
            return "U:" + g.guardianUserId();
        }
        if (email(g.email()) != null) {
            return "E:" + email(g.email());
        }
        if (phone(g.phone()) != null) {
            return "P:" + phone(g.phone());
        }
        return "G:" + g.guardianId();
    }

    static String email(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    static String phone(String phone) {
        if (phone == null) {
            return null;
        }
        String p = phone.trim();
        String digits = p.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return null;
        }
        return (p.startsWith("+") ? "+" : "") + digits;
    }
}
