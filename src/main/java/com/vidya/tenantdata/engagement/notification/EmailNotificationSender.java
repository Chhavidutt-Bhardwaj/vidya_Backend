package com.vidya.tenantdata.engagement.notification;

import com.vidya.tenantdata.engagement.entity.NotificationOutbox;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Plain-text email through Spring Mail ({@code spring.mail.*}). Without a mail server, email is SKIPPED. */
@Component
public class EmailNotificationSender implements NotificationSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailNotificationSender(ObjectProvider<JavaMailSender> mailSender,
                                   @Value("${vidya.notifications.email.from:}") String from,
                                   @Value("${spring.mail.username:}") String mailUser) {
        this.mailSender = mailSender;
        this.from = from != null && !from.isBlank() ? from : mailUser;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationOutbox message, DeliveryContext context) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || from == null || from.isBlank()) {
            throw new ChannelUnavailableException("Email is not configured on this server (spring.mail.host / vidya.notifications.email.from)");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(context.senderName() == null ? from : context.senderName() + " <" + from + ">");
        if (context.replyToEmail() != null) {
            mail.setReplyTo(context.replyToEmail());
        }
        mail.setTo(message.getAddress());
        mail.setSubject(message.getSubject());
        mail.setText(message.getBody());
        sender.send(mail);
    }
}
