package com.vidya.tenantdata.engagement.service;

import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyActivityReport;
import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates.Resolved;
import com.vidya.tenantdata.engagement.notification.OutboxDraft;
import com.vidya.tenantdata.engagement.notification.RecipientTargets;
import com.vidya.tenantdata.engagement.service.DailyReportQueries.GuardianContact;
import com.vidya.tenantdata.operations.enums.NotificationChannel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Daily report messages: one message per channel per guardian contact, covering all of that guardian's
 * children (a parent with two children at the school gets one email, not two).
 */
public final class DailyReportComposer {

    public static final String REF_TYPE = "DAILY_REPORT";

    private DailyReportComposer() {
    }

    /** @param recipients distinct people reached (one parent across several children counts once) */
    public record Composition(List<OutboxDraft> drafts, int recipients) {
    }

    public static Composition compose(List<DailyActivityReport> reports, List<GuardianContact> guardians, Resolved template,
                                      String schoolName, LocalDate date) {
        Map<UUID, DailyActivityReport> byStudent = new HashMap<>();
        reports.stream().filter(DailyReportFormatter::hasContent).forEach(r -> byStudent.put(r.studentId(), r));

        Map<String, Target> targets = new LinkedHashMap<>();
        Set<String> people = new HashSet<>();
        for (GuardianContact g : guardians) {
            if (!byStudent.containsKey(g.studentId())) {
                continue;
            }
            boolean reached = false;
            for (NotificationChannel ch : template.channels()) {
                String to = RecipientTargets.target(ch, g);
                if (to == null) {
                    continue;
                }
                targets.computeIfAbsent(ch + ":" + to, k -> new Target(ch, to, g.name())).students.add(g.studentId());
                reached = true;
            }
            if (reached) {
                people.add(RecipientTargets.person(g));
            }
        }

        String dateText = DailyReportFormatter.DATE.format(date);
        List<OutboxDraft> drafts = new ArrayList<>();
        for (Map.Entry<String, Target> e : targets.entrySet()) {
            Target t = e.getValue();
            List<DailyActivityReport> children = t.students.stream().map(byStudent::get).toList();
            boolean shortForm = t.channel == NotificationChannel.SMS || t.channel == NotificationChannel.WHATSAPP;
            Map<String, String> values = Map.of(
                    "date", dateText,
                    "children", DailyReportFormatter.childNames(children),
                    "summary", shortForm ? DailyReportFormatter.compact(children) : DailyReportFormatter.detailed(children),
                    "school_name", schoolName,
                    "today", dateText);
            boolean inApp = t.channel == NotificationChannel.IN_APP;
            drafts.add(new OutboxDraft(template.event().name(), t.channel, inApp ? UUID.fromString(t.to) : null, t.name,
                    inApp ? null : t.to, NotificationTemplates.render(template.subject(), values),
                    NotificationTemplates.render(template.body(), values), AnnouncementPriority.NORMAL, REF_TYPE, null, date,
                    REF_TYPE + ":" + date + ":" + e.getKey()));
        }
        return new Composition(drafts, people.size());
    }

    private static final class Target {
        final NotificationChannel channel;
        final String to;
        final String name;
        final Set<UUID> students = new LinkedHashSet<>();

        Target(NotificationChannel channel, String to, String name) {
            this.channel = channel;
            this.to = to;
            this.name = name;
        }
    }
}
