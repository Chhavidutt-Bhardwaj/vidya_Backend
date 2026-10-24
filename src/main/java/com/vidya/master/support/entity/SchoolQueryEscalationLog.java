package com.vidya.master.support.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log record capturing an escalation step between levels:
 * COMMUNITY_RM &rarr; HUB_MANAGER &rarr; PLATFORM_ADMIN.
 */
@Entity
@Table(name = "school_query_escalation_log")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolQueryEscalationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "query_id", nullable = false)
    private SchoolSupportQuery query;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_level", nullable = false, length = 30)
    private EscalationLevel fromLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_level", nullable = false, length = 30)
    private EscalationLevel toLevel;

    @Column(name = "escalated_by_user_id")
    private UUID escalatedByUserId;

    @Column(name = "escalated_by_name", length = 200)
    private String escalatedByName;

    @Column(name = "escalated_by_role", length = 50)
    private String escalatedByRole;

    @Column(name = "escalated_to_user_id")
    private UUID escalatedToUserId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public SchoolQueryEscalationLog(SchoolSupportQuery query,
                                   EscalationLevel fromLevel,
                                   EscalationLevel toLevel,
                                   UUID escalatedByUserId,
                                   String escalatedByName,
                                   String escalatedByRole,
                                   UUID escalatedToUserId,
                                   String reason,
                                   String notes) {
        this.query = query;
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
        this.escalatedByUserId = escalatedByUserId;
        this.escalatedByName = escalatedByName;
        this.escalatedByRole = escalatedByRole;
        this.escalatedToUserId = escalatedToUserId;
        this.reason = reason;
        this.notes = notes;
        this.occurredAt = Instant.now();
    }
}
