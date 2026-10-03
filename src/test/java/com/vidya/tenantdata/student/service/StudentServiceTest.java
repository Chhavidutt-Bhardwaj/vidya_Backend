package com.vidya.tenantdata.student.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.dto.StudentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentRequest;
import com.vidya.tenantdata.student.dto.UpdateStudentStatusRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentService — Unit Tests")
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private com.vidya.tenantdata.student.service.StudentEnrollmentService enrollmentService;

    @Mock
    private com.vidya.tenantdata.student.service.StudentGuardianService guardianService;

    @InjectMocks
    private StudentServiceImpl studentService;

    private UUID tenantUserId;
    private UUID studentId;
    private TenantUser sampleUser;
    private Student sampleStudent;

    @BeforeEach
    void setUp() {
        tenantUserId = UUID.randomUUID();
        studentId = UUID.randomUUID();

        sampleUser = new TenantUser("USR-12345678", "student@school.edu.in", "+919876543210", TenantUserType.STUDENT);
        sampleUser.setId(tenantUserId);
        TenantUserProfile profile = new TenantUserProfile(sampleUser, "Aarav", "Sharma");
        sampleUser.setProfile(profile);

        sampleStudent = new Student("STU-87654321", sampleUser, "ADM-2026-001", LocalDate.of(2026, 6, 1));
        sampleStudent.setId(studentId);
    }

    @Test
    @DisplayName("Create student successfully when valid tenant user and unique admission number")
    void createStudent_Success() {
        CreateStudentRequest request = CreateStudentRequest.builder()
                .tenantUserId(tenantUserId)
                .admissionNumber("ADM-2026-001")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));
        when(studentRepository.existsByTenantUserId(tenantUserId)).thenReturn(false);
        when(studentRepository.existsByAdmissionNumber("ADM-2026-001")).thenReturn(false);
        when(studentRepository.existsByStudentCode(anyString())).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            s.setId(studentId);
            return s;
        });

        StudentDetailResponse response = studentService.createStudent(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(studentId);
        assertThat(response.getAdmissionNumber()).isEqualTo("ADM-2026-001");
        assertThat(response.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("student@school.edu.in");
    }

    @Test
    @DisplayName("Create student throws ResourceNotFoundException when tenant user does not exist")
    void createStudent_TenantUserNotFound() {
        CreateStudentRequest request = CreateStudentRequest.builder()
                .tenantUserId(tenantUserId)
                .admissionNumber("ADM-2026-001")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.createStudent(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Create student throws DuplicateResourceException when tenant user already has student record")
    void createStudent_DuplicateTenantUser() {
        CreateStudentRequest request = CreateStudentRequest.builder()
                .tenantUserId(tenantUserId)
                .admissionNumber("ADM-2026-001")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));
        when(studentRepository.existsByTenantUserId(tenantUserId)).thenReturn(true);

        assertThatThrownBy(() -> studentService.createStudent(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Create student throws DuplicateResourceException when admission number is already taken")
    void createStudent_DuplicateAdmissionNumber() {
        CreateStudentRequest request = CreateStudentRequest.builder()
                .tenantUserId(tenantUserId)
                .admissionNumber("ADM-2026-001")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));
        when(studentRepository.existsByTenantUserId(tenantUserId)).thenReturn(false);
        when(studentRepository.existsByAdmissionNumber("ADM-2026-001")).thenReturn(true);

        assertThatThrownBy(() -> studentService.createStudent(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Get student by ID returns detail response when found")
    void getStudentById_Success() {
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));

        StudentDetailResponse response = studentService.getStudentById(studentId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(studentId);
        assertThat(response.getStudentCode()).isEqualTo("STU-87654321");
    }

    @Test
    @DisplayName("Get student by ID throws ResourceNotFoundException when not found")
    void getStudentById_NotFound() {
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.getStudentById(studentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get student by studentCode returns detail response")
    void getStudentByStudentCode_Success() {
        when(studentRepository.findByStudentCodeAndNotDeleted("STU-87654321")).thenReturn(Optional.of(sampleStudent));

        StudentDetailResponse response = studentService.getStudentByStudentCode("STU-87654321");

        assertThat(response).isNotNull();
        assertThat(response.getStudentCode()).isEqualTo("STU-87654321");
    }

    @Test
    @DisplayName("Get student by admissionNumber returns detail response")
    void getStudentByAdmissionNumber_Success() {
        when(studentRepository.findByAdmissionNumberAndNotDeleted("ADM-2026-001")).thenReturn(Optional.of(sampleStudent));

        StudentDetailResponse response = studentService.getStudentByAdmissionNumber("ADM-2026-001");

        assertThat(response).isNotNull();
        assertThat(response.getAdmissionNumber()).isEqualTo("ADM-2026-001");
    }

    @Test
    @DisplayName("Update student admission details successfully")
    void updateStudent_Success() {
        UpdateStudentRequest request = UpdateStudentRequest.builder()
                .admissionNumber("ADM-2026-002")
                .admissionDate(LocalDate.of(2026, 7, 1))
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(studentRepository.existsByAdmissionNumber("ADM-2026-002")).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentDetailResponse response = studentService.updateStudent(studentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getAdmissionNumber()).isEqualTo("ADM-2026-002");
        assertThat(response.getAdmissionDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    @DisplayName("Update student throws DuplicateResourceException on conflicting admission number")
    void updateStudent_DuplicateAdmissionNumber() {
        UpdateStudentRequest request = UpdateStudentRequest.builder()
                .admissionNumber("ADM-DUPLICATE")
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(studentRepository.existsByAdmissionNumber("ADM-DUPLICATE")).thenReturn(true);

        assertThatThrownBy(() -> studentService.updateStudent(studentId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Update student status transitions successfully")
    void updateStudentStatus_Success() {
        UpdateStudentStatusRequest request = UpdateStudentStatusRequest.builder()
                .status(StudentStatus.SUSPENDED)
                .reason("Disciplinary action")
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentDetailResponse response = studentService.updateStudentStatus(studentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(StudentStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Update student status throws InvalidStateTransitionException on invalid transition")
    void updateStudentStatus_InvalidTransition() {
        sampleStudent.setStatus(StudentStatus.GRADUATED);

        UpdateStudentStatusRequest request = UpdateStudentStatusRequest.builder()
                .status(StudentStatus.ACTIVE)
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));

        assertThatThrownBy(() -> studentService.updateStudentStatus(studentId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Soft delete student marks status DELETED and deletedAt timestamp")
    void deleteStudent_Success() {
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        studentService.deleteStudent(studentId);

        assertThat(sampleStudent.getStatus()).isEqualTo(StudentStatus.DELETED);
        assertThat(sampleStudent.isDeleted()).isTrue();
        assertThat(sampleStudent.getDeletedAt()).isNotNull();
        verify(studentRepository).save(sampleStudent);
    }

    @Test
    @DisplayName("Search students returns paginated summary responses")
    void searchStudents_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Student> page = new PageImpl<>(List.of(sampleStudent), pageable, 1);

        when(studentRepository.searchStudents(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<StudentResponse> result = studentService.searchStudents(StudentStatus.ACTIVE, null, null, "Aarav", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStudentCode()).isEqualTo("STU-87654321");
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("Aarav Sharma");
    }
}
