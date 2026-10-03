package com.vidya.tenantdata.student.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.student.dto.ClassSectionStudentResponse;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.EnrollmentDetailResponse;
import com.vidya.tenantdata.student.dto.TransferEnrollmentRequest;
import com.vidya.tenantdata.student.dto.WithdrawEnrollmentRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.repository.StudentSubjectEnrollmentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
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
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StudentEnrollmentService — Unit Tests")
class StudentEnrollmentServiceTest {

    @Mock
    private StudentAcademicEnrollmentRepository enrollmentRepository;

    @Mock
    private StudentSubjectEnrollmentRepository subjectEnrollmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private StudentSubjectEnrollmentService studentSubjectEnrollmentService;

    @InjectMocks
    private StudentEnrollmentServiceImpl enrollmentService;

    private UUID studentId;
    private UUID enrollmentId;
    private UUID academicYearId;
    private UUID classId;
    private UUID sectionId;
    private Student sampleStudent;
    private AcademicYear sampleYear;
    private AcademicClass sampleClass;
    private Section sampleSection;
    private StudentAcademicEnrollment sampleEnrollment;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        academicYearId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();

        TenantUser sampleUser = new TenantUser("USR-12345678", "student@school.edu.in", "+919876543210", TenantUserType.STUDENT);
        sampleUser.setId(UUID.randomUUID());
        TenantUserProfile profile = new TenantUserProfile(sampleUser, "Aarav", "Sharma");
        sampleUser.setProfile(profile);

        sampleStudent = new Student("STU-87654321", sampleUser, "ADM-2026-001", LocalDate.of(2026, 6, 1));
        sampleStudent.setId(studentId);

        sampleYear = new AcademicYear("AY-2026", "Academic Year 2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30), true, AcademicYearStatus.ACTIVE);
        sampleYear.setId(academicYearId);

        sampleClass = new AcademicClass("CLS-10", "Class 10", "Grade 10", 10);
        sampleClass.setId(classId);

        sampleSection = new Section(sampleYear, sampleClass, "SEC-A", "Section A", 40);
        sampleSection.setId(sectionId);

        sampleEnrollment = new StudentAcademicEnrollment(
                "ENR-12345678",
                sampleStudent,
                sampleYear,
                sampleClass,
                sampleSection,
                "1",
                StudentEnrollmentStatus.ACTIVE,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1),
                "Direct admission"
        );
        sampleEnrollment.setId(enrollmentId);
    }

    @Test
    @DisplayName("Create enrollment successfully")
    void createEnrollment_Success() {
        CreateEnrollmentRequest request = CreateEnrollmentRequest.builder()
                .academicYearId(academicYearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("1")
                .enrollmentDate(LocalDate.of(2026, 6, 1))
                .autoAssignCompulsorySubjects(false)
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(sampleYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sampleSection));
        when(enrollmentRepository.existsActiveEnrollmentForStudentAndYear(studentId, academicYearId)).thenReturn(false);
        when(enrollmentRepository.existsActiveRollNumber(academicYearId, classId, sectionId, "1", null)).thenReturn(false);
        when(enrollmentRepository.existsByEnrollmentCode(anyString())).thenReturn(false);
        when(enrollmentRepository.save(any(StudentAcademicEnrollment.class))).thenAnswer(inv -> {
            StudentAcademicEnrollment e = inv.getArgument(0);
            e.setId(enrollmentId);
            return e;
        });
        when(subjectEnrollmentRepository.findAllByEnrollmentId(enrollmentId)).thenReturn(List.of());

        EnrollmentDetailResponse response = enrollmentService.createEnrollment(studentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(enrollmentId);
        assertThat(response.getRollNumber()).isEqualTo("1");
        assertThat(response.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("Create enrollment throws DuplicateResourceException when student already has active enrollment in year")
    void createEnrollment_DuplicateActiveEnrollment() {
        CreateEnrollmentRequest request = CreateEnrollmentRequest.builder()
                .academicYearId(academicYearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("1")
                .enrollmentDate(LocalDate.of(2026, 6, 1))
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(sampleYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sampleSection));
        when(enrollmentRepository.existsActiveEnrollmentForStudentAndYear(studentId, academicYearId)).thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.createEnrollment(studentId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Create enrollment throws BusinessException when section belongs to another class")
    void createEnrollment_SectionMismatch() {
        AcademicClass otherClass = new AcademicClass("CLS-09", "Class 9", "Grade 9", 9);
        otherClass.setId(UUID.randomUUID());
        Section otherSection = new Section(sampleYear, otherClass, "SEC-B", "Section B", 40);
        otherSection.setId(sectionId);

        CreateEnrollmentRequest request = CreateEnrollmentRequest.builder()
                .academicYearId(academicYearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("1")
                .enrollmentDate(LocalDate.of(2026, 6, 1))
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(sampleYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(otherSection));

        assertThatThrownBy(() -> enrollmentService.createEnrollment(studentId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Get enrollment by ID returns detail response")
    void getEnrollmentById_Success() {
        when(enrollmentRepository.findByIdAndNotDeleted(enrollmentId)).thenReturn(Optional.of(sampleEnrollment));
        when(subjectEnrollmentRepository.findAllByEnrollmentId(enrollmentId)).thenReturn(List.of());

        EnrollmentDetailResponse response = enrollmentService.getEnrollmentById(enrollmentId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(enrollmentId);
        assertThat(response.getEnrollmentCode()).isEqualTo("ENR-12345678");
    }

    @Test
    @DisplayName("Get current enrollment returns CurrentEnrollmentResponse")
    void getCurrentEnrollment_Success() {
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(enrollmentRepository.findAllByStudentId(studentId)).thenReturn(List.of(sampleEnrollment));
        when(subjectEnrollmentRepository.findAllByEnrollmentId(enrollmentId)).thenReturn(List.of());

        CurrentEnrollmentResponse response = enrollmentService.getCurrentEnrollment(studentId);

        assertThat(response).isNotNull();
        assertThat(response.getEnrollmentId()).isEqualTo(enrollmentId);
        assertThat(response.getAcademicClassName()).isEqualTo("Class 10");
        assertThat(response.getSectionName()).isEqualTo("Section A");
    }

    @Test
    @DisplayName("Transfer enrollment moves student to new section and roll number")
    void transferEnrollment_Success() {
        Section newSection = new Section(sampleYear, sampleClass, "SEC-B", "Section B", 40);
        UUID newSectionId = UUID.randomUUID();
        newSection.setId(newSectionId);

        TransferEnrollmentRequest request = TransferEnrollmentRequest.builder()
                .newAcademicClassId(classId)
                .newSectionId(newSectionId)
                .newRollNumber("5")
                .transferDate(LocalDate.of(2026, 7, 1))
                .remarks("Section rebalancing")
                .build();

        when(enrollmentRepository.findByIdAndNotDeleted(enrollmentId)).thenReturn(Optional.of(sampleEnrollment));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.findByIdAndNotDeleted(newSectionId)).thenReturn(Optional.of(newSection));
        when(enrollmentRepository.existsActiveRollNumber(academicYearId, classId, newSectionId, "5", null)).thenReturn(false);
        when(enrollmentRepository.save(any(StudentAcademicEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subjectEnrollmentRepository.findAllByEnrollmentId(enrollmentId)).thenReturn(List.of());

        EnrollmentDetailResponse response = enrollmentService.transferStudent(enrollmentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getSectionId()).isEqualTo(newSectionId);
        assertThat(response.getRollNumber()).isEqualTo("5");
    }

    @Test
    @DisplayName("Withdraw enrollment transitions status to WITHDRAWN")
    void withdrawEnrollment_Success() {
        WithdrawEnrollmentRequest request = WithdrawEnrollmentRequest.builder()
                .withdrawalDate(LocalDate.of(2026, 8, 1))
                .withdrawalReason("Relocated to another city")
                .remarks("TC issued")
                .build();

        when(enrollmentRepository.findByIdAndNotDeleted(enrollmentId)).thenReturn(Optional.of(sampleEnrollment));
        when(enrollmentRepository.save(any(StudentAcademicEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subjectEnrollmentRepository.findAllByEnrollmentId(enrollmentId)).thenReturn(List.of());

        EnrollmentDetailResponse response = enrollmentService.withdrawStudent(enrollmentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.WITHDRAWN);
        assertThat(response.getWithdrawalReason()).isEqualTo("Relocated to another city");
    }

    @Test
    @DisplayName("Get class section students returns paginated roster")
    void getClassSectionStudents_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<StudentAcademicEnrollment> page = new PageImpl<>(List.of(sampleEnrollment), pageable, 1);

        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sampleSection));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(sampleYear));
        when(enrollmentRepository.searchClassSectionStudents(classId, sectionId, academicYearId, StudentEnrollmentStatus.ACTIVE, null, pageable))
                .thenReturn(page);

        Page<ClassSectionStudentResponse> response = enrollmentService.getClassSectionStudents(
                classId, sectionId, academicYearId, StudentEnrollmentStatus.ACTIVE, null, pageable
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getFullName()).isEqualTo("Aarav Sharma");
        assertThat(response.getContent().get(0).getRollNumber()).isEqualTo("1");
    }
}
