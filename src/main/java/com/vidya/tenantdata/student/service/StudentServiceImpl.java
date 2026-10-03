package com.vidya.tenantdata.student.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.dto.StudentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentRequest;
import com.vidya.tenantdata.student.dto.UpdateStudentStatusRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.mapper.StudentMapper;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.dto.StudentProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of Student business operations in the tenant database schema.
 */
@Slf4j
@Service
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final TenantUserRepository tenantUserRepository;
    private final StudentEnrollmentService enrollmentService;
    private final StudentGuardianService guardianService;

    public StudentServiceImpl(
            StudentRepository studentRepository,
            TenantUserRepository tenantUserRepository,
            @Lazy StudentEnrollmentService enrollmentService,
            @Lazy StudentGuardianService guardianService) {
        this.studentRepository = studentRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.enrollmentService = enrollmentService;
        this.guardianService = guardianService;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentDetailResponse createStudent(CreateStudentRequest request) {
        log.info("Creating student with admission number: [{}] for tenantUserId: [{}]",
                request.getAdmissionNumber(), request.getTenantUserId());

        TenantUser tenantUser = tenantUserRepository.findByIdAndNotDeleted(request.getTenantUserId())
                .orElseThrow(() -> new ResourceNotFoundException("TenantUser", request.getTenantUserId()));

        if (studentRepository.existsByTenantUserId(request.getTenantUserId())) {
            throw new DuplicateResourceException("Student", "tenantUserId", request.getTenantUserId());
        }

        String admissionNumber = request.getAdmissionNumber().trim();
        if (studentRepository.existsByAdmissionNumber(admissionNumber)) {
            throw new DuplicateResourceException("Student", "admissionNumber", admissionNumber);
        }

        if (tenantUser.getUserType() != TenantUserType.STUDENT) {
            tenantUser.setUserType(TenantUserType.STUDENT);
            tenantUserRepository.save(tenantUser);
        }

        String studentCode = CodeGenerator.studentCode();
        while (studentRepository.existsByStudentCode(studentCode)) {
            studentCode = CodeGenerator.studentCode();
        }

        Student student = StudentMapper.toEntity(request, tenantUser, studentCode);
        Student saved = studentRepository.save(student);

        log.info("Successfully created student ID: [{}] with code: [{}] and admission: [{}]",
                saved.getId(), saved.getStudentCode(), saved.getAdmissionNumber());
        return StudentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentDetailResponse getStudentById(UUID id) {
        Student student = studentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
        return StudentMapper.toDetailResponse(student);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentDetailResponse getStudentByStudentCode(String studentCode) {
        Student student = studentRepository.findByStudentCodeAndNotDeleted(studentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentCode));
        return StudentMapper.toDetailResponse(student);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentDetailResponse getStudentByAdmissionNumber(String admissionNumber) {
        Student student = studentRepository.findByAdmissionNumberAndNotDeleted(admissionNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Student", admissionNumber));
        return StudentMapper.toDetailResponse(student);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentDetailResponse getStudentByTenantUserId(UUID tenantUserId) {
        Student student = studentRepository.findByTenantUserId(tenantUserId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Student", tenantUserId));
        return StudentMapper.toDetailResponse(student);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentDetailResponse updateStudent(UUID id, UpdateStudentRequest request) {
        log.info("Updating student ID: [{}]", id);

        Student student = studentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));

        if (request.getAdmissionNumber() != null && !request.getAdmissionNumber().isBlank()) {
            String newAdmission = request.getAdmissionNumber().trim();
            if (!newAdmission.equalsIgnoreCase(student.getAdmissionNumber())) {
                if (studentRepository.existsByAdmissionNumber(newAdmission)) {
                    throw new DuplicateResourceException("Student", "admissionNumber", newAdmission);
                }
                student.setAdmissionNumber(newAdmission);
            }
        }

        if (request.getAdmissionDate() != null) {
            student.setAdmissionDate(request.getAdmissionDate());
        }

        Student saved = studentRepository.save(student);
        log.info("Successfully updated student ID: [{}]", saved.getId());
        return StudentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentDetailResponse updateStudentStatus(UUID id, UpdateStudentStatusRequest request) {
        log.info("Updating student ID: [{}] status to: [{}]", id, request.getStatus());

        Student student = studentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));

        student.updateStatus(request.getStatus());
        Student saved = studentRepository.save(student);

        log.info("Successfully transitioned student ID: [{}] to status: [{}]", saved.getId(), saved.getStatus());
        return StudentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteStudent(UUID id) {
        log.info("Soft-deleting student ID: [{}]", id);

        Student student = studentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));

        student.softDelete();
        studentRepository.save(student);
        log.info("Successfully soft-deleted student ID: [{}]", id);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<StudentResponse> searchStudents(StudentStatus status, String admissionNumber, String studentCode, String searchQuery, Pageable pageable) {
        return studentRepository.searchStudents(status, admissionNumber, studentCode, searchQuery, pageable)
                .map(StudentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentProfileResponse getStudentProfile(UUID id) {
        Student student = studentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));

        StudentDetailResponse studentDetail = StudentMapper.toDetailResponse(student);

        CurrentEnrollmentResponse currentEnrollment = null;
        try {
            currentEnrollment = enrollmentService.getCurrentEnrollment(id);
        } catch (ResourceNotFoundException ex) {
            log.debug("No current active enrollment for student ID: [{}]", id);
        }

        List<StudentGuardianResponse> guardians = guardianService.getGuardiansByStudentId(id);

        return StudentProfileResponse.builder()
                .student(studentDetail)
                .currentEnrollment(currentEnrollment)
                .guardians(guardians)
                .build();
    }
}
