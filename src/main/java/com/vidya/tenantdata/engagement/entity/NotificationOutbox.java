package com.vidya.tenantdata.engagement.entity;

import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.enums.OutboxStatus;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One message on one channel to one recipient. Written in the same transaction as the thing that caused it,
 * then delivered by the dispatcher. In-app messages are the user's inbox.
 */
@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_code", nullable = false, length = 50, updatable = false)
    private String eventCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private NotificationChannel channel;

    /** tenant_user of the recipient (required for IN_APP). */
    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    /** Email address or phone number (required for every channel but IN_APP). */
    @Column(length = 255)
    private String address;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementPriority priority = AnnouncementPriority.NORMAL;

    @Column(name = "ref_type", length = 50)
    private String refType;

    @Column(name = "ref_id")
    private UUID refId;

    @Column(name = "ref_date")
    private LocalDate refDate;

    @Column(name = "dedupe_key", nullable = false, length = 300, updatable = false)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version = 0L;

    public NotificationOutbox(String eventCode, NotificationChannel channel, String dedupeKey) {
        this.eventCode = eventCode;
        this.channel = channel;
        this.dedupeKey = dedupeKey;
    }
}
