package com.vidya.tenantdata.academic.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.dto.AcademicYearResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicYearRequest;
import com.vidya.tenantdata.academic.dto.UpdateAcademicYearRequest;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.mapper.AcademicMapper;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicYearServiceImpl implements AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final SectionRepository sectionRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicYearResponse createAcademicYear(CreateAcademicYearRequest request) {
        log.info("Creating academic year with code: [{}]", request.getCode());

        String code = request.getCode().trim();
        if (academicYearRepository.existsByCode(code)) {
            throw new DuplicateResourceException("AcademicYear", "code", code);
        }

        if (request.getEndDate().isBefore(request.getStartDate()) || request.getEndDate().isEqual(request.getStartDate())) {
            throw new BusinessException("Academic year end date must be strictly after start date");
        }

        if (request.isCurrent()) {
            academicYearRepository.unsetAllCurrent();
        }

        AcademicYear academicYear = AcademicMapper.toEntity(request);
        AcademicYear saved = academicYearRepository.save(academicYear);

        log.info("Created academic year ID: [{}] code: [{}]", saved.getId(), saved.getCode());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AcademicYearResponse getAcademicYearById(UUID id) {
        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));
        return AcademicMapper.toResponse(year);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AcademicYearResponse getAcademicYearByCode(String code) {
        AcademicYear year = academicYearRepository.findByCodeAndNotDeleted(code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", code));
        return AcademicMapper.toResponse(year);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AcademicYearResponse getCurrentAcademicYear() {
        AcademicYear year = academicYearRepository.findCurrentYear()
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "current"));
        return AcademicMapper.toResponse(year);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<AcademicYearResponse> searchAcademicYears(AcademicYearStatus status, Boolean isCurrent, Pageable pageable) {
        return academicYearRepository.searchAcademicYears(status, isCurrent, pageable)
                .map(AcademicMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicYearResponse updateAcademicYear(UUID id, UpdateAcademicYearRequest request) {
        log.info("Updating academic year ID: [{}]", id);

        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : year.getStartDate();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : year.getEndDate();

        if (endDate.isBefore(startDate) || endDate.isEqual(startDate)) {
            throw new BusinessException("Academic year end date must be strictly after start date");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            year.setName(request.getName().trim());
        }
        if (request.getStartDate() != null) {
            year.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            year.setEndDate(request.getEndDate());
        }

        AcademicYear saved = academicYearRepository.save(year);
        log.info("Updated academic year ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicYearResponse activateAcademicYear(UUID id) {
        log.info("Activating academic year ID: [{}]", id);

        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));

        year.updateStatus(AcademicYearStatus.ACTIVE);
        AcademicYear saved = academicYearRepository.save(year);

        log.info("Activated academic year ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicYearResponse closeAcademicYear(UUID id) {
        log.info("Closing academic year ID: [{}]", id);

        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));

        year.updateStatus(AcademicYearStatus.CLOSED);
        AcademicYear saved = academicYearRepository.save(year);

        log.info("Closed academic year ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicYearResponse setCurrentAcademicYear(UUID id) {
        log.info("Setting academic year ID: [{}] as current", id);

        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));

        if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED || year.getStatus() == AcademicYearStatus.DELETED) {
            throw new BusinessException("Cannot set non-active/closed academic year as current");
        }

        academicYearRepository.unsetCurrentForOtherYears(id);
        year.setCurrent(true);
        if (year.getStatus() == AcademicYearStatus.DRAFT) {
            year.setStatus(AcademicYearStatus.ACTIVE);
        }

        AcademicYear saved = academicYearRepository.save(year);
        log.info("Successfully set academic year ID: [{}] as current", saved.getId());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteAcademicYear(UUID id) {
        log.info("Soft-deleting academic year ID: [{}]", id);

        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));

        if (sectionRepository.existsByAcademicYearIdAndDeletedAtIsNull(id)) {
            throw new BusinessException("Cannot delete academic year that is referenced by existing sections");
        }

        year.softDelete();
        academicYearRepository.save(year);
        log.info("Successfully soft-deleted academic year ID: [{}]", id);
    }
}
