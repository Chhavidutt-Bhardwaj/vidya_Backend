package com.vidya.tenantdata.examination.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.examination.dto.CreateExamTermRequest;
import com.vidya.tenantdata.examination.dto.ExamTermResponse;
import com.vidya.tenantdata.examination.dto.UpdateExamTermRequest;
import com.vidya.tenantdata.examination.entity.ExamTerm;
import com.vidya.tenantdata.examination.enums.ExamTermStatus;
import com.vidya.tenantdata.examination.mapper.ExamTermMapper;
import com.vidya.tenantdata.examination.repository.ExamTermRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamTermServiceImpl implements ExamTermService {

    private final ExamTermRepository examTermRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ExamTermMapper examTermMapper;

    @Override
    @Transactional("tenantTransactionManager")
    public ExamTermResponse createExamTerm(CreateExamTermRequest request) {
        log.info("Creating exam term [{}] in academic year [{}]", request.getName(), request.getAcademicYearId());

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException("Term start date cannot be after end date");
        }

        AcademicYear academicYear = academicYearRepository.findById(request.getAcademicYearId())
                .filter(ay -> ay.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.getAcademicYearId()));

        if (examTermRepository.existsByAcademicYearIdAndNameIgnoreCase(academicYear.getId(), request.getName())) {
            throw new DuplicateResourceException("Exam term with name '" + request.getName() + "' already exists in this academic year");
        }

        ExamTerm term = new ExamTerm(
                CodeGenerator.examTermCode(),
                academicYear,
                request.getName(),
                request.getStartDate(),
                request.getEndDate(),
                request.getWeightagePercentage()
        );

        ExamTerm saved = examTermRepository.save(term);
        return examTermMapper.toResponse(saved);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ExamTermResponse getExamTermById(UUID id) {
        ExamTerm term = examTermRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamTerm", id));
        return examTermMapper.toResponse(term);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ExamTermResponse> getExamTermsByAcademicYear(UUID academicYearId) {
        return examTermRepository.findByAcademicYearId(academicYearId)
                .stream()
                .map(examTermMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<ExamTermResponse> searchExamTerms(UUID academicYearId, ExamTermStatus status, Pageable pageable) {
        return examTermRepository.searchTerms(academicYearId, status, pageable)
                .map(examTermMapper::toResponse);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public ExamTermResponse updateExamTerm(UUID id, UpdateExamTermRequest request) {
        log.info("Updating exam term [{}]", id);

        ExamTerm term = examTermRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamTerm", id));

        if (term.getStatus() == ExamTermStatus.COMPLETED) {
            throw new BusinessException("Cannot update an already completed exam term");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            term.setName(request.getName());
        }

        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new BusinessException("Term start date cannot be after end date");
            }
            term.setStartDate(request.getStartDate());
            term.setEndDate(request.getEndDate());
        } else if (request.getStartDate() != null) {
            if (request.getStartDate().isAfter(term.getEndDate())) {
                throw new BusinessException("Term start date cannot be after end date");
            }
            term.setStartDate(request.getStartDate());
        } else if (request.getEndDate() != null) {
            if (term.getStartDate().isAfter(request.getEndDate())) {
                throw new BusinessException("Term start date cannot be after end date");
            }
            term.setEndDate(request.getEndDate());
        }

        if (request.getWeightagePercentage() != null) {
            term.setWeightagePercentage(request.getWeightagePercentage());
        }

        ExamTerm updated = examTermRepository.save(term);
        return examTermMapper.toResponse(updated);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public ExamTermResponse updateTermStatus(UUID id, ExamTermStatus status) {
        log.info("Transitioning exam term [{}] to status [{}]", id, status);

        ExamTerm term = examTermRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamTerm", id));

        term.setStatus(status);
        ExamTerm saved = examTermRepository.save(term);
        return examTermMapper.toResponse(saved);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public void deleteExamTerm(UUID id) {
        log.info("Soft deleting exam term [{}]", id);

        ExamTerm term = examTermRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamTerm", id));

        term.setDeletedAt(Instant.now());
        examTermRepository.save(term);
    }
}
