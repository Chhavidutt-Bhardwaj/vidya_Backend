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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores step-specific structured JSONB payloads for an onboarding process.
 */
@Entity
@Table(name = "onboarding_step_data")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingStepData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "onboarding_id", nullable = false)
    private SchoolOnboarding onboarding;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_name", nullable = false, length = 50)
    private OnboardingStep stepName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> stepData = new HashMap<>();

    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OnboardingStepData(SchoolOnboarding onboarding,
                              OnboardingStep stepName,
                              Map<String, Object> stepData,
                              boolean completed) {
        this.onboarding = onboarding;
        this.stepName = stepName;
        this.stepData = stepData != null ? stepData : new HashMap<>();
        this.completed = completed;
        if (completed) {
            this.completedAt = Instant.now();
        }
    }
}
