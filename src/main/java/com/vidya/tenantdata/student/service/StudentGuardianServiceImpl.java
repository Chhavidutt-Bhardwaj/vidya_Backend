package com.vidya.tenantdata.student.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.student.dto.CreateStudentGuardianRequest;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentGuardianRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentGuardian;
import com.vidya.tenantdata.student.mapper.StudentGuardianMapper;
import com.vidya.tenantdata.student.repository.StudentGuardianRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentGuardianServiceImpl implements StudentGuardianService {

    private final StudentGuardianRepository guardianRepository;
    private final StudentRepository studentRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentGuardianResponse createGuardian(UUID studentId, CreateStudentGuardianRequest request) {
        log.info("Adding guardian for student ID: [{}]", studentId);

        Student student = studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        StudentGuardian guardian = createGuardianEntity(student, request);
        return StudentGuardianMapper.toResponse(guardian);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentGuardian createGuardianEntity(Student student, CreateStudentGuardianRequest request) {
        // If this is set as primary, unmark existing primary guardians for this student
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            Optional<StudentGuardian> existingPrimary = guardianRepository.findPrimaryGuardian(student.getId());
            existingPrimary.ifPresent(p -> {
                p.setPrimary(false);
                guardianRepository.save(p);
            });
        }

        String code = CodeGenerator.guardianCode();
        while (guardianRepository.existsByGuardianCode(code)) {
            code = CodeGenerator.guardianCode();
        }

        StudentGuardian entity = StudentGuardianMapper.toEntity(request, student, code);
        StudentGuardian saved = guardianRepository.save(entity);
        log.info("Saved guardian ID: [{}] with code: [{}] for student: [{}]", saved.getId(), saved.getGuardianCode(), student.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<StudentGuardianResponse> getGuardiansByStudentId(UUID studentId) {
        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        return guardianRepository.findAllByStudentId(studentId)
                .stream()
                .map(StudentGuardianMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentGuardianResponse getGuardianById(UUID studentId, UUID guardianId) {
        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        StudentGuardian guardian = guardianRepository.findByIdAndNotDeleted(guardianId)
                .filter(g -> g.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentGuardian", guardianId));

        return StudentGuardianMapper.toResponse(guardian);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public StudentGuardianResponse updateGuardian(UUID studentId, UUID guardianId, UpdateStudentGuardianRequest request) {
        log.info("Updating guardian ID: [{}] for student: [{}]", guardianId, studentId);

        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        StudentGuardian guardian = guardianRepository.findByIdAndNotDeleted(guardianId)
                .filter(g -> g.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentGuardian", guardianId));

        if (request.getGuardianName() != null && !request.getGuardianName().isBlank()) {
            guardian.setGuardianName(request.getGuardianName().trim());
        }
        if (request.getRelationshipType() != null) {
            guardian.setRelationshipType(request.getRelationshipType());
        }
        if (request.getPhone() != null) {
            guardian.setPhone(request.getPhone().trim());
        }
        if (request.getEmail() != null) {
            guardian.setEmail(request.getEmail().trim());
        }
        if (request.getOccupation() != null) {
            guardian.setOccupation(request.getOccupation().trim());
        }
        if (request.getAnnualIncome() != null) {
            guardian.setAnnualIncome(request.getAnnualIncome());
        }
        if (request.getAddressLine1() != null) {
            guardian.setAddressLine1(request.getAddressLine1().trim());
        }
        if (request.getAddressLine2() != null) {
            guardian.setAddressLine2(request.getAddressLine2().trim());
        }
        if (request.getCity() != null) {
            guardian.setCity(request.getCity().trim());
        }
        if (request.getState() != null) {
            guardian.setState(request.getState().trim());
        }
        if (request.getPostalCode() != null) {
            guardian.setPostalCode(request.getPostalCode().trim());
        }
        if (request.getIsPrimary() != null) {
            if (Boolean.TRUE.equals(request.getIsPrimary()) && !guardian.isPrimary()) {
                guardianRepository.findPrimaryGuardian(studentId)
                        .ifPresent(p -> {
                            p.setPrimary(false);
                            guardianRepository.save(p);
                        });
            }
            guardian.setPrimary(request.getIsPrimary());
        }
        if (request.getIsEmergencyContact() != null) {
            guardian.setEmergencyContact(request.getIsEmergencyContact());
        }
        if (request.getStatus() != null) {
            guardian.updateStatus(request.getStatus());
        }

        StudentGuardian saved = guardianRepository.save(guardian);
        log.info("Updated guardian ID: [{}]", saved.getId());
        return StudentGuardianMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteGuardian(UUID studentId, UUID guardianId) {
        log.info("Soft-deleting guardian ID: [{}] for student: [{}]", guardianId, studentId);

        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        StudentGuardian guardian = guardianRepository.findByIdAndNotDeleted(guardianId)
                .filter(g -> g.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("StudentGuardian", guardianId));

        guardian.softDelete();
        guardianRepository.save(guardian);
        log.info("Successfully soft-deleted guardian ID: [{}]", guardianId);
    }
}
