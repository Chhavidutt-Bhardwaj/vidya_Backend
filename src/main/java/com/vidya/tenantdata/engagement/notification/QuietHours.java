package com.vidya.tenantdata.engagement.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** The school's quiet hours (no SMS / WhatsApp), which may cross midnight, e.g. 21:00 – 07:00. */
public final class QuietHours {

    private QuietHours() {
    }

    /** {@code now} when outside quiet hours (or none are set); otherwise the moment quiet hours end. */
    public static Instant nextAllowed(Instant now, LocalTime from, LocalTime to, ZoneId zone) {
        if (from == null || to == null || from.equals(to)) {
            return now;
        }
        LocalDateTime local = LocalDateTime.ofInstant(now, zone);
        LocalTime t = local.toLocalTime();
        boolean crossesMidnight = from.isAfter(to);
        boolean inside = crossesMidnight ? (!t.isBefore(from) || t.isBefore(to)) : (!t.isBefore(from) && t.isBefore(to));
        if (!inside) {
            return now;
        }
        LocalDate endDay = crossesMidnight && !t.isBefore(from) ? local.toLocalDate().plusDays(1) : local.toLocalDate();
        return endDay.atTime(to).atZone(zone).toInstant();
    }
}
