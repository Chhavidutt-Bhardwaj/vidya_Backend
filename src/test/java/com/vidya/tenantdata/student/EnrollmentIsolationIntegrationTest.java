package com.vidya.tenantdata.student;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.student.dto.EnrollmentDetailResponse;
import com.vidya.tenantdata.student.dto.TransferEnrollmentRequest;
import com.vidya.tenantdata.student.dto.WithdrawEnrollmentRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.service.StudentEnrollmentServiceImpl;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
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

import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.repository.StudentSubjectEnrollmentRepository;
import com.vidya.tenantdata.student.service.StudentSubjectEnrollmentService;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Enrollment Isolation & IDOR Protection — Integration Tests")
class EnrollmentIsolationIntegrationTest {

    @Mock
    private StudentAcademicEnrollmentRepository enrollmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private StudentSubjectEnrollmentService subjectEnrollmentService;

    @Mock
    private StudentSubjectEnrollmentRepository subjectEnrollmentRepository;

    @InjectMocks
    private StudentEnrollmentServiceImpl enrollmentService;

    private UUID enrollmentAId;
    private UUID enrollmentBId;
    private StudentAcademicEnrollment enrollmentA;

    @BeforeEach
    void setUp() {
        enrollmentAId = UUID.randomUUID();
        enrollmentBId = UUID.randomUUID();

        TenantUser userA = new TenantUser("USR-A", "userA@school.edu.in", null, TenantUserType.STUDENT);
        userA.setId(UUID.randomUUID());
        userA.setProfile(new TenantUserProfile(userA, "Student", "A"));

        Student studentA = new Student("STU-A", userA, "ADM-A-001", LocalDate.of(2026, 6, 1));
        AcademicYear yearA = new AcademicYear("AY-2026", "Academic Year 2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30), true, AcademicYearStatus.ACTIVE);
        AcademicClass classA = new AcademicClass("CLS-10", "Class 10", "Grade 10", 10);
        Section sectionA = new Section(yearA, classA, "SEC-A", "Section A", 40);

        enrollmentA = new StudentAcademicEnrollment(
                "ENR-A-123",
                studentA,
                yearA,
                classA,
                sectionA,
                "1",
                StudentEnrollmentStatus.ACTIVE,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1),
                null
        );
        enrollmentA.setId(enrollmentAId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A accesses own enrollment — ALLOWED")
    void testTenantAAccessOwnEnrollment_Allowed() {
        TenantContext.setTenantCode("tenant_a");

        when(enrollmentRepository.findByIdAndNotDeleted(enrollmentAId))
                .thenReturn(Optional.of(enrollmentA));

        EnrollmentDetailResponse response = enrollmentService.getEnrollmentById(enrollmentAId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(enrollmentAId);
        assertThat(response.getEnrollmentCode()).isEqualTo("ENR-A-123");
    }

    @Test
    @DisplayName("Tenant A queries Tenant B enrollment ID (IDOR) — Returns ResourceNotFoundException")
    void testTenantACrossQueryTenantBEnrollment_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(enrollmentRepository.findByIdAndNotDeleted(enrollmentBId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.getEnrollmentById(enrollmentBId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A attempts transfer on Tenant B enrollment ID (IDOR) — Rejection")
    void testTenantACrossTransferTenantBEnrollment_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(enrollmentRepository.findByIdAndNotDeleted(enrollmentBId))
                .thenReturn(Optional.empty());

        TransferEnrollmentRequest request = TransferEnrollmentRequest.builder()
                .newAcademicClassId(UUID.randomUUID())
                .newSectionId(UUID.randomUUID())
                .newRollNumber("2")
                .build();

        assertThatThrownBy(() -> enrollmentService.transferStudent(enrollmentBId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A attempts withdraw on Tenant B enrollment ID (IDOR) — Rejection")
    void testTenantACrossWithdrawTenantBEnrollment_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(enrollmentRepository.findByIdAndNotDeleted(enrollmentBId))
                .thenReturn(Optional.empty());

        WithdrawEnrollmentRequest request = WithdrawEnrollmentRequest.builder()
                .withdrawalDate(LocalDate.of(2026, 8, 1))
                .withdrawalReason("Transferred")
                .build();

        assertThatThrownBy(() -> enrollmentService.withdrawStudent(enrollmentBId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
