package com.vidya.tenantdata.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** Which channels the school uses to reach people, and how it signs its messages (one row). */
@Entity
@Table(name = "communication_settings")
@Getter
@Setter
@NoArgsConstructor
public class CommunicationSettings {

    /** Always 1: the school has one row. */
    @Id
    private Short id = 1;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @Column(name = "whatsapp_enabled", nullable = false)
    private boolean whatsappEnabled;

    @Column(name = "sender_name", length = 100)
    private String senderName;

    @Column(name = "reply_to_email", length = 255)
    private String replyToEmail;

    @Column(name = "sms_sender_id", length = 11)
    private String smsSenderId;

    @Column(name = "quiet_from")
    private LocalTime quietFrom;

    @Column(name = "quiet_to")
    private LocalTime quietTo;

    @Column(name = "default_language", nullable = false, length = 10)
    private String defaultLanguage = "en";

    @Column(name = "updated_by")
    private UUID updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
