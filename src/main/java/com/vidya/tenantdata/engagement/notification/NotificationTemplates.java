package com.vidya.tenantdata.engagement.notification;

import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import com.vidya.tenantdata.operations.entity.NotificationTemplate;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.repository.CommunicationSettingsRepository;
import com.vidya.tenantdata.operations.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The wording and channels of an event as the school set them up (operations-config → communication),
 * falling back to the built-in text and IN_APP. Callers own the transaction.
 */
@Component
@RequiredArgsConstructor
public class NotificationTemplates {

    private static final short SETTINGS_ID = 1;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*}}");
    private static final String DEFAULT_SCHOOL_NAME = "School";

    private final CommunicationSettingsRepository settingsRepository;
    private final NotificationTemplateRepository templateRepository;

    /**
     * @param channels the template's channels that the school has also switched on (may be empty)
     */
    public record Resolved(NotificationEvent event, boolean enabled, Set<NotificationChannel> channels, String subject, String body) {
    }

    public Resolved resolve(NotificationEvent event) {
        Set<NotificationChannel> on = channelsOn(settings());
        NotificationTemplate t = templateRepository.findByEventCode(event.name()).orElse(null);
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (t == null) {
            channels.add(NotificationChannel.IN_APP);
        } else {
            Arrays.stream(t.getChannels().split(",")).map(String::trim).filter(c -> !c.isEmpty())
                    .map(NotificationChannel::valueOf).forEach(channels::add);
        }
        channels.retainAll(on);
        return new Resolved(event, t == null || t.isEnabled(), channels, t == null ? event.defaultSubject() : t.getSubject(),
                t == null ? event.defaultBody() : t.getBody());
    }

    public CommunicationSettings settings() {
        return settingsRepository.findById(SETTINGS_ID).orElseGet(CommunicationSettings::new);
    }

    /** Sender name of the communication settings, used as {{school_name}}. */
    public String schoolName(CommunicationSettings s) {
        return s.getSenderName() == null || s.getSenderName().isBlank() ? DEFAULT_SCHOOL_NAME : s.getSenderName();
    }

    public DeliveryContext deliveryContext(CommunicationSettings s) {
        return new DeliveryContext(s.getSenderName(), s.getReplyToEmail(), s.getSmsSenderId());
    }

    public static Set<NotificationChannel> channelsOn(CommunicationSettings s) {
        Set<NotificationChannel> on = EnumSet.noneOf(NotificationChannel.class);
        if (s.isInAppEnabled()) {
            on.add(NotificationChannel.IN_APP);
        }
        if (s.isEmailEnabled()) {
            on.add(NotificationChannel.EMAIL);
        }
        if (s.isSmsEnabled()) {
            on.add(NotificationChannel.SMS);
        }
        if (s.isWhatsappEnabled()) {
            on.add(NotificationChannel.WHATSAPP);
        }
        return on;
    }

    /** Replaces {{name}} with its value; unknown placeholders become empty. */
    public static String render(String text, Map<String, String> values) {
        if (text == null) {
            return "";
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(out);
        return out.toString();
    }
}
