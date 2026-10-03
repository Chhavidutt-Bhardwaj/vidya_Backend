package com.vidya.tenantdata.subject.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.subject.dto.CreateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.CurriculumSubject;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.mapper.CurriculumMapper;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.CurriculumSubjectRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of CurriculumSubject business operations in the tenant database schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumSubjectServiceImpl implements CurriculumSubjectService {

    private final CurriculumRepository curriculumRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicClassRepository academicClassRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public CurriculumSubjectResponse addSubjectToCurriculum(UUID curriculumId, CreateCurriculumSubjectRequest request) {
        log.info("Adding subject [{}] to curriculum [{}] for academic class [{}]",
                request.getSubjectId(), curriculumId, request.getAcademicClassId());

        // 1. Resolve Curriculum within THIS tenant
        Curriculum curriculum = curriculumRepository.findByIdAndNotDeleted(curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", curriculumId));

        if (curriculum.getStatus() == CurriculumStatus.DELETED || curriculum.getStatus() == CurriculumStatus.ARCHIVED) {
            throw new BusinessException("Cannot add subjects to a curriculum with status: " + curriculum.getStatus());
        }

        // 2. Resolve Subject within THIS tenant
        Subject subject = subjectRepository.findByIdAndNotDeleted(request.getSubjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", request.getSubjectId()));

        if (subject.getStatus() == SubjectStatus.DELETED) {
            throw new BusinessException("Cannot add a deleted subject to a curriculum");
        }

        // 3. Resolve AcademicClass within THIS tenant
        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.getAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getAcademicClassId()));

        // 4. Check for duplicate active mapping
        if (curriculumSubjectRepository.existsActiveByCurriculumSubjectClass(
                curriculumId, request.getSubjectId(), request.getAcademicClassId(), null)) {
            throw new DuplicateResourceException(
                    "CurriculumSubject",
                    "curriculumId, subjectId, academicClassId",
                    curriculumId + ", " + request.getSubjectId() + ", " + request.getAcademicClassId()
            );
        }

        boolean compulsory = request.getIsCompulsory() == null || request.getIsCompulsory();
        int displayOrder = request.getDisplayOrder() != null ? request.getDisplayOrder() : 0;

        CurriculumSubject curriculumSubject = new CurriculumSubject(
                curriculum,
                subject,
                academicClass,
                compulsory,
                displayOrder
        );

        CurriculumSubject saved = curriculumSubjectRepository.save(curriculumSubject);
        log.info("Added subject mapping id: [{}] to curriculum [{}]", saved.getId(), curriculum.getCurriculumCode());
        return CurriculumMapper.toCurriculumSubjectResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public CurriculumSubjectResponse getCurriculumSubject(UUID curriculumId, UUID curriculumSubjectId) {
        CurriculumSubject cs = curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(curriculumSubjectId, curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("CurriculumSubject", curriculumSubjectId));
        return CurriculumMapper.toCurriculumSubjectResponse(cs);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<CurriculumSubjectResponse> listCurriculumSubjects(UUID curriculumId, UUID academicClassId,
                                                                 SubjectType subjectType, Boolean isCompulsory,
                                                                 Pageable pageable) {
        if (!curriculumRepository.existsById(curriculumId)) {
            throw new ResourceNotFoundException("Curriculum", curriculumId);
        }
        return curriculumSubjectRepository.searchCurriculumSubjects(curriculumId, academicClassId, subjectType, isCompulsory, pageable)
                .map(CurriculumMapper::toCurriculumSubjectResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public CurriculumSubjectResponse updateCurriculumSubject(UUID curriculumId, UUID curriculumSubjectId,
                                                             UpdateCurriculumSubjectRequest request) {
        log.info("Updating curriculum subject id: [{}] for curriculum id: [{}]", curriculumSubjectId, curriculumId);
        CurriculumSubject cs = curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(curriculumSubjectId, curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("CurriculumSubject", curriculumSubjectId));

        if (request.getIsCompulsory() != null) {
            cs.setCompulsory(request.getIsCompulsory());
        }
        if (request.getDisplayOrder() != null) {
            cs.setDisplayOrder(request.getDisplayOrder());
        }

        CurriculumSubject updated = curriculumSubjectRepository.save(cs);
        return CurriculumMapper.toCurriculumSubjectResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void removeSubjectFromCurriculum(UUID curriculumId, UUID curriculumSubjectId) {
        log.info("Soft-deleting curriculum subject id: [{}] from curriculum id: [{}]", curriculumSubjectId, curriculumId);
        CurriculumSubject cs = curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(curriculumSubjectId, curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("CurriculumSubject", curriculumSubjectId));

        cs.softDelete();
        curriculumSubjectRepository.save(cs);
    }
}
