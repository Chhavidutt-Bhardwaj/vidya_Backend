package com.vidya.tenantdata.operations.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CommunicationSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CommunicationSettingsResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.TemplateRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.TemplateResponse;
import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import com.vidya.tenantdata.operations.entity.NotificationTemplate;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.repository.CommunicationSettingsRepository;
import com.vidya.tenantdata.operations.repository.NotificationTemplateRepository;
import com.vidya.tenantdata.user.account.SchoolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Communication setup: which channels the school uses and the wording of each automatic message.
 * This service stores the setup only; it does not send anything.
 */
@Service
@RequiredArgsConstructor
public class CommunicationConfigService {

    private static final short ID = 1;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*}}");
    /** Channels a template uses until the school chooses its own. */
    private static final List<NotificationChannel> DEFAULT_CHANNELS = List.of(NotificationChannel.IN_APP);

    private final CommunicationSettingsRepository settingsRepository;
    private final NotificationTemplateRepository templateRepository;
    private final SchoolContext context;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public CommunicationSettingsResponse settings() {
        return settingsRepository.findById(ID).map(s -> response(s, true)).orElseGet(() -> response(new CommunicationSettings(), false));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public CommunicationSettingsResponse saveSettings(CommunicationSettingsRequest r) {
        if ((r.quietFrom() == null) != (r.quietTo() == null)) {
            throw new BusinessException("Quiet hours need both quietFrom and quietTo");
        }
        if (r.quietFrom() != null && r.quietFrom().equals(r.quietTo())) {
            throw new BusinessException("quietFrom and quietTo are the same time");
        }
        CommunicationSettings s = settingsRepository.findById(ID).orElseGet(CommunicationSettings::new);
        s.setInAppEnabled(r.inAppEnabled() == null || r.inAppEnabled());
        s.setEmailEnabled(Boolean.TRUE.equals(r.emailEnabled()));
        s.setSmsEnabled(Boolean.TRUE.equals(r.smsEnabled()));
        s.setWhatsappEnabled(Boolean.TRUE.equals(r.whatsappEnabled()));
        s.setSenderName(FeeConfigService.blank(r.senderName()) ? null : r.senderName().trim());
        s.setReplyToEmail(FeeConfigService.blank(r.replyToEmail()) ? null : r.replyToEmail().trim().toLowerCase());
        s.setSmsSenderId(FeeConfigService.blank(r.smsSenderId()) ? null : r.smsSenderId().trim().toUpperCase());
        s.setQuietFrom(r.quietFrom());
        s.setQuietTo(r.quietTo());
        s.setDefaultLanguage(FeeConfigService.blank(r.defaultLanguage()) ? "en" : r.defaultLanguage().trim());
        s.setUpdatedBy(context.actorId());
        return response(settingsRepository.save(s), true);
    }

    /** Every event, with the school's wording where it wrote one and the built-in wording otherwise. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<TemplateResponse> templates() {
        Map<String, NotificationTemplate> stored = templateRepository.findAll().stream()
                .collect(Collectors.toMap(NotificationTemplate::getEventCode, Function.identity(), (a, b) -> a));
        Set<NotificationChannel> on = channelsOn();
        return Arrays.stream(NotificationEvent.values()).map(e -> response(e, stored.get(e.name()), on)).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public TemplateResponse saveTemplate(NotificationEvent event, TemplateRequest r) {
        Set<String> unknown = new TreeSet<>(placeholders(r.subject() + " " + r.body()));
        unknown.removeAll(event.placeholders());
        unknown.removeAll(NotificationEvent.COMMON_PLACEHOLDERS);
        if (!unknown.isEmpty()) {
            List<String> allowed = new ArrayList<>(event.placeholders());
            allowed.addAll(NotificationEvent.COMMON_PLACEHOLDERS);
            throw new BusinessException("UNKNOWN_PLACEHOLDER", "Unknown placeholder(s) " + unknown.stream().map(p -> "{{" + p + "}}").toList()
                    + " for " + event.label() + ". Allowed: " + allowed.stream().map(p -> "{{" + p + "}}").toList());
        }
        NotificationTemplate t = templateRepository.findByEventCode(event.name()).orElseGet(() -> new NotificationTemplate(event.name()));
        t.setEnabled(r.enabled() == null || r.enabled());
        t.setChannels(EnumSet.copyOf(r.channels()).stream().map(Enum::name).collect(Collectors.joining(",")));
        t.setSubject(r.subject().trim());
        t.setBody(r.body().trim());
        t.setUpdatedBy(context.actorId());
        return response(event, templateRepository.save(t), channelsOn());
    }

    /** Back to the built-in wording and channel. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public TemplateResponse resetTemplate(NotificationEvent event) {
        templateRepository.findByEventCode(event.name()).ifPresent(templateRepository::delete);
        return response(event, null, channelsOn());
    }

    static Set<String> placeholders(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private Set<NotificationChannel> channelsOn() {
        CommunicationSettings s = settingsRepository.findById(ID).orElseGet(CommunicationSettings::new);
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

    private static TemplateResponse response(NotificationEvent e, NotificationTemplate t, Set<NotificationChannel> on) {
        List<NotificationChannel> channels = t == null ? DEFAULT_CHANNELS
                : Arrays.stream(t.getChannels().split(",")).filter(c -> !c.isBlank()).map(c -> NotificationChannel.valueOf(c.trim())).toList();
        List<String> placeholders = new ArrayList<>(e.placeholders());
        placeholders.addAll(NotificationEvent.COMMON_PLACEHOLDERS);
        return new TemplateResponse(e, e.label(), e.description(), e.audience(), t == null || t.isEnabled(), channels,
                t == null ? e.defaultSubject() : t.getSubject(), t == null ? e.defaultBody() : t.getBody(), placeholders, t != null,
                channels.stream().filter(c -> !on.contains(c)).toList());
    }

    private static CommunicationSettingsResponse response(CommunicationSettings s, boolean saved) {
        return new CommunicationSettingsResponse(s.isInAppEnabled(), s.isEmailEnabled(), s.isSmsEnabled(), s.isWhatsappEnabled(), s.getSenderName(),
                s.getReplyToEmail(), s.getSmsSenderId(), s.getQuietFrom(), s.getQuietTo(), s.getDefaultLanguage(), saved);
    }
}
