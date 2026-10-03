package com.vidya.tenantdata.academic.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.dto.AssignStudentRequest;
import com.vidya.tenantdata.academic.dto.StudentAcademicAssignmentResponse;
import com.vidya.tenantdata.academic.dto.TransferStudentAssignmentRequest;
import com.vidya.tenantdata.academic.dto.UpdateStudentAssignmentRequest;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.StudentAcademicAssignment;
import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
import com.vidya.tenantdata.academic.mapper.AcademicMapper;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StudentAcademicAssignmentRepository;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAcademicAssignmentServiceImpl implements StudentAcademicAssignmentService {

    private final StudentAcademicAssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentAcademicAssignmentResponse assignStudent(UUID studentId, AssignStudentRequest request) {
        log.info("Assigning student: [{}] to year: [{}], class: [{}], section: [{}]",
                studentId, request.getAcademicYearId(), request.getAcademicClassId(), request.getSectionId());

        Student student = studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(request.getAcademicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.getAcademicYearId()));

        AcademicClass cls = academicClassRepository.findByIdAndNotDeleted(request.getAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getAcademicClassId()));

        Section section = sectionRepository.findByIdAndNotDeleted(request.getSectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Section", request.getSectionId()));

        // Validate structure relationship
        if (!section.getAcademicYear().getId().equals(year.getId()) || !section.getAcademicClass().getId().equals(cls.getId())) {
            throw new BusinessException("Section does not belong to specified academic year and class");
        }

        // Validate no existing active assignment for this academic year
        Optional<StudentAcademicAssignment> existingActive = assignmentRepository.findActiveAssignment(studentId, year.getId());
        if (existingActive.isPresent()) {
            throw new DuplicateResourceException("StudentAcademicAssignment", "active",
                    "Student already has an active placement in year " + year.getCode());
        }

        // Validate capacity
        if (section.getCapacity() != null) {
            long currentCount = assignmentRepository.countActiveAssignmentsBySectionId(section.getId());
            if (currentCount >= section.getCapacity()) {
                throw new BusinessException("Section capacity reached: " + section.getCapacity());
            }
        }

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();

        StudentAcademicAssignment assignment = new StudentAcademicAssignment(
                student,
                year,
                cls,
                section,
                request.getRollNumber(),
                startDate
        );

        StudentAcademicAssignment saved = assignmentRepository.save(assignment);
        log.info("Created student academic assignment ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<StudentAcademicAssignmentResponse> getStudentAssignments(UUID studentId) {
        // Validate student exists
        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        return assignmentRepository.findAllByStudentId(studentId)
                .stream()
                .map(AcademicMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentAcademicAssignmentResponse getCurrentAssignment(UUID studentId) {
        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        Optional<AcademicYear> currentYear = academicYearRepository.findCurrentYear();
        if (currentYear.isPresent()) {
            Optional<StudentAcademicAssignment> current = assignmentRepository.findActiveAssignment(studentId, currentYear.get().getId());
            if (current.isPresent()) {
                return AcademicMapper.toResponse(current.get());
            }
        }

        // Fallback: look for latest active assignment across years
        List<StudentAcademicAssignment> assignments = assignmentRepository.findAllByStudentId(studentId);
        return assignments.stream()
                .filter(a -> a.getStatus() == StudentAssignmentStatus.ACTIVE && !a.isDeleted())
                .findFirst()
                .map(AcademicMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicAssignment", "No active assignment found for student " + studentId));
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentAcademicAssignmentResponse getAssignmentById(UUID studentId, UUID assignmentId) {
        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        StudentAcademicAssignment assignment = assignmentRepository.findByIdAndNotDeleted(assignmentId)
                .filter(a -> a.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicAssignment", assignmentId));

        return AcademicMapper.toResponse(assignment);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentAcademicAssignmentResponse updateAssignment(UUID studentId, UUID assignmentId, UpdateStudentAssignmentRequest request) {
        log.info("Updating assignment ID: [{}] for student: [{}]", assignmentId, studentId);

        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        StudentAcademicAssignment assignment = assignmentRepository.findByIdAndNotDeleted(assignmentId)
                .filter(a -> a.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicAssignment", assignmentId));

        if (request.getRollNumber() != null) {
            assignment.setRollNumber(request.getRollNumber().trim());
        }
        if (request.getStartDate() != null) {
            assignment.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            if (request.getEndDate().isBefore(assignment.getStartDate())) {
                throw new BusinessException("End date cannot be before start date");
            }
            assignment.setEndDate(request.getEndDate());
        }

        StudentAcademicAssignment saved = assignmentRepository.save(assignment);
        log.info("Updated assignment ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentAcademicAssignmentResponse transferStudent(UUID studentId, UUID assignmentId, TransferStudentAssignmentRequest request) {
        log.info("Transferring student: [{}] assignment: [{}] to new class: [{}] section: [{}]",
                studentId, assignmentId, request.getNewAcademicClassId(), request.getNewSectionId());

        Student student = studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        StudentAcademicAssignment oldAssignment = assignmentRepository.findByIdAndNotDeleted(assignmentId)
                .filter(a -> a.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicAssignment", assignmentId));

        if (oldAssignment.getStatus() != StudentAssignmentStatus.ACTIVE) {
            throw new BusinessException("Can only transfer an ACTIVE student assignment");
        }

        AcademicClass newClass = academicClassRepository.findByIdAndNotDeleted(request.getNewAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getNewAcademicClassId()));

        Section newSection = sectionRepository.findByIdAndNotDeleted(request.getNewSectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Section", request.getNewSectionId()));

        AcademicYear academicYear = oldAssignment.getAcademicYear();

        if (!newSection.getAcademicYear().getId().equals(academicYear.getId()) || !newSection.getAcademicClass().getId().equals(newClass.getId())) {
            throw new BusinessException("Target section does not belong to the academic year and class");
        }

        // Validate destination section capacity
        if (newSection.getCapacity() != null) {
            long currentCount = assignmentRepository.countActiveAssignmentsBySectionId(newSection.getId());
            if (currentCount >= newSection.getCapacity()) {
                throw new BusinessException("Target section capacity reached: " + newSection.getCapacity());
            }
        }

        LocalDate transferDate = request.getTransferDate() != null ? request.getTransferDate() : LocalDate.now();

        // End old assignment
        oldAssignment.transfer(transferDate);
        assignmentRepository.save(oldAssignment);

        // Create new assignment
        StudentAcademicAssignment newAssignment = new StudentAcademicAssignment(
                student,
                academicYear,
                newClass,
                newSection,
                request.getNewRollNumber(),
                transferDate
        );

        StudentAcademicAssignment saved = assignmentRepository.save(newAssignment);
        log.info("Successfully transferred student to new assignment ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<StudentAcademicAssignmentResponse> searchAssignments(
            UUID studentId,
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            StudentAssignmentStatus status,
            Pageable pageable) {
        return assignmentRepository.searchAssignments(studentId, academicYearId, academicClassId, sectionId, status, pageable)
                .map(AcademicMapper::toResponse);
    }
}
