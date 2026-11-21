package com.vidya.tenantdata.engagement.notification;

import com.vidya.tenantdata.engagement.dto.EngagementDtos.Inbox;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.InboxItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.OutboxItem;
import com.vidya.tenantdata.engagement.entity.NotificationOutbox;
import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.enums.OutboxStatus;
import com.vidya.tenantdata.engagement.repository.NotificationOutboxRepository;
import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The notification outbox: queue messages (deduplicated, quiet hours applied), deliver due ones through the
 * channel senders with retry, and serve each user's in-app inbox.
 */
@Slf4j
@Service
public class NotificationOutboxService {

    static final int MAX_ATTEMPTS = 5;
    private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1));
    private static final int KEY_CHUNK = 1000;

    private final NotificationOutboxRepository repository;
    private final NotificationTemplates templates;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);
    private final Clock clock;

    public NotificationOutboxService(NotificationOutboxRepository repository, NotificationTemplates templates,
                                     List<NotificationSender> senders, Clock clock) {
        this.repository = repository;
        this.templates = templates;
        this.clock = clock;
        senders.forEach(s -> this.senders.put(s.channel(), s));
    }

    /** Queues the drafts whose key is new. SMS / WhatsApp wait for quiet hours to end unless EMERGENCY. Returns how many were queued. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public int enqueue(Collection<OutboxDraft> drafts) {
        if (drafts.isEmpty()) {
            return 0;
        }
        Set<String> seen = new HashSet<>();
        List<String> keys = drafts.stream().map(OutboxDraft::dedupeKey).distinct().toList();
        for (int i = 0; i < keys.size(); i += KEY_CHUNK) {
            seen.addAll(repository.findExistingKeys(keys.subList(i, Math.min(keys.size(), i + KEY_CHUNK))));
        }
        CommunicationSettings s = templates.settings();
        Instant now = clock.instant();
        List<NotificationOutbox> rows = new ArrayList<>();
        for (OutboxDraft d : drafts) {
            if (!seen.add(d.dedupeKey())) {
                continue;
            }
            NotificationOutbox o = new NotificationOutbox(d.eventCode(), d.channel(), d.dedupeKey());
            o.setRecipientUserId(d.recipientUserId());
            o.setRecipientName(truncate(d.recipientName(), 200));
            o.setAddress(d.address());
            o.setSubject(truncate(d.subject(), 300));
            o.setBody(d.body());
            o.setPriority(d.priority() == null ? AnnouncementPriority.NORMAL : d.priority());
            o.setRefType(d.refType());
            o.setRefId(d.refId());
            o.setRefDate(d.refDate());
            boolean quietApplies = (d.channel() == NotificationChannel.SMS || d.channel() == NotificationChannel.WHATSAPP)
                    && o.getPriority() != AnnouncementPriority.EMERGENCY;
            o.setNextAttemptAt(quietApplies ? QuietHours.nextAllowed(now, s.getQuietFrom(), s.getQuietTo(), clock.getZone()) : now);
            rows.add(o);
        }
        repository.saveAll(rows);
        return rows.size();
    }

    /** Delivers up to {@code limit} due messages of the current school. Returns how many were handled. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public int dispatchDue(int limit) {
        Instant now = clock.instant();
        List<NotificationOutbox> due = repository.lockDue(now, limit);
        if (due.isEmpty()) {
            return 0;
        }
        CommunicationSettings s = templates.settings();
        Set<NotificationChannel> on = NotificationTemplates.channelsOn(s);
        DeliveryContext ctx = templates.deliveryContext(s);
        for (NotificationOutbox o : due) {
            deliver(o, on, ctx, now);
        }
        repository.saveAll(due);
        return due.size();
    }

    void deliver(NotificationOutbox o, Set<NotificationChannel> on, DeliveryContext ctx, Instant now) {
        if (!on.contains(o.getChannel())) {
            skip(o, o.getChannel() + " is turned off in the communication settings");
            return;
        }
        NotificationSender sender = senders.get(o.getChannel());
        if (sender == null) {
            skip(o, "No " + o.getChannel() + " provider is configured on this server");
            return;
        }
        o.setAttempts(o.getAttempts() + 1);
        try {
            sender.send(o, ctx);
            o.setStatus(OutboxStatus.SENT);
            o.setSentAt(now);
            o.setLastError(null);
        } catch (ChannelUnavailableException e) {
            skip(o, e.getMessage());
        } catch (Exception e) {
            o.setLastError(truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 2000));
            if (o.getAttempts() >= MAX_ATTEMPTS) {
                o.setStatus(OutboxStatus.FAILED);
                log.warn("Notification [{}] on {} failed for good after {} attempts: {}", o.getId(), o.getChannel(), o.getAttempts(), e.getMessage());
            } else {
                o.setNextAttemptAt(now.plus(BACKOFF.get(Math.min(o.getAttempts(), BACKOFF.size()) - 1)));
            }
        }
    }

    private static void skip(NotificationOutbox o, String reason) {
        o.setStatus(OutboxStatus.SKIPPED);
        o.setLastError(reason);
    }

    // ── inbox ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Inbox inbox(Collection<UUID> tenantUserIds, boolean unreadOnly, Integer limit) {
        if (tenantUserIds.isEmpty()) {
            return new Inbox(0, List.of());
        }
        int max = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        List<InboxItem> items = repository.inbox(tenantUserIds, unreadOnly, max).stream()
                .map(o -> new InboxItem(o.getId(), o.getEventCode(), o.getSubject(), o.getBody(), o.getPriority(), o.getRefType(),
                        o.getRefId(), o.getRefDate(), o.getCreatedAt(), o.getReadAt()))
                .toList();
        return new Inbox(repository.unread(tenantUserIds), items);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void markRead(Collection<UUID> tenantUserIds, UUID id) {
        NotificationOutbox o = repository.findById(id)
                .filter(m -> m.getChannel() == NotificationChannel.IN_APP && tenantUserIds.contains(m.getRecipientUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (o.getReadAt() == null) {
            o.setReadAt(clock.instant());
            repository.save(o);
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public int markAllRead(Collection<UUID> tenantUserIds) {
        return tenantUserIds.isEmpty() ? 0 : repository.markAllRead(tenantUserIds);
    }

    /** Recent outbox rows, newest first (delivery troubleshooting for admins). */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<OutboxItem> recent(Integer limit) {
        int max = limit == null ? 100 : Math.max(1, Math.min(limit, 500));
        return repository.findAll(PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "createdAt"))).stream()
                .map(o -> new OutboxItem(o.getId(), o.getEventCode(), o.getChannel(), o.getRecipientName(), o.getAddress(), o.getSubject(),
                        o.getPriority(), o.getStatus(), o.getAttempts(), o.getNextAttemptAt(), o.getSentAt(), o.getLastError(), o.getCreatedAt()))
                .toList();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
