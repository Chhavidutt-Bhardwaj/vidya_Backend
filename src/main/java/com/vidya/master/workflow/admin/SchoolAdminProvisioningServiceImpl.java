package com.vidya.master.workflow.admin;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.entity.OnboardingStepData;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.repository.OnboardingStepDataRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminRequest;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminResponse;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolAdminProvisioningServiceImpl implements SchoolAdminProvisioningService {

    private final SchoolRepository schoolRepository;
    private final SchoolOnboardingRepository onboardingRepository;
    private final OnboardingStepDataRepository stepDataRepository;
    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public InitialSchoolAdminResponse provisionInitialAdmin(UUID schoolId, UUID onboardingId, InitialSchoolAdminRequest request) {
        log.info("Provisioning initial school administrator for schoolId: {}, onboardingId: {}", schoolId, onboardingId);

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        SchoolOnboarding onboarding = null;
        if (onboardingId != null) {
            onboarding = onboardingRepository.findById(onboardingId).orElse(null);
        }

        // 1. Resolve Admin Contact Info from Request, Onboarding Step Data, or School details
        AdminContactInfo contactInfo = resolveContactInfo(school, onboarding, request);

        // 2. Resolve Role
        PlatformRole role = (request != null && request.getRole() != null)
                ? request.getRole()
                : PlatformRole.SCHOOL_OWNER;

        // 3. Check if user already exists
        Optional<PlatformUser> existingUserOpt = userRepository.findByEmailIgnoreCase(contactInfo.email);
        if (existingUserOpt.isPresent()) {
            PlatformUser existing = existingUserOpt.get();
            log.info("User already exists for email: {}. Linking to school: {}", existing.getEmail(), schoolId);
            existing.setSchoolId(schoolId);
            existing.setPlatformRole(role);
            if (existing.getStatus() != UserStatus.ACTIVE) {
                existing.setStatus(UserStatus.ACTIVE);
            }
            PlatformUser updated = userRepository.save(existing);
            return mapToResponse(updated, null);
        }

        // 4. Generate Credentials & Create New Platform User
        String rawPassword = (request != null && request.getPassword() != null && !request.getPassword().isBlank())
                ? request.getPassword()
                : "Vidya@" + CodeGenerator.generate(8);

        String userCode = CodeGenerator.userCode();
        String passwordHash = passwordEncoder.encode(rawPassword);

        PlatformUser newUser = new PlatformUser(
                userCode,
                contactInfo.email,
                passwordHash,
                contactInfo.firstName,
                contactInfo.lastName,
                contactInfo.phone,
                role,
                schoolId
        );

        PlatformUser savedUser = userRepository.save(newUser);
        log.info("Created initial school admin user: {} ({}) with role: {}",
                savedUser.getUserCode(), savedUser.getEmail(), savedUser.getPlatformRole());

        return mapToResponse(savedUser, rawPassword);
    }

    private AdminContactInfo resolveContactInfo(School school, SchoolOnboarding onboarding, InitialSchoolAdminRequest request) {
        String email = request != null ? request.getEmail() : null;
        String firstName = request != null ? request.getFirstName() : null;
        String lastName = request != null ? request.getLastName() : null;
        String phone = request != null ? request.getPhone() : null;

        if (onboarding != null) {
            // Check CONTACT_INFORMATION step
            Optional<OnboardingStepData> contactStep = stepDataRepository.findByOnboardingIdAndStepName(
                    onboarding.getId(), OnboardingStep.CONTACT_INFORMATION);
            if (contactStep.isPresent()) {
                Map<String, Object> data = contactStep.get().getStepData();
                if (email == null || email.isBlank()) email = getStringValue(data, "email", "contactEmail", "adminEmail");
                if (firstName == null || firstName.isBlank()) firstName = getStringValue(data, "firstName", "contactPerson", "name");
                if (lastName == null || lastName.isBlank()) lastName = getStringValue(data, "lastName");
                if (phone == null || phone.isBlank()) phone = getStringValue(data, "phone", "contactPhone");
            }

            // Check BASIC_INFORMATION step
            if (email == null || email.isBlank()) {
                Optional<OnboardingStepData> basicStep = stepDataRepository.findByOnboardingIdAndStepName(
                        onboarding.getId(), OnboardingStep.BASIC_INFORMATION);
                if (basicStep.isPresent()) {
                    Map<String, Object> data = basicStep.get().getStepData();
                    if (email == null || email.isBlank()) email = getStringValue(data, "email", "adminEmail");
                    if (firstName == null || firstName.isBlank()) firstName = getStringValue(data, "contactPerson", "principalName");
                    if (phone == null || phone.isBlank()) phone = getStringValue(data, "phone");
                }
            }
        }

        // Fallbacks from School entity
        if (email == null || email.isBlank()) {
            email = school.getEmail();
        }
        if (firstName == null || firstName.isBlank()) {
            firstName = "School";
            lastName = "Administrator";
        }
        if (lastName == null) {
            lastName = "";
        }
        if (phone == null || phone.isBlank()) {
            phone = school.getPhone();
        }

        return new AdminContactInfo(email.trim().toLowerCase(), firstName.trim(), lastName.trim(), phone != null ? phone.trim() : null);
    }

    private String getStringValue(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            Object val = data.get(key);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }
        return null;
    }

    private InitialSchoolAdminResponse mapToResponse(PlatformUser user, String temporaryPassword) {
        return InitialSchoolAdminResponse.builder()
                .userId(user.getId())
                .userCode(user.getUserCode())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getPlatformRole())
                .schoolId(user.getSchoolId())
                .status(user.getStatus())
                .temporaryPassword(temporaryPassword)
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt() : Instant.now())
                .build();
    }

    private record AdminContactInfo(String email, String firstName, String lastName, String phone) {}
}
