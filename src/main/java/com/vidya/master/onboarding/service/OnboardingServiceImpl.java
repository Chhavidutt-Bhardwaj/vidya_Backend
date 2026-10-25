package com.vidya.master.onboarding.service;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.onboarding.dto.InitiateOnboardingRequest;
import com.vidya.master.onboarding.dto.OnboardingDetailResponse;
import com.vidya.master.onboarding.dto.OnboardingResponse;
import com.vidya.master.onboarding.dto.RejectOnboardingRequest;
import com.vidya.master.onboarding.dto.SaveStepDataRequest;
import com.vidya.master.onboarding.dto.VerifyOnboardingRequest;
import com.vidya.master.onboarding.entity.OnboardingActivity;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.entity.OnboardingStepData;
import com.vidya.master.onboarding.entity.PerformerType;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.mapper.OnboardingMapper;
import com.vidya.master.onboarding.repository.OnboardingActivityRepository;
import com.vidya.master.onboarding.repository.OnboardingStepDataRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.community.repository.CommunityRepository;
import com.vidya.master.school.entity.BoardAffiliationLevel;
import com.vidya.master.school.entity.BoardAffiliationStatus;
import com.vidya.master.school.entity.BoardAffiliationType;
import com.vidya.master.school.entity.SchoolAddress;
import com.vidya.master.school.entity.SchoolAddressType;
import com.vidya.master.school.entity.SchoolBoardAffiliation;
import com.vidya.master.school.entity.SchoolContactDetail;
import com.vidya.master.school.entity.SchoolContactType;
import com.vidya.master.school.repository.SchoolAddressRepository;
import com.vidya.master.school.repository.SchoolBoardAffiliationRepository;
import com.vidya.master.school.repository.SchoolContactDetailRepository;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link OnboardingService} orchestrating the multi-step
 * school onboarding state machine and audit trail.
 */
@Service
@Slf4j
@Transactional(transactionManager = "masterTransactionManager")
public class OnboardingServiceImpl implements OnboardingService {

    private final SchoolOnboardingRepository schoolOnboardingRepository;
    private final OnboardingStepDataRepository onboardingStepDataRepository;
    private final OnboardingActivityRepository onboardingActivityRepository;
    private final SchoolRepository schoolRepository;
    private final CommunityRepository communityRepository;
    private final SchoolContactDetailRepository contactDetailRepository;
    private final SchoolAddressRepository addressRepository;
    private final SchoolBoardAffiliationRepository boardAffiliationRepository;
    private final OnboardingMapper onboardingMapper;

    @Autowired
    public OnboardingServiceImpl(SchoolOnboardingRepository schoolOnboardingRepository,
                                 OnboardingStepDataRepository onboardingStepDataRepository,
                                 OnboardingActivityRepository onboardingActivityRepository,
                                 SchoolRepository schoolRepository,
                                 CommunityRepository communityRepository,
                                 SchoolContactDetailRepository contactDetailRepository,
                                 SchoolAddressRepository addressRepository,
                                 SchoolBoardAffiliationRepository boardAffiliationRepository,
                                 OnboardingMapper onboardingMapper) {
        this.schoolOnboardingRepository = schoolOnboardingRepository;
        this.onboardingStepDataRepository = onboardingStepDataRepository;
        this.onboardingActivityRepository = onboardingActivityRepository;
        this.schoolRepository = schoolRepository;
        this.communityRepository = communityRepository;
        this.contactDetailRepository = contactDetailRepository;
        this.addressRepository = addressRepository;
        this.boardAffiliationRepository = boardAffiliationRepository;
        this.onboardingMapper = onboardingMapper;
    }

    public OnboardingServiceImpl(SchoolOnboardingRepository schoolOnboardingRepository,
                                 OnboardingStepDataRepository onboardingStepDataRepository,
                                 OnboardingActivityRepository onboardingActivityRepository,
                                 SchoolRepository schoolRepository,
                                 OnboardingMapper onboardingMapper) {
        this(schoolOnboardingRepository, onboardingStepDataRepository, onboardingActivityRepository,
             schoolRepository, null, null, null, null, onboardingMapper);
    }

    @Override
    public OnboardingResponse initiateOnboarding(InitiateOnboardingRequest request) {
        log.info("Initiating onboarding for school: [{}] via [{}]", request.schoolName(), request.source());

        String schoolCode = generateUniqueSchoolCode();
        School school = new School(
                schoolCode,
                request.schoolName().trim(),
                null,
                null,
                request.primaryBoard(),
                null,
                request.email().trim().toLowerCase(),
                request.phone(),
                null,
                SchoolStatus.DRAFT
        );

        if (request.communityId() != null) {
            communityRepository.findById(request.communityId())
                    .ifPresent(c -> {
                        school.setOnboardingCommunity(c);
                        school.setOnboardedAt(Instant.now());
                    });
        }

        School savedSchool = schoolRepository.save(school);

        // Record initial primary contact if contact person name provided
        if (request.contactPersonName() != null && !request.contactPersonName().isBlank()) {
            SchoolContactDetail primaryContact = new SchoolContactDetail(
                    savedSchool,
                    null,
                    SchoolContactType.PRINCIPAL,
                    request.contactPersonName().trim(),
                    "Inbound In-Charge",
                    request.email().trim().toLowerCase(),
                    request.phone() != null ? request.phone().trim() : "",
                    null,
                    true
            );
            contactDetailRepository.save(primaryContact);
        }

        // Record initial address if city or state provided
        if ((request.city() != null && !request.city().isBlank()) || (request.state() != null && !request.state().isBlank())) {
            SchoolAddress initialAddress = new SchoolAddress(
                    savedSchool,
                    null,
                    SchoolAddressType.CAMPUS_PRIMARY,
                    "Campus Primary Location",
                    null,
                    request.city() != null ? request.city().trim() : "Pending",
                    request.state() != null ? request.state().trim() : "Pending",
                    "000000",
                    "India",
                    null,
                    null,
                    null,
                    true
            );
            addressRepository.save(initialAddress);
        }

        // Record initial board affiliation if primary board provided
        if (request.primaryBoard() != null) {
            BoardAffiliationType bType;
            try {
                bType = BoardAffiliationType.valueOf(request.primaryBoard().name());
            } catch (Exception e) {
                bType = BoardAffiliationType.OTHER;
            }
            SchoolBoardAffiliation initialAffiliation = new SchoolBoardAffiliation(
                    savedSchool,
                    bType,
                    request.primaryBoard().name() + " Affiliation",
                    "PENDING-" + CodeGenerator.generate(6),
                    BoardAffiliationLevel.ALL_LEVELS,
                    null,
                    null,
                    true,
                    null,
                    BoardAffiliationStatus.PROVISIONAL
            );
            boardAffiliationRepository.save(initialAffiliation);
        }

        String onboardingCode = generateUniqueOnboardingCode();
        SchoolOnboarding onboarding = new SchoolOnboarding(
                onboardingCode,
                savedSchool,
                request.source() != null ? request.source() : OnboardingSource.DIRECT,
                request.communityId(),
                request.communityMemberId()
        );

        // Inbound requests from Community, Referral, or Website are automatically submitted for review
        if (request.source() == OnboardingSource.COMMUNITY ||
            request.source() == OnboardingSource.REFERRAL ||
            request.source() == OnboardingSource.WEBSITE) {
            onboarding.setStatus(OnboardingStatus.SUBMITTED);
            onboarding.setSubmittedAt(Instant.now());
        }

        SchoolOnboarding savedOnboarding = schoolOnboardingRepository.save(onboarding);

        recordActivity(savedOnboarding, "ONBOARDING_INITIATED", null, savedOnboarding.getStatus().name(),
                request.communityMemberId(),
                request.communityMemberId() != null ? PerformerType.COMMUNITY_MEMBER : PerformerType.SYSTEM,
                Map.of("schoolName", request.schoolName(), "source", savedOnboarding.getSource().name()));

        log.info("Onboarding initiated with ID: [{}] and code: [{}] in status: [{}]",
                savedOnboarding.getId(), savedOnboarding.getOnboardingCode(), savedOnboarding.getStatus());
        return onboardingMapper.toResponse(savedOnboarding);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public OnboardingResponse getOnboardingById(UUID id) {
        SchoolOnboarding onboarding = findOnboardingOrThrow(id);
        return onboardingMapper.toResponse(onboarding);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public OnboardingResponse getOnboardingByCode(String code) {
        SchoolOnboarding onboarding = schoolOnboardingRepository.findByOnboardingCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolOnboarding", code));
        return onboardingMapper.toResponse(onboarding);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public OnboardingDetailResponse getOnboardingDetail(UUID id) {
        SchoolOnboarding onboarding = findOnboardingOrThrow(id);
        return onboardingMapper.toDetailResponse(onboarding);
    }

    @Override
    public OnboardingResponse saveStepData(UUID id, SaveStepDataRequest request) {
        log.info("Saving step [{}] data for onboarding [{}]", request.stepName(), id);
        SchoolOnboarding onboarding = findOnboardingOrThrow(id);

        if (onboarding.getStatus() == OnboardingStatus.REJECTED || onboarding.getStatus() == OnboardingStatus.CANCELLED) {
            throw new InvalidStateTransitionException("SchoolOnboarding", onboarding.getStatus().name(), "SAVE_STEP");
        }

        Optional<OnboardingStepData> existing = onboardingStepDataRepository
                .findByOnboardingIdAndStepName(id, request.stepName());

        OnboardingStepData stepData;
        if (existing.isPresent()) {
            stepData = existing.get();
            stepData.setStepData(request.data());
            if (request.markCompleted()) {
                stepData.setCompleted(true);
                stepData.setCompletedAt(Instant.now());
            }
        } else {
            stepData = new OnboardingStepData(
                    onboarding,
                    request.stepName(),
                    request.data(),
                    request.markCompleted()
            );
            onboarding.getStepData().add(stepData);
        }

        onboardingStepDataRepository.save(stepData);

        if (request.markCompleted()) {
            onboarding.addCompletedStep(request.stepName().name());
            advanceCurrentStep(onboarding, request.stepName());
        }

        SchoolOnboarding updated = schoolOnboardingRepository.save(onboarding);

        recordActivity(updated, "STEP_SAVED", null, null, null, PerformerType.SYSTEM,
                Map.of("stepName", request.stepName().name(), "completed", request.markCompleted()));

        return onboardingMapper.toResponse(updated);
    }

    @Override
    public OnboardingResponse submitOnboarding(UUID id) {
        log.info("Submitting onboarding application: [{}]", id);
        SchoolOnboarding onboarding = findOnboardingOrThrow(id);

        if (onboarding.getStatus() != OnboardingStatus.DRAFT) {
            throw new InvalidStateTransitionException("SchoolOnboarding", onboarding.getStatus().name(), "SUBMITTED");
        }

        String prevStatus = onboarding.getStatus().name();
        onboarding.setStatus(OnboardingStatus.SUBMITTED);
        onboarding.setSubmittedAt(Instant.now());

        if (onboarding.getSchool() != null) {
            onboarding.getSchool().setStatus(SchoolStatus.PENDING);
            schoolRepository.save(onboarding.getSchool());
        }

        SchoolOnboarding updated = schoolOnboardingRepository.save(onboarding);

        recordActivity(updated, "ONBOARDING_SUBMITTED", prevStatus, OnboardingStatus.SUBMITTED.name(),
                null, PerformerType.SYSTEM, Map.of());

        return onboardingMapper.toResponse(updated);
    }

    @Override
    public OnboardingResponse verifyOnboarding(UUID id, VerifyOnboardingRequest request) {
        log.info("Verifying onboarding [{}] - verified: {}", id, request.verified());
        SchoolOnboarding onboarding = findOnboardingOrThrow(id);

        if (onboarding.getStatus() != OnboardingStatus.SUBMITTED && onboarding.getStatus() != OnboardingStatus.VERIFICATION_PENDING) {
            throw new InvalidStateTransitionException("SchoolOnboarding", onboarding.getStatus().name(), "VERIFIED");
        }

        String prevStatus = onboarding.getStatus().name();
        OnboardingStatus newStatus = request.verified() ? OnboardingStatus.VERIFIED : OnboardingStatus.VERIFICATION_PENDING;
        onboarding.setStatus(newStatus);

        if (request.verified() && onboarding.getSchool() != null) {
            onboarding.getSchool().setStatus(SchoolStatus.VERIFIED);
            schoolRepository.save(onboarding.getSchool());
        }

        SchoolOnboarding updated = schoolOnboardingRepository.save(onboarding);

        Map<String, Object> meta = new HashMap<>();
        if (request.notes() != null) {
            meta.put("notes", request.notes());
        }
        recordActivity(updated, "ONBOARDING_VERIFIED", prevStatus, newStatus.name(), null, PerformerType.PLATFORM_USER, meta);

        return onboardingMapper.toResponse(updated);
    }

    @Override
    public OnboardingResponse approveOnboarding(UUID id, String notes) {
        log.info("Approving onboarding application: [{}]", id);
        SchoolOnboarding onboarding = findOnboardingOrThrow(id);

        if (onboarding.getStatus() != OnboardingStatus.VERIFIED &&
                onboarding.getStatus() != OnboardingStatus.PLAN_SELECTED &&
                onboarding.getStatus() != OnboardingStatus.PAYMENT_COMPLETED) {
            throw new InvalidStateTransitionException("SchoolOnboarding", onboarding.getStatus().name(), "APPROVED");
        }

        String prevStatus = onboarding.getStatus().name();
        onboarding.setStatus(OnboardingStatus.APPROVED);

        SchoolOnboarding updated = schoolOnboardingRepository.save(onboarding);

        Map<String, Object> meta = new HashMap<>();
        if (notes != null) {
            meta.put("notes", notes);
        }
        recordActivity(updated, "ONBOARDING_APPROVED", prevStatus, OnboardingStatus.APPROVED.name(), null, PerformerType.PLATFORM_USER, meta);

        return onboardingMapper.toResponse(updated);
    }

    @Override
    public OnboardingResponse rejectOnboarding(UUID id, RejectOnboardingRequest request) {
        log.info("Rejecting onboarding [{}] reason: [{}]", id, request.rejectionReason());
        SchoolOnboarding onboarding = findOnboardingOrThrow(id);

        String prevStatus = onboarding.getStatus().name();
        onboarding.setStatus(OnboardingStatus.REJECTED);
        onboarding.setRejectionReason(request.rejectionReason());

        SchoolOnboarding updated = schoolOnboardingRepository.save(onboarding);

        recordActivity(updated, "ONBOARDING_REJECTED", prevStatus, OnboardingStatus.REJECTED.name(),
                null, PerformerType.PLATFORM_USER, Map.of("reason", request.rejectionReason()));

        return onboardingMapper.toResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Page<OnboardingResponse> getAllOnboardings(Pageable pageable, OnboardingStatus status, OnboardingSource source, UUID communityId) {
        Page<SchoolOnboarding> page = schoolOnboardingRepository.findWithFilters(status, source, communityId, pageable);
        return page.map(onboardingMapper::toResponse);
    }

    private SchoolOnboarding findOnboardingOrThrow(UUID id) {
        return schoolOnboardingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolOnboarding", id));
    }

    private void advanceCurrentStep(SchoolOnboarding onboarding, OnboardingStep completedStep) {
        OnboardingStep[] steps = OnboardingStep.values();
        int nextOrdinal = completedStep.ordinal() + 1;
        if (nextOrdinal < steps.length) {
            onboarding.setCurrentStep(steps[nextOrdinal]);
        }
    }

    private void recordActivity(SchoolOnboarding onboarding,
                                String action,
                                String previousStatus,
                                String newStatus,
                                UUID performedBy,
                                PerformerType performedByType,
                                Map<String, Object> metadata) {
        OnboardingActivity activity = new OnboardingActivity(
                onboarding,
                action,
                previousStatus,
                newStatus,
                performedBy,
                performedByType,
                metadata
        );
        onboardingActivityRepository.save(activity);
    }

    private String generateUniqueSchoolCode() {
        String code;
        int attempts = 0;
        do {
            code = CodeGenerator.schoolCode();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate unique school code after 10 attempts");
            }
        } while (schoolRepository.existsBySchoolCode(code));
        return code;
    }

    private String generateUniqueOnboardingCode() {
        String code;
        int attempts = 0;
        do {
            code = CodeGenerator.onboardingCode();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate unique onboarding code after 10 attempts");
            }
        } while (schoolOnboardingRepository.findByOnboardingCode(code).isPresent());
        return code;
    }
}
