package com.vidya.tenantdata.student.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.student.dto.BatchAssignSubjectsRequest;
import com.vidya.tenantdata.student.dto.CreateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.dto.StudentSubjectEnrollmentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollment;
import com.vidya.tenantdata.student.mapper.StudentSubjectEnrollmentMapper;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentSubjectEnrollmentRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentSubjectEnrollmentServiceImpl implements StudentSubjectEnrollmentService {

    private final StudentSubjectEnrollmentRepository subjectEnrollmentRepository;
    private final StudentAcademicEnrollmentRepository enrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentSubjectEnrollmentResponse enrollSubject(UUID enrollmentId, CreateStudentSubjectEnrollmentRequest request) {
        log.info("Enrolling student enrollment [{}] into subject [{}]", enrollmentId, request.getSubjectId());

        StudentAcademicEnrollment enrollment = enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        Subject subject = subjectRepository.findByIdAndNotDeleted(request.getSubjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", request.getSubjectId()));

        if (subjectEnrollmentRepository.existsActiveByEnrollmentIdAndSubjectId(enrollmentId, request.getSubjectId())) {
            throw new DuplicateResourceException("StudentSubjectEnrollment", "subjectId", request.getSubjectId());
        }

        ClassSubjectAssignment csa = null;
        if (request.getClassSubjectAssignmentId() != null) {
            csa = classSubjectAssignmentRepository.findByIdAndNotDeleted(request.getClassSubjectAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", request.getClassSubjectAssignmentId()));

            // Validate that CSA matches the student's academic year and class
            if (!csa.getAcademicYear().getId().equals(enrollment.getAcademicYear().getId()) ||
                    !csa.getAcademicClass().getId().equals(enrollment.getAcademicClass().getId())) {
                throw new BusinessException("ClassSubjectAssignment does not match student enrollment's academic year and class");
            }
            if (!csa.getSubject().getId().equals(subject.getId())) {
                throw new BusinessException("ClassSubjectAssignment subject does not match the requested subject");
            }
        } else {
            // Try to resolve matching CSA for this class, section and subject
            UUID sectionId = enrollment.getSection() != null ? enrollment.getSection().getId() : null;
            List<ClassSubjectAssignment> assignments = classSubjectAssignmentRepository.findActiveByClassAndSection(
                    enrollment.getAcademicYear().getId(),
                    enrollment.getAcademicClass().getId(),
                    sectionId
            );
            csa = assignments.stream()
                    .filter(a -> a.getSubject().getId().equals(subject.getId()))
                    .findFirst()
                    .orElse(null);
        }

        String code = generateUniqueSubjectEnrollmentCode();
        LocalDate effectiveFrom = request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now();

        StudentSubjectEnrollment subjectEnrollment = new StudentSubjectEnrollment(
                code,
                enrollment,
                subject,
                csa,
                effectiveFrom,
                request.getRemarks()
        );

        StudentSubjectEnrollment saved = subjectEnrollmentRepository.save(subjectEnrollment);
        log.info("Successfully enrolled student enrollment [{}] into subject [{}], code: [{}]",
                enrollmentId, subject.getName(), saved.getSubjectEnrollmentCode());
        return StudentSubjectEnrollmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<StudentSubjectEnrollmentResponse> batchEnrollSubjects(UUID enrollmentId, BatchAssignSubjectsRequest request) {
        log.info("Batch enrolling student enrollment [{}] into [{}] subjects", enrollmentId, request.getSubjectIds().size());

        StudentAcademicEnrollment enrollment = enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        List<StudentSubjectEnrollmentResponse> responses = new ArrayList<>();
        LocalDate effectiveFrom = request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now();

        for (UUID subjectId : request.getSubjectIds()) {
            if (subjectEnrollmentRepository.existsActiveByEnrollmentIdAndSubjectId(enrollmentId, subjectId)) {
                log.warn("Student enrollment [{}] already enrolled in subject [{}], skipping duplicate", enrollmentId, subjectId);
                continue;
            }
            Subject subject = subjectRepository.findByIdAndNotDeleted(subjectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Subject", subjectId));

            UUID sectionId = enrollment.getSection() != null ? enrollment.getSection().getId() : null;
            List<ClassSubjectAssignment> assignments = classSubjectAssignmentRepository.findActiveByClassAndSection(
                    enrollment.getAcademicYear().getId(),
                    enrollment.getAcademicClass().getId(),
                    sectionId
            );
            ClassSubjectAssignment csa = assignments.stream()
                    .filter(a -> a.getSubject().getId().equals(subject.getId()))
                    .findFirst()
                    .orElse(null);

            String code = generateUniqueSubjectEnrollmentCode();
            StudentSubjectEnrollment sse = new StudentSubjectEnrollment(
                    code,
                    enrollment,
                    subject,
                    csa,
                    effectiveFrom,
                    request.getRemarks()
            );
            StudentSubjectEnrollment saved = subjectEnrollmentRepository.save(sse);
            responses.add(StudentSubjectEnrollmentMapper.toResponse(saved));
        }

        return responses;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<StudentSubjectEnrollment> autoEnrollCompulsorySubjects(StudentAcademicEnrollment enrollment) {
        log.info("Auto-enrolling compulsory subjects for enrollment [{}] in class [{}]",
                enrollment.getId(), enrollment.getAcademicClass().getCode());

        UUID sectionId = enrollment.getSection() != null ? enrollment.getSection().getId() : null;
        List<ClassSubjectAssignment> assignments = classSubjectAssignmentRepository.findActiveByClassAndSection(
                enrollment.getAcademicYear().getId(),
                enrollment.getAcademicClass().getId(),
                sectionId
        );

        List<StudentSubjectEnrollment> enrolledList = new ArrayList<>();
        LocalDate effectiveFrom = enrollment.getEnrollmentDate() != null ? enrollment.getEnrollmentDate() : LocalDate.now();

        for (ClassSubjectAssignment csa : assignments) {
            if (csa.isCompulsory()) {
                Subject subject = csa.getSubject();
                if (!subjectEnrollmentRepository.existsActiveByEnrollmentIdAndSubjectId(enrollment.getId(), subject.getId())) {
                    String code = generateUniqueSubjectEnrollmentCode();
                    StudentSubjectEnrollment sse = new StudentSubjectEnrollment(
                            code,
                            enrollment,
                            subject,
                            csa,
                            effectiveFrom,
                            "Auto-assigned compulsory class subject"
                    );
                    enrolledList.add(subjectEnrollmentRepository.save(sse));
                }
            }
        }
        return enrolledList;
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<StudentSubjectEnrollmentResponse> getEnrolledSubjects(UUID enrollmentId) {
        enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        return subjectEnrollmentRepository.findAllByEnrollmentId(enrollmentId)
                .stream()
                .map(StudentSubjectEnrollmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentSubjectEnrollmentResponse getSubjectEnrollmentById(UUID enrollmentId, UUID subjectEnrollmentId) {
        enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        StudentSubjectEnrollment sse = subjectEnrollmentRepository.findByIdAndNotDeleted(subjectEnrollmentId)
                .filter(s -> s.getEnrollment().getId().equals(enrollmentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentSubjectEnrollment", subjectEnrollmentId));

        return StudentSubjectEnrollmentMapper.toResponse(sse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentSubjectEnrollmentResponse updateSubjectEnrollment(UUID enrollmentId, UUID subjectEnrollmentId, UpdateStudentSubjectEnrollmentRequest request) {
        log.info("Updating subject enrollment [{}] for enrollment [{}]", subjectEnrollmentId, enrollmentId);

        enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        StudentSubjectEnrollment sse = subjectEnrollmentRepository.findByIdAndNotDeleted(subjectEnrollmentId)
                .filter(s -> s.getEnrollment().getId().equals(enrollmentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentSubjectEnrollment", subjectEnrollmentId));

        if (request.getStatus() != null) {
            sse.updateStatus(request.getStatus());
        }
        if (request.getEffectiveTo() != null) {
            if (request.getEffectiveTo().isBefore(sse.getEffectiveFrom())) {
                throw new BusinessException("Effective to date cannot be before effective from date");
            }
            sse.setEffectiveTo(request.getEffectiveTo());
        }
        if (request.getRemarks() != null) {
            sse.setRemarks(request.getRemarks().trim());
        }

        StudentSubjectEnrollment saved = subjectEnrollmentRepository.save(sse);
        log.info("Updated subject enrollment [{}] to status [{}]", saved.getId(), saved.getStatus());
        return StudentSubjectEnrollmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void removeSubjectEnrollment(UUID enrollmentId, UUID subjectEnrollmentId) {
        log.info("Soft-deleting subject enrollment [{}] for enrollment [{}]", subjectEnrollmentId, enrollmentId);

        enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        StudentSubjectEnrollment sse = subjectEnrollmentRepository.findByIdAndNotDeleted(subjectEnrollmentId)
                .filter(s -> s.getEnrollment().getId().equals(enrollmentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentSubjectEnrollment", subjectEnrollmentId));

        sse.softDelete();
        subjectEnrollmentRepository.save(sse);
        log.info("Successfully soft-deleted subject enrollment [{}]", subjectEnrollmentId);
    }

    private String generateUniqueSubjectEnrollmentCode() {
        String code = CodeGenerator.studentSubjectEnrollmentCode();
        while (subjectEnrollmentRepository.existsBySubjectEnrollmentCode(code)) {
            code = CodeGenerator.studentSubjectEnrollmentCode();
        }
        return code;
    }
}
