package com.vidya.master.workflow.admin;

import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.entity.OnboardingStepData;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.repository.OnboardingStepDataRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminRequest;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminResponse;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolAdminProvisioningServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SchoolOnboardingRepository onboardingRepository;

    @Mock
    private OnboardingStepDataRepository stepDataRepository;

    @Mock
    private PlatformUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SchoolAdminProvisioningServiceImpl adminProvisioningService;

    private UUID schoolId;
    private UUID onboardingId;
    private School school;
    private SchoolOnboarding onboarding;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        onboardingId = UUID.randomUUID();

        school = new School(
                "SCH-12345678",
                "Greenwood High",
                "Greenwood Trust",
                "REG-123",
                null,
                null,
                "admin@greenwood.edu.in",
                "+919876543210",
                "https://greenwood.edu.in",
                SchoolStatus.VERIFIED
        );
        school.setId(schoolId);

        onboarding = new SchoolOnboarding("ONB-12345678", school, null, null, null);
        onboarding.setId(onboardingId);
    }

    @Test
    @DisplayName("Should provision new school owner from request payload")
    void provisionInitialAdmin_NewUser_FromRequest() {
        InitialSchoolAdminRequest request = InitialSchoolAdminRequest.builder()
                .email("principal@greenwood.edu.in")
                .firstName("Suresh")
                .lastName("Raina")
                .phone("+919988776655")
                .role(PlatformRole.SCHOOL_OWNER)
                .password("CustomPass@123")
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(userRepository.findByEmailIgnoreCase("principal@greenwood.edu.in")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("CustomPass@123")).thenReturn("hashed_custom_pass");
        when(userRepository.save(any())).thenAnswer(invocation -> {
            PlatformUser u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        InitialSchoolAdminResponse response = adminProvisioningService.provisionInitialAdmin(schoolId, onboardingId, request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("principal@greenwood.edu.in");
        assertThat(response.getFullName()).isEqualTo("Suresh Raina");
        assertThat(response.getRole()).isEqualTo(PlatformRole.SCHOOL_OWNER);
        assertThat(response.getSchoolId()).isEqualTo(schoolId);
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getTemporaryPassword()).isEqualTo("CustomPass@123");

        verify(userRepository).save(any(PlatformUser.class));
    }

    @Test
    @DisplayName("Should resolve admin contact from onboarding CONTACT_INFORMATION step data if request is empty")
    void provisionInitialAdmin_FromStepData() {
        OnboardingStepData contactStep = new OnboardingStepData(
                onboarding,
                OnboardingStep.CONTACT_INFORMATION,
                Map.of("email", "contact@greenwood.edu.in",
                        "firstName", "Amit",
                        "lastName", "Verma",
                        "phone", "+919123456780"),
                true
        );

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(stepDataRepository.findByOnboardingIdAndStepName(onboardingId, OnboardingStep.CONTACT_INFORMATION))
                .thenReturn(Optional.of(contactStep));
        when(userRepository.findByEmailIgnoreCase("contact@greenwood.edu.in")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed_auto_pass");
        when(userRepository.save(any())).thenAnswer(invocation -> {
            PlatformUser u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        InitialSchoolAdminResponse response = adminProvisioningService.provisionInitialAdmin(schoolId, onboardingId, null);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("contact@greenwood.edu.in");
        assertThat(response.getFullName()).isEqualTo("Amit Verma");
        assertThat(response.getRole()).isEqualTo(PlatformRole.SCHOOL_OWNER);
        assertThat(response.getSchoolId()).isEqualTo(schoolId);
        assertThat(response.getTemporaryPassword()).startsWith("Vidya@");
    }

    @Test
    @DisplayName("Should link existing platform user to school idempotently")
    void provisionInitialAdmin_ExistingUser_LinksSchool() {
        PlatformUser existingUser = new PlatformUser(
                "USR-EXISTING",
                "admin@greenwood.edu.in",
                "existing_hash",
                "Existing",
                "User",
                "+919876543210",
                PlatformRole.COMMUNITY_MANAGER
        );
        existingUser.setId(UUID.randomUUID());

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(userRepository.findByEmailIgnoreCase("admin@greenwood.edu.in")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InitialSchoolAdminResponse response = adminProvisioningService.provisionInitialAdmin(schoolId, onboardingId, null);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("admin@greenwood.edu.in");
        assertThat(response.getRole()).isEqualTo(PlatformRole.SCHOOL_OWNER);
        assertThat(response.getSchoolId()).isEqualTo(schoolId);
        assertThat(existingUser.getSchoolId()).isEqualTo(schoolId);
    }
}
