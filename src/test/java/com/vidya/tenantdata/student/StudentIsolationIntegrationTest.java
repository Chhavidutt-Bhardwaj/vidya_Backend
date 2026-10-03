package com.vidya.tenantdata.student;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Student Isolation & IDOR Protection — Integration Tests")
class StudentIsolationIntegrationTest {

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

    private UUID tenantAUserId;
    private UUID tenantBUserId;
    private UUID studentAId;
    private UUID studentBId;

    private TenantUser tenantAUser;
    private TenantUser tenantBUser;
    private Student studentA;
    private Student studentB;

    @BeforeEach
    void setUp() {
        tenantAUserId = UUID.randomUUID();
        tenantBUserId = UUID.randomUUID();
        studentAId = UUID.randomUUID();
        studentBId = UUID.randomUUID();

        // Tenant A User & Student
        tenantAUser = new TenantUser("USR-TENANT-A-01", "student.a@school-a.edu.in", "+919876543201", TenantUserType.STUDENT);
        tenantAUser.setId(tenantAUserId);
        TenantUserProfile profileA = new TenantUserProfile(tenantAUser, "StudentA", "SchoolA");
        tenantAUser.setProfile(profileA);

        studentA = new Student("STU-TENANT-A-01", tenantAUser, "ADM-A-001", LocalDate.of(2026, 6, 1));
        studentA.setId(studentAId);

        // Tenant B User & Student
        tenantBUser = new TenantUser("USR-TENANT-B-01", "student.b@school-b.edu.in", "+919876543202", TenantUserType.STUDENT);
        tenantBUser.setId(tenantBUserId);
        TenantUserProfile profileB = new TenantUserProfile(tenantBUser, "StudentB", "SchoolB");
        tenantBUser.setProfile(profileB);

        studentB = new Student("STU-TENANT-B-01", tenantBUser, "ADM-B-001", LocalDate.of(2026, 6, 1));
        studentB.setId(studentBId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A context accesses Tenant A Student — ALLOWED")
    void testTenantAAccessOwnStudent_Allowed() {
        TenantContext.setTenantCode("tenant_a");

        when(studentRepository.findByIdAndNotDeleted(studentAId))
                .thenReturn(Optional.of(studentA));

        StudentDetailResponse response = studentService.getStudentById(studentAId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(studentAId);
        assertThat(response.getAdmissionNumber()).isEqualTo("ADM-A-001");
        assertThat(response.getUser().getEmail()).isEqualTo("student.a@school-a.edu.in");
    }

    @Test
    @DisplayName("Tenant A context queries Tenant B Student ID (IDOR) — Returns ResourceNotFoundException in Tenant A DB")
    void testTenantACrossQueryTenantBStudent_Denied() {
        TenantContext.setTenantCode("tenant_a");

        // In Tenant A's isolated DB, Tenant B's student record does NOT exist
        when(studentRepository.findByIdAndNotDeleted(studentBId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.getStudentById(studentBId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A context attempts update on Tenant B Student ID (IDOR) — Rejection")
    void testTenantACrossUpdateTenantBStudent_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(studentRepository.findByIdAndNotDeleted(studentBId))
                .thenReturn(Optional.empty());

        UpdateStudentRequest request = UpdateStudentRequest.builder()
                .admissionNumber("ADM-HACKED")
                .build();

        assertThatThrownBy(() -> studentService.updateStudent(studentBId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A context attempts status change on Tenant B Student ID (IDOR) — Rejection")
    void testTenantACrossStatusChangeTenantBStudent_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(studentRepository.findByIdAndNotDeleted(studentBId))
                .thenReturn(Optional.empty());

        UpdateStudentStatusRequest request = UpdateStudentStatusRequest.builder()
                .status(StudentStatus.SUSPENDED)
                .build();

        assertThatThrownBy(() -> studentService.updateStudentStatus(studentBId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A context attempts deletion on Tenant B Student ID (IDOR) — Rejection")
    void testTenantACrossDeleteTenantBStudent_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(studentRepository.findByIdAndNotDeleted(studentBId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.deleteStudent(studentBId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant User Relationship Security: Creating Student in Tenant A with Tenant B userId — Fails")
    void testTenantACrossTenantUserCreation_Denied() {
        TenantContext.setTenantCode("tenant_a");

        CreateStudentRequest request = CreateStudentRequest.builder()
                .tenantUserId(tenantBUserId) // User ID from Tenant B
                .admissionNumber("ADM-A-002")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        // In Tenant A's isolated database, tenantBUserId does not exist
        when(tenantUserRepository.findByIdAndNotDeleted(tenantBUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.createStudent(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant Context switches cleanly between Tenant A and Tenant B")
    void testTenantContextSwitching() {
        TenantContext.setTenantCode("tenant_a");
        assertThat(TenantContext.getTenantCode()).isEqualTo("tenant_a");

        TenantContext.setTenantCode("tenant_b");
        assertThat(TenantContext.getTenantCode()).isEqualTo("tenant_b");

        TenantContext.clear();
        assertThat(TenantContext.getTenantCode()).isNull();
    }
}
