package com.vidya.tenantdata.academic.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.dto.AcademicClassResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicClassRequest;
import com.vidya.tenantdata.academic.dto.UpdateAcademicClassRequest;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import com.vidya.tenantdata.academic.mapper.AcademicMapper;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicClassServiceImpl implements AcademicClassService {

    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicClassResponse createAcademicClass(CreateAcademicClassRequest request) {
        log.info("Creating academic class with code: [{}]", request.getCode());

        String code = request.getCode().trim();
        if (academicClassRepository.existsByCode(code)) {
            throw new DuplicateResourceException("AcademicClass", "code", code);
        }

        AcademicClass academicClass = AcademicMapper.toEntity(request);
        AcademicClass saved = academicClassRepository.save(academicClass);

        log.info("Created academic class ID: [{}] code: [{}]", saved.getId(), saved.getCode());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AcademicClassResponse getAcademicClassById(UUID id) {
        AcademicClass cls = academicClassRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", id));
        return AcademicMapper.toResponse(cls);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AcademicClassResponse getAcademicClassByCode(String code) {
        AcademicClass cls = academicClassRepository.findByCodeAndNotDeleted(code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", code));
        return AcademicMapper.toResponse(cls);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<AcademicClassResponse> searchAcademicClasses(AcademicClassStatus status, Pageable pageable) {
        return academicClassRepository.searchAcademicClasses(status, pageable)
                .map(AcademicMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicClassResponse updateAcademicClass(UUID id, UpdateAcademicClassRequest request) {
        log.info("Updating academic class ID: [{}]", id);

        AcademicClass cls = academicClassRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", id));

        if (request.getName() != null && !request.getName().isBlank()) {
            cls.setName(request.getName().trim());
        }
        if (request.getDisplayName() != null) {
            cls.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getSortOrder() != null) {
            cls.setSortOrder(request.getSortOrder());
        }

        AcademicClass saved = academicClassRepository.save(cls);
        log.info("Updated academic class ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteAcademicClass(UUID id) {
        log.info("Soft-deleting academic class ID: [{}]", id);

        AcademicClass cls = academicClassRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", id));

        if (sectionRepository.existsByAcademicClassIdAndDeletedAtIsNull(id)) {
            throw new BusinessException("Cannot delete academic class referenced by existing sections");
        }

        cls.softDelete();
        academicClassRepository.save(cls);
        log.info("Successfully soft-deleted academic class ID: [{}]", id);
    }
}
