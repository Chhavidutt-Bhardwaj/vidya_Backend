package com.vidya.tenantdata.student;

import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.dto.StudentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentRequest;
import com.vidya.tenantdata.student.dto.UpdateStudentStatusRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.service.StudentServiceImpl;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Student — End-to-End Lifecycle Flow Integration Tests")
class StudentLifecycleIntegrationTest {

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
    private TenantUser tenantUser;
    private Student student;

    @BeforeEach
    void setUp() {
        tenantUserId = UUID.randomUUID();
        studentId = UUID.randomUUID();

        tenantUser = new TenantUser("USR-STU-001", "student.lifecycle@greenwood.edu.in", "+919876543210", TenantUserType.STUDENT);
        tenantUser.setId(tenantUserId);
        TenantUserProfile profile = new TenantUserProfile(tenantUser, "Rohan", "Verma");
        tenantUser.setProfile(profile);

        student = new Student("STU-2026-0001", tenantUser, "ADM-2026-100", LocalDate.of(2026, 6, 1));
        student.setId(studentId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Complete Student Lifecycle: Enroll -> Fetch -> Update -> Status Change -> Search -> Soft Delete")
    void testCompleteStudentLifecycleFlow() {
        TenantContext.setTenantCode("greenwood_high");

        // 1. Enroll / Create Student
        CreateStudentRequest createReq = CreateStudentRequest.builder()
                .tenantUserId(tenantUserId)
                .admissionNumber("ADM-2026-100")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(tenantUser));
        when(studentRepository.existsByTenantUserId(tenantUserId)).thenReturn(false);
        when(studentRepository.existsByAdmissionNumber("ADM-2026-100")).thenReturn(false);
        when(studentRepository.existsByStudentCode(anyString())).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            s.setId(studentId);
            return s;
        });

        StudentDetailResponse created = studentService.createStudent(createReq);
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(studentId);
        assertThat(created.getAdmissionNumber()).isEqualTo("ADM-2026-100");
        assertThat(created.getStatus()).isEqualTo(StudentStatus.ACTIVE);

        // 2. Fetch Student by ID & Code
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(studentRepository.findByStudentCodeAndNotDeleted("STU-2026-0001")).thenReturn(Optional.of(student));

        StudentDetailResponse fetchedById = studentService.getStudentById(studentId);
        StudentDetailResponse fetchedByCode = studentService.getStudentByStudentCode("STU-2026-0001");

        assertThat(fetchedById.getId()).isEqualTo(studentId);
        assertThat(fetchedByCode.getStudentCode()).isEqualTo("STU-2026-0001");
        assertThat(fetchedById.getUser().getProfile().getFirstName()).isEqualTo("Rohan");

        // 3. Update Student details
        UpdateStudentRequest updateReq = UpdateStudentRequest.builder()
                .admissionNumber("ADM-2026-100-REV")
                .admissionDate(LocalDate.of(2026, 6, 5))
                .build();

        when(studentRepository.existsByAdmissionNumber("ADM-2026-100-REV")).thenReturn(false);

        StudentDetailResponse updated = studentService.updateStudent(studentId, updateReq);
        assertThat(updated.getAdmissionNumber()).isEqualTo("ADM-2026-100-REV");
        assertThat(updated.getAdmissionDate()).isEqualTo(LocalDate.of(2026, 6, 5));

        // 4. Change Status to SUSPENDED then back to ACTIVE
        UpdateStudentStatusRequest suspendReq = UpdateStudentStatusRequest.builder()
                .status(StudentStatus.SUSPENDED)
                .reason("Medical Leave")
                .build();

        StudentDetailResponse suspended = studentService.updateStudentStatus(studentId, suspendReq);
        assertThat(suspended.getStatus()).isEqualTo(StudentStatus.SUSPENDED);

        UpdateStudentStatusRequest activateReq = UpdateStudentStatusRequest.builder()
                .status(StudentStatus.ACTIVE)
                .build();

        StudentDetailResponse reactivated = studentService.updateStudentStatus(studentId, activateReq);
        assertThat(reactivated.getStatus()).isEqualTo(StudentStatus.ACTIVE);

        // 5. Search Student
        Pageable pageable = PageRequest.of(0, 10);
        when(studentRepository.searchStudents(eq(StudentStatus.ACTIVE), any(), any(), eq("Rohan"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(student), pageable, 1));

        Page<StudentResponse> searchResult = studentService.searchStudents(StudentStatus.ACTIVE, null, null, "Rohan", pageable);
        assertThat(searchResult.getContent()).hasSize(1);
        assertThat(searchResult.getContent().get(0).getFullName()).isEqualTo("Rohan Verma");

        // 6. Soft Delete Student
        studentService.deleteStudent(studentId);
        assertThat(student.getStatus()).isEqualTo(StudentStatus.DELETED);
        assertThat(student.isDeleted()).isTrue();
        assertThat(student.getDeletedAt()).isNotNull();
    }
}
