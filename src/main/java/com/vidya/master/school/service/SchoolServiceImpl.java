package com.vidya.master.school.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.school.dto.CreateBranchRequest;
import com.vidya.master.school.dto.CreateSchoolRequest;
import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolResponse;
import com.vidya.master.school.dto.UpdateBranchRequest;
import com.vidya.master.school.dto.UpdateSchoolRequest;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolBranch;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.mapper.SchoolMapper;
import com.vidya.master.school.repository.SchoolBranchRepository;
import com.vidya.master.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of {@link SchoolService} managing schools and multi-branch campuses
 * in the Master Database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "masterTransactionManager")
public class SchoolServiceImpl implements SchoolService {

    private final SchoolRepository schoolRepository;
    private final SchoolBranchRepository schoolBranchRepository;
    private final SchoolMapper schoolMapper;

    @Override
    public SchoolResponse createSchool(CreateSchoolRequest request) {
        log.info("Creating school: [{}] with email: [{}]", request.schoolName(), request.email());

        if (schoolRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("School", "email", request.email());
        }

        String schoolCode = generateUniqueSchoolCode();

        School school = new School(
                schoolCode,
                request.schoolName().trim(),
                request.legalName(),
                request.registrationNumber(),
                request.board(),
                request.schoolType(),
                request.email().trim().toLowerCase(),
                request.phone(),
                request.website(),
                SchoolStatus.DRAFT
        );

        School savedSchool = schoolRepository.save(school);

        SchoolBranch mainBranch = null;
        if (request.mainBranch() != null) {
            String branchCode = generateUniqueBranchCode();
            mainBranch = new SchoolBranch(
                    savedSchool,
                    branchCode,
                    request.mainBranch().branchName().trim(),
                    true,
                    request.mainBranch().addressLine1(),
                    request.mainBranch().addressLine2(),
                    request.mainBranch().city(),
                    request.mainBranch().state(),
                    request.mainBranch().pincode(),
                    request.mainBranch().country(),
                    request.mainBranch().contactEmail(),
                    request.mainBranch().contactPhone()
            );
            savedSchool.addBranch(mainBranch);
            mainBranch = schoolBranchRepository.save(mainBranch);
        }

        log.info("School created with ID: [{}] and code: [{}]", savedSchool.getId(), savedSchool.getSchoolCode());
        return schoolMapper.toResponse(savedSchool, mainBranch != null ? 1L : 0L, mainBranch);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SchoolResponse getSchoolById(UUID id) {
        School school = findSchoolOrThrow(id);
        long branchCount = schoolBranchRepository.countBySchoolId(id);
        SchoolBranch mainBranch = schoolBranchRepository.findBySchoolIdAndMainBranchTrue(id).orElse(null);
        return schoolMapper.toResponse(school, branchCount, mainBranch);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SchoolResponse getSchoolByCode(String code) {
        School school = schoolRepository.findBySchoolCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("School", code));
        long branchCount = schoolBranchRepository.countBySchoolId(school.getId());
        SchoolBranch mainBranch = schoolBranchRepository.findBySchoolIdAndMainBranchTrue(school.getId()).orElse(null);
        return schoolMapper.toResponse(school, branchCount, mainBranch);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SchoolResponse getSchoolByTenantId(UUID tenantId) {
        School school = schoolRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("School with tenantId", tenantId));
        long branchCount = schoolBranchRepository.countBySchoolId(school.getId());
        SchoolBranch mainBranch = schoolBranchRepository.findBySchoolIdAndMainBranchTrue(school.getId()).orElse(null);
        return schoolMapper.toResponse(school, branchCount, mainBranch);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Page<SchoolResponse> getAllSchools(Pageable pageable, SchoolStatus status, SchoolBoard board, String search) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<School> page = schoolRepository.findWithFilters(status, board, cleanSearch, pageable);
        return page.map(s -> {
            long count = schoolBranchRepository.countBySchoolId(s.getId());
            SchoolBranch mainBranch = schoolBranchRepository.findBySchoolIdAndMainBranchTrue(s.getId()).orElse(null);
            return schoolMapper.toResponse(s, count, mainBranch);
        });
    }

    @Override
    public SchoolResponse updateSchool(UUID id, UpdateSchoolRequest request) {
        log.info("Updating school: [{}]", id);
        School school = findSchoolOrThrow(id);

        if (schoolRepository.existsByEmailIgnoreCaseAndIdNot(request.email().trim(), id)) {
            throw new DuplicateResourceException("School", "email", request.email());
        }

        school.setSchoolName(request.schoolName().trim());
        school.setLegalName(request.legalName());
        school.setRegistrationNumber(request.registrationNumber());
        school.setBoard(request.board());
        school.setSchoolType(request.schoolType());
        school.setEmail(request.email().trim().toLowerCase());
        school.setPhone(request.phone());
        school.setWebsite(request.website());

        School updated = schoolRepository.save(school);
        long branchCount = schoolBranchRepository.countBySchoolId(id);
        SchoolBranch mainBranch = schoolBranchRepository.findBySchoolIdAndMainBranchTrue(id).orElse(null);
        return schoolMapper.toResponse(updated, branchCount, mainBranch);
    }

    @Override
    public SchoolResponse changeSchoolStatus(UUID id, SchoolStatus newStatus) {
        log.info("Changing school [{}] status to [{}]", id, newStatus);
        School school = findSchoolOrThrow(id);
        school.setStatus(newStatus);
        School updated = schoolRepository.save(school);
        long branchCount = schoolBranchRepository.countBySchoolId(id);
        SchoolBranch mainBranch = schoolBranchRepository.findBySchoolIdAndMainBranchTrue(id).orElse(null);
        return schoolMapper.toResponse(updated, branchCount, mainBranch);
    }

    @Override
    public SchoolResponse linkTenant(UUID schoolId, UUID tenantId) {
        log.info("Linking tenant [{}] to school [{}]", tenantId, schoolId);
        School school = findSchoolOrThrow(schoolId);
        school.setTenantId(tenantId);
        School updated = schoolRepository.save(school);
        long branchCount = schoolBranchRepository.countBySchoolId(schoolId);
        SchoolBranch mainBranch = schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId).orElse(null);
        return schoolMapper.toResponse(updated, branchCount, mainBranch);
    }

    @Override
    public SchoolBranchResponse addBranch(UUID schoolId, CreateBranchRequest request) {
        log.info("Adding branch [{}] to school [{}]", request.branchName(), schoolId);
        School school = findSchoolOrThrow(schoolId);

        if (request.isMainBranch()) {
            schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId).ifPresent(prev -> {
                prev.setMainBranch(false);
                schoolBranchRepository.save(prev);
            });
        }

        String branchCode = generateUniqueBranchCode();
        SchoolBranch branch = new SchoolBranch(
                school,
                branchCode,
                request.branchName().trim(),
                request.isMainBranch(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.state(),
                request.pincode(),
                request.country(),
                request.contactEmail(),
                request.contactPhone()
        );

        SchoolBranch saved = schoolBranchRepository.save(branch);
        log.info("Branch created with ID: [{}] and code: [{}]", saved.getId(), saved.getBranchCode());
        return schoolMapper.toBranchResponse(saved);
    }

    @Override
    public SchoolBranchResponse updateBranch(UUID schoolId, UUID branchId, UpdateBranchRequest request) {
        log.info("Updating branch [{}] for school [{}]", branchId, schoolId);
        findSchoolOrThrow(schoolId);

        SchoolBranch branch = schoolBranchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolBranch", branchId));

        if (!branch.getSchool().getId().equals(schoolId)) {
            throw new ResourceNotFoundException("SchoolBranch", branchId);
        }

        if (request.isMainBranch() && !branch.isMainBranch()) {
            schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId).ifPresent(prev -> {
                prev.setMainBranch(false);
                schoolBranchRepository.save(prev);
            });
        }

        branch.setBranchName(request.branchName().trim());
        branch.setMainBranch(request.isMainBranch());
        branch.setAddressLine1(request.addressLine1());
        branch.setAddressLine2(request.addressLine2());
        branch.setCity(request.city());
        branch.setState(request.state());
        branch.setPincode(request.pincode());
        if (request.country() != null && !request.country().isBlank()) {
            branch.setCountry(request.country());
        }
        branch.setContactEmail(request.contactEmail());
        branch.setContactPhone(request.contactPhone());
        if (request.status() != null) {
            branch.setStatus(request.status());
        }

        SchoolBranch updated = schoolBranchRepository.save(branch);
        return schoolMapper.toBranchResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Page<SchoolBranchResponse> getSchoolBranches(UUID schoolId, Pageable pageable) {
        findSchoolOrThrow(schoolId);
        Page<SchoolBranch> branches = schoolBranchRepository.findBySchoolId(schoolId, pageable);
        return branches.map(schoolMapper::toBranchResponse);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SchoolBranchResponse getBranchById(UUID schoolId, UUID branchId) {
        findSchoolOrThrow(schoolId);
        SchoolBranch branch = schoolBranchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolBranch", branchId));

        if (!branch.getSchool().getId().equals(schoolId)) {
            throw new ResourceNotFoundException("SchoolBranch", branchId);
        }

        return schoolMapper.toBranchResponse(branch);
    }

    private School findSchoolOrThrow(UUID id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("School", id));
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

    private String generateUniqueBranchCode() {
        String code;
        int attempts = 0;
        do {
            code = CodeGenerator.branchCode();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate unique branch code after 10 attempts");
            }
        } while (schoolBranchRepository.findByBranchCode(code).isPresent());
        return code;
    }
}
