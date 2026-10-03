package com.vidya.tenantdata.subject.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.subject.dto.CreateSubjectRequest;
import com.vidya.tenantdata.subject.dto.SubjectDetailResponse;
import com.vidya.tenantdata.subject.dto.SubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateSubjectRequest;
import com.vidya.tenantdata.subject.dto.UpdateSubjectStatusRequest;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.mapper.SubjectMapper;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of Subject business operations in the tenant database schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public SubjectDetailResponse createSubject(CreateSubjectRequest request) {
        log.info("Creating academic subject: [{}] with type: [{}]", request.getName(), request.getSubjectType());

        // 1. Generate unique subject code
        String subjectCode = CodeGenerator.subjectCode();
        int attempts = 0;
        while (subjectRepository.existsBySubjectCode(subjectCode) && attempts < 5) {
            subjectCode = CodeGenerator.subjectCode();
            attempts++;
        }
        if (subjectRepository.existsBySubjectCode(subjectCode)) {
            throw new DuplicateResourceException("Subject", "subjectCode", subjectCode);
        }

        // 2. Build and save entity
        Subject subject = new Subject(
                subjectCode,
                request.getName(),
                request.getShortName(),
                request.getDescription(),
                request.getSubjectType(),
                request.getDisplayOrder()
        );

        Subject saved = subjectRepository.save(subject);
        log.info("Created subject [{}] with id: [{}]", saved.getSubjectCode(), saved.getId());
        return SubjectMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public SubjectDetailResponse getSubject(UUID id) {
        Subject subject = subjectRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", id));
        return SubjectMapper.toDetailResponse(subject);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public SubjectDetailResponse getSubjectByCode(String subjectCode) {
        Subject subject = subjectRepository.findBySubjectCodeAndNotDeleted(subjectCode)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", subjectCode));
        return SubjectMapper.toDetailResponse(subject);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<SubjectResponse> searchSubjects(SubjectType subjectType, SubjectStatus status,
                                                String query, Pageable pageable) {
        return subjectRepository.searchSubjects(subjectType, status, query, pageable)
                .map(SubjectMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public SubjectDetailResponse updateSubject(UUID id, UpdateSubjectRequest request) {
        log.info("Updating subject id: [{}]", id);
        Subject subject = subjectRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", id));

        subject.setName(request.getName().trim());
        subject.setShortName(request.getShortName() != null ? request.getShortName().trim() : null);
        subject.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        subject.setSubjectType(request.getSubjectType());
        if (request.getDisplayOrder() != null) {
            subject.setDisplayOrder(request.getDisplayOrder());
        }

        Subject updated = subjectRepository.save(subject);
        return SubjectMapper.toDetailResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public SubjectDetailResponse updateStatus(UUID id, UpdateSubjectStatusRequest request) {
        log.info("Updating status for subject id: [{}] to [{}]", id, request.getStatus());
        Subject subject = subjectRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", id));

        subject.updateStatus(request.getStatus());
        Subject updated = subjectRepository.save(subject);
        return SubjectMapper.toDetailResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteSubject(UUID id) {
        log.info("Soft-deleting subject id: [{}]", id);
        Subject subject = subjectRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", id));

        subject.softDelete();
        subjectRepository.save(subject);
    }
}
