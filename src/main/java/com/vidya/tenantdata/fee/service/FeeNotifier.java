package com.vidya.tenantdata.fee.service;

import com.vidya.tenantdata.engagement.notification.NotificationOutboxService;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates.Resolved;
import com.vidya.tenantdata.engagement.notification.OutboxDraft;
import com.vidya.tenantdata.engagement.notification.RecipientTargets;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries.GuardianContact;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Queues fee messages to students' guardians through the notification outbox, using the school's templates. */
@Component
@RequiredArgsConstructor
public class FeeNotifier {

    private final NotificationTemplates templates;
    private final NotificationOutboxService outbox;
    private final DailyReportQueries contacts;

    /**
     * One message to send: for whose guardians, with which template values, under which dedupe key prefix.
     * {@code onlyGuardianIds}: just these guardian rows (the payer of a mandate or link); null = every guardian of the student.
     */
    public record Message(UUID studentId, Map<String, String> values, String refType, UUID refId, String dedupePrefix, Set<UUID> onlyGuardianIds) {

        public Message(UUID studentId, Map<String, String> values, String refType, UUID refId, String dedupePrefix) {
            this(studentId, values, refType, refId, dedupePrefix, null);
        }
    }

    /** What happened: messages queued, students none of whose guardians can be reached, and messages prepared per student. */
    public record Outcome(int queued, Set<UUID> unreachable, Map<UUID, Integer> perStudent, Set<NotificationChannel> channels, boolean templateEnabled) {
    }

    /**
     * @param requested channels asked for; empty or null = the template's. Either way only channels switched on for the school are used.
     */
    public Outcome send(NotificationEvent event, Collection<Message> messages, Set<NotificationChannel> requested) {
        Resolved t = templates.resolve(event);
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (requested == null || requested.isEmpty()) {
            channels.addAll(t.channels());
        } else {
            channels.addAll(requested);
            channels.retainAll(NotificationTemplates.channelsOn(templates.settings()));
        }
        if (!t.enabled() || channels.isEmpty() || messages.isEmpty()) {
            return new Outcome(0, Set.of(), Map.of(), channels, t.enabled());
        }
        String school = templates.schoolName(templates.settings());
        Map<UUID, List<GuardianContact>> byStudent = contacts.guardians(messages.stream().map(Message::studentId).collect(Collectors.toSet()))
                .stream().collect(Collectors.groupingBy(GuardianContact::studentId));
        List<OutboxDraft> drafts = new ArrayList<>();
        Set<UUID> unreachable = new HashSet<>();
        Map<UUID, Integer> perStudent = new HashMap<>();
        for (Message m : messages) {
            Map<String, String> values = new HashMap<>(m.values());
            values.putIfAbsent("school_name", school);
            String subject = NotificationTemplates.render(t.subject(), values);
            String body = NotificationTemplates.render(t.body(), values);
            boolean reached = false;
            for (GuardianContact g : byStudent.getOrDefault(m.studentId(), List.of())) {
                if (m.onlyGuardianIds() != null && !m.onlyGuardianIds().contains(g.guardianId())) {
                    continue;
                }
                for (NotificationChannel ch : channels) {
                    String target = RecipientTargets.target(ch, g);
                    if (target == null) {
                        continue;
                    }
                    reached = true;
                    perStudent.merge(m.studentId(), 1, Integer::sum);
                    UUID userId = ch == NotificationChannel.IN_APP ? g.guardianUserId() : null;
                    drafts.add(new OutboxDraft(event.name(), ch, userId, g.name(), ch == NotificationChannel.IN_APP ? null : target, subject, body,
                            null, m.refType(), m.refId(), null, m.dedupePrefix() + ":" + ch + ":" + RecipientTargets.person(g)));
                }
            }
            if (!reached) {
                unreachable.add(m.studentId());
            }
        }
        return new Outcome(outbox.enqueue(drafts), unreachable, perStudent, channels, true);
    }
}
