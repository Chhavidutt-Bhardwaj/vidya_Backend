package com.vidya.tenantdata.student.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.student.dto.AdmissionResponse;
import com.vidya.tenantdata.student.dto.BatchAssignSubjectsRequest;
import com.vidya.tenantdata.student.dto.CreateAdmissionRequest;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.CreateStudentGuardianRequest;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.dto.StudentSubjectEnrollmentResponse;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentGuardian;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollment;
import com.vidya.tenantdata.student.mapper.EnrollmentMapper;
import com.vidya.tenantdata.student.mapper.StudentGuardianMapper;
import com.vidya.tenantdata.student.mapper.StudentMapper;
import com.vidya.tenantdata.student.mapper.StudentSubjectEnrollmentMapper;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.repository.StudentSubjectEnrollmentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAdmissionServiceImpl implements StudentAdmissionService {

    private final StudentRepository studentRepository;
    private final TenantUserRepository tenantUserRepository;
    private final StudentEnrollmentService enrollmentService;
    private final StudentGuardianService guardianService;
    private final StudentSubjectEnrollmentService subjectEnrollmentService;
    private final StudentSubjectEnrollmentRepository subjectEnrollmentRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AdmissionResponse admitStudent(CreateAdmissionRequest request) {
        log.info("Processing admission for admission number: [{}]", request.getAdmissionNumber());

        String admissionNumber = request.getAdmissionNumber().trim();
        if (studentRepository.existsByAdmissionNumber(admissionNumber)) {
            throw new DuplicateResourceException("Student", "admissionNumber", admissionNumber);
        }

        // 1. Generate unique student code and tenant user code
        String studentCode = CodeGenerator.studentCode();
        while (studentRepository.existsByStudentCode(studentCode)) {
            studentCode = CodeGenerator.studentCode();
        }

        String userCode = CodeGenerator.userCode();
        while (tenantUserRepository.existsByUserCode(userCode)) {
            userCode = CodeGenerator.userCode();
        }

        // 2. Generate or sanitize email
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            email = "student." + studentCode.toLowerCase() + "@school.vidya.internal";
        } else {
            email = email.trim().toLowerCase();
        }
        if (tenantUserRepository.existsByEmailIgnoreCase(email)) {
            email = "student." + studentCode.toLowerCase() + "." + CodeGenerator.generate(4).toLowerCase() + "@school.vidya.internal";
        }

        // 3. Create TenantUser and Profile
        TenantUser tenantUser = new TenantUser(userCode, email, request.getPhone(), TenantUserType.STUDENT);
        TenantUserProfile profile = new TenantUserProfile(
                tenantUser,
                request.getFirstName() != null ? request.getFirstName().trim() : "Student",
                request.getLastName() != null ? request.getLastName().trim() : null
        );
        profile.setDisplayName(request.getDisplayName());
        profile.setGender(request.getGender());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setAddressLine2(request.getAddressLine2());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setPostalCode(request.getPostalCode());
        tenantUser.setProfile(profile);

        TenantUser savedUser = tenantUserRepository.save(tenantUser);

        // 4. Create Student Identity
        Student student = new Student(studentCode, savedUser, admissionNumber, request.getAdmissionDate());
        Student savedStudent = studentRepository.save(student);

        // 5. Create Guardians if provided
        List<StudentGuardianResponse> guardianResponses = new ArrayList<>();
        if (request.getGuardians() != null && !request.getGuardians().isEmpty()) {
            for (CreateStudentGuardianRequest gReq : request.getGuardians()) {
                StudentGuardian g = guardianService.createGuardianEntity(savedStudent, gReq);
                guardianResponses.add(StudentGuardianMapper.toResponse(g));
            }
        }

        // 6. Create Academic Enrollment
        LocalDate enrollmentDate = request.getEnrollmentDate() != null ? request.getEnrollmentDate() : request.getAdmissionDate();
        CreateEnrollmentRequest enrollmentReq = CreateEnrollmentRequest.builder()
                .academicYearId(request.getAcademicYearId())
                .academicClassId(request.getAcademicClassId())
                .sectionId(request.getSectionId())
                .rollNumber(request.getRollNumber())
                .enrollmentStatus(StudentEnrollmentStatus.ACTIVE)
                .admissionDate(request.getAdmissionDate())
                .enrollmentDate(enrollmentDate)
                .remarks(request.getRemarks())
                .build();

        StudentAcademicEnrollment enrollment = enrollmentService.createEnrollmentEntity(savedStudent, enrollmentReq);

        // 7. Enrolls Subjects (Compulsory + Electives)
        List<StudentSubjectEnrollment> enrolledSubjects = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getAutoAssignCompulsorySubjects())) {
            enrolledSubjects.addAll(subjectEnrollmentService.autoEnrollCompulsorySubjects(enrollment));
        }
        if (request.getSubjectIds() != null && !request.getSubjectIds().isEmpty()) {
            subjectEnrollmentService.batchEnrollSubjects(
                    enrollment.getId(),
                    BatchAssignSubjectsRequest.builder()
                            .subjectIds(request.getSubjectIds())
                            .effectiveFrom(enrollmentDate)
                            .remarks("Assigned during admission")
                            .build()
            );
            enrolledSubjects = subjectEnrollmentRepository.findAllByEnrollmentId(enrollment.getId());
        }

        List<StudentSubjectEnrollmentResponse> subjectResponses = enrolledSubjects.stream()
                .map(StudentSubjectEnrollmentMapper::toResponse)
                .toList();

        log.info("Successfully completed admission for student ID: [{}], Code: [{}], Admission: [{}]",
                savedStudent.getId(), savedStudent.getStudentCode(), savedStudent.getAdmissionNumber());

        return AdmissionResponse.builder()
                .student(StudentMapper.toDetailResponse(savedStudent))
                .enrollment(EnrollmentMapper.toResponse(enrollment))
                .enrolledSubjects(subjectResponses)
                .guardians(guardianResponses)
                .build();
    }
}
