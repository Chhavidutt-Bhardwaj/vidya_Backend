package com.vidya.tenantdata.assignment.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import com.vidya.tenantdata.assignment.mapper.ClassSubjectAssignmentMapper;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of ClassSubjectAssignment business operations in the tenant database schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassSubjectAssignmentServiceImpl implements ClassSubjectAssignmentService {

    private final ClassSubjectAssignmentRepository classSubjectAssignmentRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final CurriculumRepository curriculumRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassSubjectAssignmentDetailResponse createAssignment(CreateClassSubjectAssignmentRequest request) {
        log.info("Creating class subject assignment: year [{}], class [{}], section [{}], subject [{}]",
                request.getAcademicYearId(), request.getAcademicClassId(), request.getSectionId(), request.getSubjectId());

        // 1. Resolve AcademicYear from THIS tenant's DB
        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(request.getAcademicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.getAcademicYearId()));

        // 2. Resolve AcademicClass from THIS tenant's DB
        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.getAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getAcademicClassId()));

        // 3. If Section is provided, resolve and validate consistency
        Section section = null;
        if (request.getSectionId() != null) {
            section = sectionRepository.findByIdAndNotDeleted(request.getSectionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Section", request.getSectionId()));

            if (!section.getAcademicYear().getId().equals(academicYear.getId())) {
                throw new BusinessException("Section does not belong to the specified AcademicYear");
            }
            if (!section.getAcademicClass().getId().equals(academicClass.getId())) {
                throw new BusinessException("Section does not belong to the specified AcademicClass");
            }
        }

        // 4. Resolve Subject from THIS tenant's DB
        Subject subject = subjectRepository.findByIdAndNotDeleted(request.getSubjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", request.getSubjectId()));

        if (subject.getStatus() != SubjectStatus.ACTIVE) {
            throw new BusinessException("Cannot assign subject that is not ACTIVE. Current status: " + subject.getStatus());
        }

        // 5. If Curriculum is provided, resolve and validate consistency
        Curriculum curriculum = null;
        if (request.getCurriculumId() != null) {
            curriculum = curriculumRepository.findByIdAndNotDeleted(request.getCurriculumId())
                    .orElseThrow(() -> new ResourceNotFoundException("Curriculum", request.getCurriculumId()));

            if (curriculum.getStatus() == CurriculumStatus.DELETED) {
                throw new BusinessException("Cannot associate a DELETED curriculum");
            }
            if (!curriculum.getAcademicYear().getId().equals(academicYear.getId())) {
                throw new BusinessException("Curriculum does not belong to the specified AcademicYear");
            }
        }

        // 6. Duplicate active mapping check
        if (classSubjectAssignmentRepository.existsActiveDuplicate(
                request.getAcademicYearId(),
                request.getAcademicClassId(),
                request.getSectionId(),
                request.getSubjectId(),
                null)) {
            throw new DuplicateResourceException(
                    "ClassSubjectAssignment",
                    "academicYearId, academicClassId, sectionId, subjectId",
                    request.getAcademicYearId() + ", " + request.getAcademicClassId() + ", " + request.getSectionId() + ", " + request.getSubjectId()
            );
        }

        // 7. Generate business code
        String assignmentCode = CodeGenerator.classSubjectAssignmentCode();
        int attempts = 0;
        while (classSubjectAssignmentRepository.existsByAssignmentCode(assignmentCode) && attempts < 5) {
            assignmentCode = CodeGenerator.classSubjectAssignmentCode();
            attempts++;
        }
        if (classSubjectAssignmentRepository.existsByAssignmentCode(assignmentCode)) {
            throw new DuplicateResourceException("ClassSubjectAssignment", "assignmentCode", assignmentCode);
        }

        boolean compulsory = request.getIsCompulsory() == null || request.getIsCompulsory();
        int displayOrder = request.getDisplayOrder() != null ? request.getDisplayOrder() : 0;

        // 8. Build and persist entity
        ClassSubjectAssignment assignment = new ClassSubjectAssignment(
                assignmentCode,
                academicYear,
                academicClass,
                section,
                subject,
                curriculum,
                compulsory,
                displayOrder,
                request.getPeriodsPerWeek()
        );

        ClassSubjectAssignment saved = classSubjectAssignmentRepository.save(assignment);
        log.info("Created class subject assignment [{}] with id: [{}]", saved.getAssignmentCode(), saved.getId());
        return ClassSubjectAssignmentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ClassSubjectAssignmentDetailResponse getAssignment(UUID id) {
        ClassSubjectAssignment assignment = classSubjectAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", id));
        return ClassSubjectAssignmentMapper.toDetailResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ClassSubjectAssignmentDetailResponse getAssignmentByCode(String assignmentCode) {
        ClassSubjectAssignment assignment = classSubjectAssignmentRepository.findByAssignmentCodeAndNotDeleted(assignmentCode)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", assignmentCode));
        return ClassSubjectAssignmentMapper.toDetailResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<ClassSubjectAssignmentResponse> searchAssignments(
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            UUID subjectId,
            ClassSubjectAssignmentStatus status,
            Boolean isCompulsory,
            Pageable pageable) {
        return classSubjectAssignmentRepository.searchAssignments(
                academicYearId, academicClassId, sectionId, subjectId, status, isCompulsory, pageable
        ).map(ClassSubjectAssignmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ClassSubjectAssignmentResponse> getActiveAssignmentsByClassAndSection(UUID academicYearId, UUID academicClassId, UUID sectionId) {
        return classSubjectAssignmentRepository.findActiveByClassAndSection(academicYearId, academicClassId, sectionId)
                .stream()
                .map(ClassSubjectAssignmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassSubjectAssignmentDetailResponse updateAssignment(UUID id, UpdateClassSubjectAssignmentRequest request) {
        log.info("Updating class subject assignment id: [{}]", id);
        ClassSubjectAssignment assignment = classSubjectAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", id));

        if (request.getIsCompulsory() != null) {
            assignment.setCompulsory(request.getIsCompulsory());
        }
        if (request.getDisplayOrder() != null) {
            assignment.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getPeriodsPerWeek() != null) {
            assignment.setPeriodsPerWeek(request.getPeriodsPerWeek());
        }

        ClassSubjectAssignment updated = classSubjectAssignmentRepository.save(assignment);
        return ClassSubjectAssignmentMapper.toDetailResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassSubjectAssignmentDetailResponse updateStatus(UUID id, UpdateClassSubjectAssignmentStatusRequest request) {
        log.info("Updating status for class subject assignment id: [{}] to [{}]", id, request.getStatus());
        ClassSubjectAssignment assignment = classSubjectAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", id));

        assignment.updateStatus(request.getStatus());
        ClassSubjectAssignment updated = classSubjectAssignmentRepository.save(assignment);
        return ClassSubjectAssignmentMapper.toDetailResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteAssignment(UUID id) {
        log.info("Soft-deleting class subject assignment id: [{}]", id);
        ClassSubjectAssignment assignment = classSubjectAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", id));

        assignment.softDelete();
        classSubjectAssignmentRepository.save(assignment);
    }
}
