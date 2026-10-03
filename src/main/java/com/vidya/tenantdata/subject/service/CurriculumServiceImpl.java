package com.vidya.tenantdata.subject.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.subject.dto.CreateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.CurriculumDetailResponse;
import com.vidya.tenantdata.subject.dto.CurriculumResponse;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumStatusRequest;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.mapper.CurriculumMapper;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.CurriculumSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of Curriculum business operations in the tenant database schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumServiceImpl implements CurriculumService {

    private final CurriculumRepository curriculumRepository;
    private final AcademicYearRepository academicYearRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public CurriculumDetailResponse createCurriculum(CreateCurriculumRequest request) {
        log.info("Creating curriculum: [{}] for academicYearId: [{}]", request.getName(), request.getAcademicYearId());

        // 1. Resolve AcademicYear from THIS tenant's DB (cross-tenant isolation check)
        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(request.getAcademicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.getAcademicYearId()));

        // 2. Generate unique curriculum code
        String curriculumCode = CodeGenerator.curriculumCode();
        int attempts = 0;
        while (curriculumRepository.existsByCurriculumCode(curriculumCode) && attempts < 5) {
            curriculumCode = CodeGenerator.curriculumCode();
            attempts++;
        }
        if (curriculumRepository.existsByCurriculumCode(curriculumCode)) {
            throw new DuplicateResourceException("Curriculum", "curriculumCode", curriculumCode);
        }

        // 3. Build and save entity
        Curriculum curriculum = new Curriculum(
                curriculumCode,
                academicYear,
                request.getName(),
                request.getDescription()
        );

        Curriculum saved = curriculumRepository.save(curriculum);
        log.info("Created curriculum [{}] with id: [{}]", saved.getCurriculumCode(), saved.getId());
        return CurriculumMapper.toDetailResponse(saved, List.of());
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public CurriculumDetailResponse getCurriculum(UUID id) {
        Curriculum curriculum = curriculumRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", id));
        List<CurriculumSubjectResponse> subjects = curriculumSubjectRepository
                .findByCurriculumIdAndNotDeleted(id)
                .stream()
                .map(CurriculumMapper::toCurriculumSubjectResponse)
                .toList();
        return CurriculumMapper.toDetailResponse(curriculum, subjects);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public CurriculumDetailResponse getCurriculumByCode(String curriculumCode) {
        Curriculum curriculum = curriculumRepository.findByCurriculumCodeAndNotDeleted(curriculumCode)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", curriculumCode));
        List<CurriculumSubjectResponse> subjects = curriculumSubjectRepository
                .findByCurriculumIdAndNotDeleted(curriculum.getId())
                .stream()
                .map(CurriculumMapper::toCurriculumSubjectResponse)
                .toList();
        return CurriculumMapper.toDetailResponse(curriculum, subjects);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<CurriculumResponse> searchCurricula(UUID academicYearId, CurriculumStatus status,
                                                    String query, Pageable pageable) {
        return curriculumRepository.searchCurricula(academicYearId, status, query, pageable)
                .map(CurriculumMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public CurriculumDetailResponse updateCurriculum(UUID id, UpdateCurriculumRequest request) {
        log.info("Updating curriculum id: [{}]", id);
        Curriculum curriculum = curriculumRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", id));

        curriculum.setName(request.getName().trim());
        curriculum.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Curriculum updated = curriculumRepository.save(curriculum);
        List<CurriculumSubjectResponse> subjects = curriculumSubjectRepository
                .findByCurriculumIdAndNotDeleted(id)
                .stream()
                .map(CurriculumMapper::toCurriculumSubjectResponse)
                .toList();
        return CurriculumMapper.toDetailResponse(updated, subjects);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public CurriculumDetailResponse updateStatus(UUID id, UpdateCurriculumStatusRequest request) {
        log.info("Updating status for curriculum id: [{}] to [{}]", id, request.getStatus());
        Curriculum curriculum = curriculumRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", id));

        curriculum.updateStatus(request.getStatus());
        Curriculum updated = curriculumRepository.save(curriculum);
        List<CurriculumSubjectResponse> subjects = curriculumSubjectRepository
                .findByCurriculumIdAndNotDeleted(id)
                .stream()
                .map(CurriculumMapper::toCurriculumSubjectResponse)
                .toList();
        return CurriculumMapper.toDetailResponse(updated, subjects);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteCurriculum(UUID id) {
        log.info("Soft-deleting curriculum id: [{}]", id);
        Curriculum curriculum = curriculumRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", id));

        curriculum.softDelete();
        curriculumRepository.save(curriculum);
    }
}
