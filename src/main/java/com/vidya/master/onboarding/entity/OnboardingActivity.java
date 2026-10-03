package com.vidya.master.onboarding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only immutable audit trail for onboarding state changes and operations.
 */
@Entity
@Table(name = "onboarding_activity")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "onboarding_id", nullable = false)
    private SchoolOnboarding onboarding;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "performed_by_type", nullable = false, length = 30)
    private PerformerType performedByType = PerformerType.SYSTEM;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OnboardingActivity(SchoolOnboarding onboarding,
                              String action,
                              String previousStatus,
                              String newStatus,
                              UUID performedBy,
                              PerformerType performedByType,
                              Map<String, Object> metadata) {
        this.onboarding = onboarding;
        this.action = action;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.performedBy = performedBy;
        this.performedByType = performedByType != null ? performedByType : PerformerType.SYSTEM;
        this.metadata = metadata;
    }
}
