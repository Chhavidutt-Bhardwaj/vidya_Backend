package com.vidya.master.onboarding.service;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.onboarding.dto.InitiateOnboardingRequest;
import com.vidya.master.onboarding.dto.OnboardingDetailResponse;
import com.vidya.master.onboarding.dto.OnboardingResponse;
import com.vidya.master.onboarding.dto.RejectOnboardingRequest;
import com.vidya.master.onboarding.dto.SaveStepDataRequest;
import com.vidya.master.onboarding.dto.VerifyOnboardingRequest;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.mapper.OnboardingMapper;
import com.vidya.master.onboarding.repository.OnboardingActivityRepository;
import com.vidya.master.onboarding.repository.OnboardingStepDataRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private SchoolOnboardingRepository schoolOnboardingRepository;

    @Mock
    private OnboardingStepDataRepository onboardingStepDataRepository;

    @Mock
    private OnboardingActivityRepository onboardingActivityRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Spy
    private OnboardingMapper onboardingMapper = new OnboardingMapper();

    @InjectMocks
    private OnboardingServiceImpl onboardingService;

    private School sampleSchool;
    private SchoolOnboarding sampleOnboarding;
    private UUID onboardingId;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        onboardingId = UUID.randomUUID();

        sampleSchool = new School(
                "SCH-12345678",
                "Vidya Model School",
                null, null, null, null,
                "contact@vidyamodel.edu.in",
                "+919876543210",
                null,
                SchoolStatus.DRAFT
        );
        sampleSchool.setId(schoolId);

        sampleOnboarding = new SchoolOnboarding(
                "ONB-12345678",
                sampleSchool,
                OnboardingSource.DIRECT,
                null,
                null
        );
        sampleOnboarding.setId(onboardingId);
    }

    @Test
    @DisplayName("initiateOnboarding should create draft school and onboarding record")
    void initiateOnboarding_Success() {
        InitiateOnboardingRequest request = new InitiateOnboardingRequest(
                OnboardingSource.DIRECT, null, null, "Vidya Model School", "contact@vidyamodel.edu.in", "+919876543210");

        when(schoolRepository.existsBySchoolCode(any())).thenReturn(false);
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> {
            School s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(schoolOnboardingRepository.findByOnboardingCode(any())).thenReturn(Optional.empty());
        when(schoolOnboardingRepository.save(any(SchoolOnboarding.class))).thenAnswer(inv -> {
            SchoolOnboarding o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        OnboardingResponse response = onboardingService.initiateOnboarding(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OnboardingStatus.DRAFT);
        assertThat(response.currentStep()).isEqualTo(OnboardingStep.BASIC_INFORMATION);
        verify(schoolRepository).save(any(School.class));
        verify(schoolOnboardingRepository).save(any(SchoolOnboarding.class));
        verify(onboardingActivityRepository).save(any());
    }

    @Test
    @DisplayName("saveStepData should persist step data and advance currentStep")
    void saveStepData_Success() {
        SaveStepDataRequest request = new SaveStepDataRequest(
                OnboardingStep.BASIC_INFORMATION,
                Map.of("schoolName", "Vidya Model School", "board", "CBSE"),
                true
        );

        when(schoolOnboardingRepository.findById(onboardingId)).thenReturn(Optional.of(sampleOnboarding));
        when(onboardingStepDataRepository.findByOnboardingIdAndStepName(onboardingId, OnboardingStep.BASIC_INFORMATION))
                .thenReturn(Optional.empty());
        when(schoolOnboardingRepository.save(any(SchoolOnboarding.class))).thenReturn(sampleOnboarding);

        OnboardingResponse response = onboardingService.saveStepData(onboardingId, request);

        assertThat(response).isNotNull();
        assertThat(sampleOnboarding.getCompletedSteps()).contains("BASIC_INFORMATION");
        assertThat(sampleOnboarding.getCurrentStep()).isEqualTo(OnboardingStep.CONTACT_INFORMATION);
        verify(onboardingStepDataRepository).save(any());
    }

    @Test
    @DisplayName("submitOnboarding should transition status to SUBMITTED")
    void submitOnboarding_Success() {
        when(schoolOnboardingRepository.findById(onboardingId)).thenReturn(Optional.of(sampleOnboarding));
        when(schoolOnboardingRepository.save(any(SchoolOnboarding.class))).thenReturn(sampleOnboarding);

        OnboardingResponse response = onboardingService.submitOnboarding(onboardingId);

        assertThat(response).isNotNull();
        assertThat(sampleOnboarding.getStatus()).isEqualTo(OnboardingStatus.SUBMITTED);
        assertThat(sampleOnboarding.getSubmittedAt()).isNotNull();
        assertThat(sampleSchool.getStatus()).isEqualTo(SchoolStatus.PENDING);
    }

    @Test
    @DisplayName("verifyOnboarding when approved should transition to VERIFIED")
    void verifyOnboarding_Success() {
        sampleOnboarding.setStatus(OnboardingStatus.SUBMITTED);
        VerifyOnboardingRequest request = new VerifyOnboardingRequest(true, "All documents valid");

        when(schoolOnboardingRepository.findById(onboardingId)).thenReturn(Optional.of(sampleOnboarding));
        when(schoolOnboardingRepository.save(any(SchoolOnboarding.class))).thenReturn(sampleOnboarding);

        OnboardingResponse response = onboardingService.verifyOnboarding(onboardingId, request);

        assertThat(response).isNotNull();
        assertThat(sampleOnboarding.getStatus()).isEqualTo(OnboardingStatus.VERIFIED);
        assertThat(sampleSchool.getStatus()).isEqualTo(SchoolStatus.VERIFIED);
    }

    @Test
    @DisplayName("approveOnboarding should transition to APPROVED")
    void approveOnboarding_Success() {
        sampleOnboarding.setStatus(OnboardingStatus.VERIFIED);

        when(schoolOnboardingRepository.findById(onboardingId)).thenReturn(Optional.of(sampleOnboarding));
        when(schoolOnboardingRepository.save(any(SchoolOnboarding.class))).thenReturn(sampleOnboarding);

        OnboardingResponse response = onboardingService.approveOnboarding(onboardingId, "Approved by admin");

        assertThat(response).isNotNull();
        assertThat(sampleOnboarding.getStatus()).isEqualTo(OnboardingStatus.APPROVED);
    }

    @Test
    @DisplayName("rejectOnboarding should transition to REJECTED and set reason")
    void rejectOnboarding_Success() {
        RejectOnboardingRequest request = new RejectOnboardingRequest("Invalid accreditation certificates");

        when(schoolOnboardingRepository.findById(onboardingId)).thenReturn(Optional.of(sampleOnboarding));
        when(schoolOnboardingRepository.save(any(SchoolOnboarding.class))).thenReturn(sampleOnboarding);

        OnboardingResponse response = onboardingService.rejectOnboarding(onboardingId, request);

        assertThat(response).isNotNull();
        assertThat(sampleOnboarding.getStatus()).isEqualTo(OnboardingStatus.REJECTED);
        assertThat(sampleOnboarding.getRejectionReason()).isEqualTo("Invalid accreditation certificates");
    }

    @Test
    @DisplayName("getOnboardingDetail should return summary, step data, and activities")
    void getOnboardingDetail_Success() {
        when(schoolOnboardingRepository.findById(onboardingId)).thenReturn(Optional.of(sampleOnboarding));

        OnboardingDetailResponse detail = onboardingService.getOnboardingDetail(onboardingId);

        assertThat(detail).isNotNull();
        assertThat(detail.summary()).isNotNull();
        assertThat(detail.summary().onboardingCode()).isEqualTo("ONB-12345678");
    }
}
