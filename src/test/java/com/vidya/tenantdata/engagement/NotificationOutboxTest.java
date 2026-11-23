package com.vidya.tenantdata.engagement;

import com.vidya.tenantdata.engagement.entity.NotificationOutbox;
import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.enums.OutboxStatus;
import com.vidya.tenantdata.engagement.notification.ChannelUnavailableException;
import com.vidya.tenantdata.engagement.notification.DeliveryContext;
import com.vidya.tenantdata.engagement.notification.NotificationOutboxService;
import com.vidya.tenantdata.engagement.notification.NotificationSender;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates;
import com.vidya.tenantdata.engagement.notification.OutboxDraft;
import com.vidya.tenantdata.engagement.notification.QuietHours;
import com.vidya.tenantdata.engagement.repository.NotificationOutboxRepository;
import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Notification outbox")
class NotificationOutboxTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Nested
    @DisplayName("Quiet hours")
    class Quiet {

        private Instant at(int hour, int minute) {
            return ZonedDateTime.of(2026, 11, 13, hour, minute, 0, 0, IST).toInstant();
        }

        @Test
        void outsideQuietHoursSendsNow() {
            Instant now = at(12, 0);
            assertThat(QuietHours.nextAllowed(now, LocalTime.of(21, 0), LocalTime.of(7, 0), IST)).isEqualTo(now);
        }

        @Test
        void lateEveningWaitsUntilNextMorning() {
            assertThat(QuietHours.nextAllowed(at(22, 30), LocalTime.of(21, 0), LocalTime.of(7, 0), IST))
                    .isEqualTo(ZonedDateTime.of(2026, 11, 14, 7, 0, 0, 0, IST).toInstant());
        }

        @Test
        void earlyMorningWaitsUntilSameMorning() {
            assertThat(QuietHours.nextAllowed(at(5, 0), LocalTime.of(21, 0), LocalTime.of(7, 0), IST))
                    .isEqualTo(ZonedDateTime.of(2026, 11, 13, 7, 0, 0, 0, IST).toInstant());
        }

        @Test
        void sameDayWindow() {
            assertThat(QuietHours.nextAllowed(at(14, 0), LocalTime.of(13, 0), LocalTime.of(15, 0), IST))
                    .isEqualTo(at(15, 0));
            assertThat(QuietHours.nextAllowed(at(15, 0), LocalTime.of(13, 0), LocalTime.of(15, 0), IST)).isEqualTo(at(15, 0));
        }

        @Test
        void noQuietHours() {
            Instant now = at(23, 0);
            assertThat(QuietHours.nextAllowed(now, null, null, IST)).isEqualTo(now);
        }
    }

    @Test
    @DisplayName("Placeholders are filled; unknown ones become empty")
    void render() {
        assertThat(NotificationTemplates.render("Hi {{ name }}, {{date}}{{unknown}}!", Map.of("name", "Asha", "date", "13 Nov")))
                .isEqualTo("Hi Asha, 13 Nov!");
        assertThat(NotificationTemplates.render("cost {{amount}}", Map.of("amount", "$5 \\ 6"))).isEqualTo("cost $5 \\ 6");
    }

    @Nested
    @DisplayName("Queue and deliver")
    class Service {

        @Mock private NotificationOutboxRepository repository;
        @Mock private NotificationTemplates templates;

        private final CommunicationSettings settings = new CommunicationSettings();
        private final Instant now = ZonedDateTime.of(2026, 11, 13, 22, 0, 0, 0, IST).toInstant();
        private final List<NotificationOutbox> saved = new ArrayList<>();
        private final AtomicReference<Exception> emailFailure = new AtomicReference<>();
        private NotificationOutboxService service;

        @BeforeEach
        void setUp() {
            settings.setInAppEnabled(true);
            settings.setEmailEnabled(true);
            settings.setSmsEnabled(true);
            settings.setQuietFrom(LocalTime.of(21, 0));
            settings.setQuietTo(LocalTime.of(7, 0));
            when(templates.settings()).thenReturn(settings);
            when(templates.deliveryContext(any())).thenReturn(new DeliveryContext("Green Valley School", null, null));
            when(repository.saveAll(any())).thenAnswer(inv -> {
                Iterable<NotificationOutbox> rows = inv.getArgument(0);
                rows.forEach(saved::add);
                return saved;
            });
            NotificationSender inApp = new NotificationSender() {
                public NotificationChannel channel() { return NotificationChannel.IN_APP; }
                public void send(NotificationOutbox m, DeliveryContext c) { }
            };
            NotificationSender email = new NotificationSender() {
                public NotificationChannel channel() { return NotificationChannel.EMAIL; }
                public void send(NotificationOutbox m, DeliveryContext c) throws Exception {
                    if (emailFailure.get() != null) {
                        throw emailFailure.get();
                    }
                }
            };
            service = new NotificationOutboxService(repository, templates, List.of(inApp, email), Clock.fixed(now, IST));
        }

        private OutboxDraft draft(NotificationChannel ch, String key, AnnouncementPriority priority) {
            boolean inApp = ch == NotificationChannel.IN_APP;
            return new OutboxDraft("DAILY_ACTIVITY_REPORT", ch, inApp ? UUID.randomUUID() : null, "Parent", inApp ? null : "x@y.z",
                    "Subject", "Body", priority, "DAILY_REPORT", null, LocalDate.of(2026, 11, 13), key);
        }

        @Test
        @DisplayName("Existing and repeated keys are queued once")
        void dedupe() {
            when(repository.findExistingKeys(anyCollection())).thenReturn(Set.of("k1"));
            int n = service.enqueue(List.of(draft(NotificationChannel.EMAIL, "k1", null), draft(NotificationChannel.EMAIL, "k2", null),
                    draft(NotificationChannel.EMAIL, "k2", null)));
            assertThat(n).isEqualTo(1);
            assertThat(saved).extracting(NotificationOutbox::getDedupeKey).containsExactly("k2");
        }

        @Test
        @DisplayName("SMS waits for quiet hours to end; email and emergencies go now")
        void quietHoursOnEnqueue() {
            when(repository.findExistingKeys(anyCollection())).thenReturn(Set.of());
            service.enqueue(List.of(draft(NotificationChannel.SMS, "sms", AnnouncementPriority.NORMAL),
                    draft(NotificationChannel.SMS, "sms-emergency", AnnouncementPriority.EMERGENCY),
                    draft(NotificationChannel.EMAIL, "email", null)));
            assertThat(saved.get(0).getNextAttemptAt()).isEqualTo(ZonedDateTime.of(2026, 11, 14, 7, 0, 0, 0, IST).toInstant());
            assertThat(saved.get(1).getNextAttemptAt()).isEqualTo(now);
            assertThat(saved.get(2).getNextAttemptAt()).isEqualTo(now);
            assertThat(saved.get(2).getPriority()).isEqualTo(AnnouncementPriority.NORMAL);
        }

        private NotificationOutbox row(NotificationChannel ch) {
            when(repository.findExistingKeys(anyCollection())).thenReturn(Set.of());
            service.enqueue(List.of(draft(ch, UUID.randomUUID().toString(), null)));
            NotificationOutbox o = saved.remove(saved.size() - 1);
            when(repository.lockDue(any(), anyInt())).thenReturn(List.of(o));
            return o;
        }

        @Test
        void sent() {
            NotificationOutbox o = row(NotificationChannel.IN_APP);
            assertThat(service.dispatchDue(10)).isEqualTo(1);
            assertThat(o.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(o.getSentAt()).isEqualTo(now);
            assertThat(o.getAttempts()).isEqualTo(1);
        }

        @Test
        void channelTurnedOffIsSkipped() {
            NotificationOutbox o = row(NotificationChannel.EMAIL);
            settings.setEmailEnabled(false);
            service.dispatchDue(10);
            assertThat(o.getStatus()).isEqualTo(OutboxStatus.SKIPPED);
            assertThat(o.getLastError()).contains("turned off");
        }

        @Test
        void noProviderIsSkipped() {
            NotificationOutbox o = row(NotificationChannel.SMS);
            service.dispatchDue(10);
            assertThat(o.getStatus()).isEqualTo(OutboxStatus.SKIPPED);
            assertThat(o.getLastError()).contains("No SMS provider");
        }

        @Test
        void unavailableProviderIsSkippedNotRetried() {
            NotificationOutbox o = row(NotificationChannel.EMAIL);
            emailFailure.set(new ChannelUnavailableException("Email is not configured"));
            service.dispatchDue(10);
            assertThat(o.getStatus()).isEqualTo(OutboxStatus.SKIPPED);
        }

        @Test
        void failureIsRetriedWithBackoffThenFails() {
            NotificationOutbox o = row(NotificationChannel.EMAIL);
            emailFailure.set(new IllegalStateException("SMTP down"));
            service.dispatchDue(10);
            assertThat(o.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(o.getNextAttemptAt()).isEqualTo(now.plus(Duration.ofMinutes(1)));
            assertThat(o.getLastError()).contains("SMTP down");
            for (int i = 0; i < 4; i++) {
                service.dispatchDue(10);
            }
            assertThat(o.getAttempts()).isEqualTo(5);
            assertThat(o.getStatus()).isEqualTo(OutboxStatus.FAILED);
        }
    }
}
