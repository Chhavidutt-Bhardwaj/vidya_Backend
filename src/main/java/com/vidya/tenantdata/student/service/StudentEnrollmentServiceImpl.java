package com.vidya.tenantdata.student.service;

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
import com.vidya.tenantdata.student.dto.BatchAssignSubjectsRequest;
import com.vidya.tenantdata.student.dto.ClassSectionStudentResponse;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.EnrollmentDetailResponse;
import com.vidya.tenantdata.student.dto.EnrollmentResponse;
import com.vidya.tenantdata.student.dto.TransferEnrollmentRequest;
import com.vidya.tenantdata.student.dto.UpdateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.WithdrawEnrollmentRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollment;
import com.vidya.tenantdata.student.mapper.EnrollmentMapper;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.repository.StudentSubjectEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentEnrollmentServiceImpl implements StudentEnrollmentService {

    private final StudentAcademicEnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final StudentSubjectEnrollmentService subjectEnrollmentService;
    private final StudentSubjectEnrollmentRepository subjectEnrollmentRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public EnrollmentDetailResponse createEnrollment(UUID studentId, CreateEnrollmentRequest request) {
        log.info("Enrolling student ID: [{}] in year: [{}], class: [{}], section: [{}]",
                studentId, request.getAcademicYearId(), request.getAcademicClassId(), request.getSectionId());

        Student student = studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        StudentAcademicEnrollment enrollment = createEnrollmentEntity(student, request);

        List<StudentSubjectEnrollment> enrolledSubjects = Collections.emptyList();
        if (Boolean.TRUE.equals(request.getAutoAssignCompulsorySubjects())) {
            enrolledSubjects = subjectEnrollmentService.autoEnrollCompulsorySubjects(enrollment);
        }
        if (request.getSubjectIds() != null && !request.getSubjectIds().isEmpty()) {
            subjectEnrollmentService.batchEnrollSubjects(
                    enrollment.getId(),
                    BatchAssignSubjectsRequest.builder()
                            .subjectIds(request.getSubjectIds())
                            .effectiveFrom(enrollment.getEnrollmentDate())
                            .remarks("Assigned during enrollment")
                            .build()
            );
            enrolledSubjects = subjectEnrollmentRepository.findAllByEnrollmentId(enrollment.getId());
        }

        return EnrollmentMapper.toDetailResponse(enrollment, enrolledSubjects);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentAcademicEnrollment createEnrollmentEntity(Student student, CreateEnrollmentRequest request) {
        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(request.getAcademicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.getAcademicYearId()));

        AcademicClass cls = academicClassRepository.findByIdAndNotDeleted(request.getAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getAcademicClassId()));

        Section section = null;
        if (request.getSectionId() != null) {
            section = sectionRepository.findByIdAndNotDeleted(request.getSectionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Section", request.getSectionId()));

            // Section must belong to specified year and class
            if (!section.getAcademicYear().getId().equals(year.getId()) || !section.getAcademicClass().getId().equals(cls.getId())) {
                throw new BusinessException("Section does not belong to the specified academic year and class");
            }

            // Check capacity
            if (section.getCapacity() != null) {
                long count = enrollmentRepository.countActiveEnrollmentsBySectionId(section.getId());
                if (count >= section.getCapacity()) {
                    throw new BusinessException("Section capacity reached: " + section.getCapacity());
                }
            }
        }

        // Validate active enrollment uniqueness
        if (enrollmentRepository.existsActiveEnrollmentForStudentAndYear(student.getId(), year.getId())) {
            throw new DuplicateResourceException("StudentAcademicEnrollment", "academicYear",
                    "Student already has an active enrollment in academic year " + year.getCode());
        }

        // Validate roll number uniqueness if present
        if (request.getRollNumber() != null && !request.getRollNumber().isBlank()) {
            String roll = request.getRollNumber().trim();
            UUID sectionId = section != null ? section.getId() : null;
            if (enrollmentRepository.existsActiveRollNumber(year.getId(), cls.getId(), sectionId, roll, null)) {
                throw new DuplicateResourceException("StudentAcademicEnrollment", "rollNumber",
                        "Roll number " + roll + " is already assigned in this class/section");
            }
        }

        String enrollmentCode = CodeGenerator.enrollmentCode();
        while (enrollmentRepository.existsByEnrollmentCode(enrollmentCode)) {
            enrollmentCode = CodeGenerator.enrollmentCode();
        }

        StudentEnrollmentStatus status = request.getEnrollmentStatus() != null ? request.getEnrollmentStatus() : StudentEnrollmentStatus.ACTIVE;
        LocalDate admissionDate = request.getAdmissionDate() != null ? request.getAdmissionDate() : student.getAdmissionDate();
        LocalDate enrollmentDate = request.getEnrollmentDate() != null ? request.getEnrollmentDate() : LocalDate.now();

        StudentAcademicEnrollment enrollment = new StudentAcademicEnrollment(
                enrollmentCode,
                student,
                year,
                cls,
                section,
                request.getRollNumber(),
                status,
                admissionDate,
                enrollmentDate,
                request.getRemarks()
        );

        StudentAcademicEnrollment saved = enrollmentRepository.save(enrollment);
        log.info("Successfully created student academic enrollment ID: [{}] with code: [{}]", saved.getId(), saved.getEnrollmentCode());
        return saved;
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public EnrollmentDetailResponse getEnrollmentById(UUID enrollmentId) {
        StudentAcademicEnrollment enrollment = enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        List<StudentSubjectEnrollment> subjects = subjectEnrollmentRepository.findAllByEnrollmentId(enrollmentId);
        return EnrollmentMapper.toDetailResponse(enrollment, subjects);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public EnrollmentDetailResponse getEnrollmentByCode(String code) {
        StudentAcademicEnrollment enrollment = enrollmentRepository.findByEnrollmentCodeAndNotDeleted(code)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", code));

        List<StudentSubjectEnrollment> subjects = subjectEnrollmentRepository.findAllByEnrollmentId(enrollment.getId());
        return EnrollmentMapper.toDetailResponse(enrollment, subjects);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<EnrollmentResponse> getEnrollmentsByStudentId(UUID studentId) {
        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        return enrollmentRepository.findAllByStudentId(studentId)
                .stream()
                .map(EnrollmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public CurrentEnrollmentResponse getCurrentEnrollment(UUID studentId) {
        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        Optional<AcademicYear> currentYear = academicYearRepository.findCurrentYear();
        StudentAcademicEnrollment currentEnrollment = null;

        if (currentYear.isPresent()) {
            currentEnrollment = enrollmentRepository.findActiveEnrollment(studentId, currentYear.get().getId()).orElse(null);
        }

        if (currentEnrollment == null) {
            // Fallback to most recent active enrollment
            List<StudentAcademicEnrollment> list = enrollmentRepository.findAllByStudentId(studentId);
            currentEnrollment = list.stream()
                    .filter(e -> e.getEnrollmentStatus().isActiveOrAdmitted())
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", "No active enrollment found for student " + studentId));
        }

        List<StudentSubjectEnrollment> subjects = subjectEnrollmentRepository.findAllByEnrollmentId(currentEnrollment.getId());
        return EnrollmentMapper.toCurrentResponse(currentEnrollment, subjects);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public EnrollmentDetailResponse updateEnrollment(UUID enrollmentId, UpdateEnrollmentRequest request) {
        log.info("Updating enrollment ID: [{}]", enrollmentId);

        StudentAcademicEnrollment enrollment = enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        if (request.getSectionId() != null) {
            Section section = sectionRepository.findByIdAndNotDeleted(request.getSectionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Section", request.getSectionId()));

            if (!section.getAcademicYear().getId().equals(enrollment.getAcademicYear().getId()) ||
                    !section.getAcademicClass().getId().equals(enrollment.getAcademicClass().getId())) {
                throw new BusinessException("Section does not belong to the enrollment's academic year and class");
            }
            enrollment.setSection(section);
        }

        if (request.getRollNumber() != null) {
            String newRoll = request.getRollNumber().trim();
            UUID sectionId = enrollment.getSection() != null ? enrollment.getSection().getId() : null;
            if (!newRoll.equalsIgnoreCase(enrollment.getRollNumber())) {
                if (enrollmentRepository.existsActiveRollNumber(
                        enrollment.getAcademicYear().getId(),
                        enrollment.getAcademicClass().getId(),
                        sectionId,
                        newRoll,
                        enrollment.getId())) {
                    throw new DuplicateResourceException("StudentAcademicEnrollment", "rollNumber",
                            "Roll number " + newRoll + " is already assigned in this class/section");
                }
                enrollment.setRollNumber(newRoll);
            }
        }

        if (request.getEnrollmentDate() != null) {
            enrollment.setEnrollmentDate(request.getEnrollmentDate());
        }

        if (request.getRemarks() != null) {
            enrollment.setRemarks(request.getRemarks().trim());
        }

        StudentAcademicEnrollment saved = enrollmentRepository.save(enrollment);
        log.info("Successfully updated enrollment ID: [{}]", saved.getId());
        List<StudentSubjectEnrollment> subjects = subjectEnrollmentRepository.findAllByEnrollmentId(saved.getId());
        return EnrollmentMapper.toDetailResponse(saved, subjects);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public EnrollmentDetailResponse withdrawStudent(UUID enrollmentId, WithdrawEnrollmentRequest request) {
        log.info("Withdrawing student from enrollment ID: [{}]", enrollmentId);

        StudentAcademicEnrollment enrollment = enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        enrollment.withdraw(request.getWithdrawalDate(), request.getWithdrawalReason(), request.getRemarks());
        StudentAcademicEnrollment saved = enrollmentRepository.save(enrollment);

        log.info("Successfully withdrew enrollment ID: [{}]", saved.getId());
        List<StudentSubjectEnrollment> subjects = subjectEnrollmentRepository.findAllByEnrollmentId(saved.getId());
        return EnrollmentMapper.toDetailResponse(saved, subjects);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public EnrollmentDetailResponse transferStudent(UUID enrollmentId, TransferEnrollmentRequest request) {
        log.info("Transferring student enrollment ID: [{}] to class: [{}], section: [{}]",
                enrollmentId, request.getNewAcademicClassId(), request.getNewSectionId());

        StudentAcademicEnrollment oldEnrollment = enrollmentRepository.findByIdAndNotDeleted(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAcademicEnrollment", enrollmentId));

        if (!oldEnrollment.getEnrollmentStatus().isActiveOrAdmitted()) {
            throw new BusinessException("Can only transfer an ACTIVE or ADMITTED student enrollment");
        }

        AcademicClass newClass = academicClassRepository.findByIdAndNotDeleted(request.getNewAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getNewAcademicClassId()));

        Section newSection = null;
        if (request.getNewSectionId() != null) {
            newSection = sectionRepository.findByIdAndNotDeleted(request.getNewSectionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Section", request.getNewSectionId()));

            if (!newSection.getAcademicYear().getId().equals(oldEnrollment.getAcademicYear().getId()) ||
                    !newSection.getAcademicClass().getId().equals(newClass.getId())) {
                throw new BusinessException("Target section does not belong to the academic year and target class");
            }
            if (newSection.getCapacity() != null) {
                long currentCount = enrollmentRepository.countActiveEnrollmentsBySectionId(newSection.getId());
                if (currentCount >= newSection.getCapacity()) {
                    throw new BusinessException("Target section capacity reached: " + newSection.getCapacity());
                }
            }
        }

        LocalDate transferDate = request.getTransferDate() != null ? request.getTransferDate() : LocalDate.now();

        // End old enrollment
        oldEnrollment.transfer(transferDate, request.getRemarks());
        enrollmentRepository.save(oldEnrollment);

        // Validate roll number uniqueness for new enrollment if provided
        if (request.getNewRollNumber() != null && !request.getNewRollNumber().isBlank()) {
            String roll = request.getNewRollNumber().trim();
            UUID sectionId = newSection != null ? newSection.getId() : null;
            if (enrollmentRepository.existsActiveRollNumber(
                    oldEnrollment.getAcademicYear().getId(),
                    newClass.getId(),
                    sectionId,
                    roll,
                    null)) {
                throw new DuplicateResourceException("StudentAcademicEnrollment", "rollNumber",
                        "Roll number " + roll + " is already assigned in target class/section");
            }
        }

        String newEnrollmentCode = CodeGenerator.enrollmentCode();
        while (enrollmentRepository.existsByEnrollmentCode(newEnrollmentCode)) {
            newEnrollmentCode = CodeGenerator.enrollmentCode();
        }

        StudentAcademicEnrollment newEnrollment = new StudentAcademicEnrollment(
                newEnrollmentCode,
                oldEnrollment.getStudent(),
                oldEnrollment.getAcademicYear(),
                newClass,
                newSection,
                request.getNewRollNumber(),
                StudentEnrollmentStatus.ACTIVE,
                oldEnrollment.getAdmissionDate(),
                transferDate,
                request.getRemarks()
        );

        StudentAcademicEnrollment saved = enrollmentRepository.save(newEnrollment);

        // Auto-assign compulsory subjects for the new class
        List<StudentSubjectEnrollment> newSubjects = subjectEnrollmentService.autoEnrollCompulsorySubjects(saved);

        log.info("Successfully transferred student to new enrollment ID: [{}]", saved.getId());
        return EnrollmentMapper.toDetailResponse(saved, newSubjects);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<EnrollmentResponse> searchEnrollments(
            UUID studentId,
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            StudentEnrollmentStatus status,
            String enrollmentCode,
            Pageable pageable) {
        return enrollmentRepository.searchEnrollments(studentId, academicYearId, academicClassId, sectionId, status, enrollmentCode, pageable)
                .map(EnrollmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<ClassSectionStudentResponse> getClassSectionStudents(
            UUID classId,
            UUID sectionId,
            UUID academicYearId,
            StudentEnrollmentStatus status,
            String searchQuery,
            Pageable pageable) {
        academicClassRepository.findByIdAndNotDeleted(classId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", classId));

        if (sectionId != null) {
            sectionRepository.findByIdAndNotDeleted(sectionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
        }

        return enrollmentRepository.searchClassSectionStudents(classId, sectionId, academicYearId, status, searchQuery, pageable)
                .map(EnrollmentMapper::toClassSectionStudentResponse);
    }
}
