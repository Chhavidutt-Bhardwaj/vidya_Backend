package com.vidya.master.onboarding.entity;

import com.vidya.common.audit.AuditableEntity;
import com.vidya.master.school.entity.School;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Primary state-machine entity tracking a school's onboarding journey.
 */
@Entity
@Table(name = "school_onboarding")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolOnboarding extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "onboarding_code", nullable = false, unique = true, length = 50, updatable = false)
    private String onboardingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private OnboardingSource source = OnboardingSource.DIRECT;

    @Column(name = "community_id")
    private UUID communityId;

    @Column(name = "community_member_id")
    private UUID communityMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 50)
    private OnboardingStep currentStep = OnboardingStep.BASIC_INFORMATION;

    @Column(name = "completed_steps", columnDefinition = "text[]")
    private List<String> completedSteps = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OnboardingStatus status = OnboardingStatus.DRAFT;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "onboarding", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OnboardingStepData> stepData = new HashSet<>();

    @OneToMany(mappedBy = "onboarding", cascade = CascadeType.ALL)
    private List<OnboardingActivity> activities = new ArrayList<>();

    public SchoolOnboarding(String onboardingCode,
                            School school,
                            OnboardingSource source,
                            UUID communityId,
                            UUID communityMemberId) {
        this.onboardingCode = onboardingCode;
        this.school = school;
        this.source = source != null ? source : OnboardingSource.DIRECT;
        this.communityId = communityId;
        this.communityMemberId = communityMemberId;
        this.currentStep = OnboardingStep.BASIC_INFORMATION;
        this.status = OnboardingStatus.DRAFT;
        this.startedAt = Instant.now();
    }

    public void addCompletedStep(String step) {
        if (completedSteps == null) {
            completedSteps = new ArrayList<>();
        }
        if (!completedSteps.contains(step)) {
            completedSteps.add(step);
        }
    }

    public void addActivity(OnboardingActivity activity) {
        activities.add(activity);
        activity.setOnboarding(this);
    }
}
