package com.vidya.tenantdata.student.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.student.dto.CreateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.dto.StudentSubjectEnrollmentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollment;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollmentStatus;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentSubjectEnrollmentRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
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

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StudentSubjectEnrollmentService — Unit Tests")
class StudentSubjectEnrollmentServiceTest {

    @Mock
    private StudentSubjectEnrollmentRepository subjectEnrollmentRepository;

    @Mock
    private StudentAcademicEnrollmentRepository academicEnrollmentRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

    @InjectMocks
    private StudentSubjectEnrollmentServiceImpl subjectEnrollmentService;

    private UUID enrollmentId;
    private UUID subjectId;
    private UUID subjectEnrollmentId;
    private StudentAcademicEnrollment sampleEnrollment;
    private Subject sampleSubject;
    private StudentSubjectEnrollment sampleSubjectEnrollment;

    @BeforeEach
    void setUp() {
        enrollmentId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        subjectEnrollmentId = UUID.randomUUID();

        TenantUser sampleUser = new TenantUser("USR-12345678", "student@school.edu.in", "+919876543210", TenantUserType.STUDENT);
        sampleUser.setId(UUID.randomUUID());
        TenantUserProfile profile = new TenantUserProfile(sampleUser, "Aarav", "Sharma");
        sampleUser.setProfile(profile);

        Student student = new Student("STU-87654321", sampleUser, "ADM-2026-001", LocalDate.of(2026, 6, 1));
        AcademicYear year = new AcademicYear("AY-2026", "Academic Year 2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30), true, AcademicYearStatus.ACTIVE);
        AcademicClass clazz = new AcademicClass("CLS-10", "Class 10", "Grade 10", 10);
        Section section = new Section(year, clazz, "SEC-A", "Section A", 40);

        sampleEnrollment = new StudentAcademicEnrollment(
                "ENR-12345678",
                student,
                year,
                clazz,
                section,
                "1",
                StudentEnrollmentStatus.ACTIVE,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1),
                "Direct admission"
        );
        sampleEnrollment.setId(enrollmentId);

        sampleSubject = new Subject("SUB-101", "Mathematics", "MATH", "Mathematics Course", SubjectType.CORE, 1);
        sampleSubject.setId(subjectId);

        sampleSubjectEnrollment = new StudentSubjectEnrollment(
                "SSE-12345678",
                sampleEnrollment,
                sampleSubject,
                null,
                LocalDate.of(2026, 6, 1),
                "Core subject"
        );
        sampleSubjectEnrollment.setId(subjectEnrollmentId);
    }

    @Test
    @DisplayName("Enroll subject successfully")
    void enrollSubject_Success() {
        CreateStudentSubjectEnrollmentRequest request = CreateStudentSubjectEnrollmentRequest.builder()
                .subjectId(subjectId)
                .effectiveFrom(LocalDate.of(2026, 6, 1))
                .remarks("Core subject")
                .build();

        when(academicEnrollmentRepository.findByIdAndNotDeleted(enrollmentId)).thenReturn(Optional.of(sampleEnrollment));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));
        when(subjectEnrollmentRepository.existsActiveByEnrollmentIdAndSubjectId(enrollmentId, subjectId)).thenReturn(false);
        when(classSubjectAssignmentRepository.findActiveByClassAndSection(any(), any(), any()))
                .thenReturn(List.of(new ClassSubjectAssignment("CSA-101", sampleEnrollment.getAcademicYear(), sampleEnrollment.getAcademicClass(), null, sampleSubject, null, true, 1, 5)));
        when(subjectEnrollmentRepository.existsBySubjectEnrollmentCode(anyString())).thenReturn(false);
        when(subjectEnrollmentRepository.save(any(StudentSubjectEnrollment.class))).thenAnswer(inv -> {
            StudentSubjectEnrollment s = inv.getArgument(0);
            s.setId(subjectEnrollmentId);
            return s;
        });

        StudentSubjectEnrollmentResponse response = subjectEnrollmentService.enrollSubject(enrollmentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(subjectEnrollmentId);
        assertThat(response.getSubjectName()).isEqualTo("Mathematics");
        assertThat(response.getStatus()).isEqualTo(StudentSubjectEnrollmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("Enroll subject throws DuplicateResourceException on duplicate active subject")
    void enrollSubject_DuplicateActiveSubject() {
        CreateStudentSubjectEnrollmentRequest request = CreateStudentSubjectEnrollmentRequest.builder()
                .subjectId(subjectId)
                .build();

        when(academicEnrollmentRepository.findByIdAndNotDeleted(enrollmentId)).thenReturn(Optional.of(sampleEnrollment));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));
        when(subjectEnrollmentRepository.existsActiveByEnrollmentIdAndSubjectId(enrollmentId, subjectId)).thenReturn(true);

        assertThatThrownBy(() -> subjectEnrollmentService.enrollSubject(enrollmentId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Update subject enrollment status to DROPPED")
    void updateSubjectEnrollment_DropSubject() {
        UpdateStudentSubjectEnrollmentRequest request = UpdateStudentSubjectEnrollmentRequest.builder()
                .status(StudentSubjectEnrollmentStatus.DROPPED)
                .effectiveTo(LocalDate.of(2026, 7, 1))
                .remarks("Switched to Sanskrit")
                .build();

        when(academicEnrollmentRepository.findByIdAndNotDeleted(enrollmentId)).thenReturn(Optional.of(sampleEnrollment));
        when(subjectEnrollmentRepository.findByIdAndNotDeleted(subjectEnrollmentId)).thenReturn(Optional.of(sampleSubjectEnrollment));
        when(subjectEnrollmentRepository.save(any(StudentSubjectEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentSubjectEnrollmentResponse response = subjectEnrollmentService.updateSubjectEnrollment(enrollmentId, subjectEnrollmentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(StudentSubjectEnrollmentStatus.DROPPED);
    }

    @Test
    @DisplayName("Remove subject enrollment soft deletes record")
    void removeSubjectEnrollment_Success() {
        when(academicEnrollmentRepository.findByIdAndNotDeleted(enrollmentId)).thenReturn(Optional.of(sampleEnrollment));
        when(subjectEnrollmentRepository.findByIdAndNotDeleted(subjectEnrollmentId)).thenReturn(Optional.of(sampleSubjectEnrollment));
        when(subjectEnrollmentRepository.save(any(StudentSubjectEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        subjectEnrollmentService.removeSubjectEnrollment(enrollmentId, subjectEnrollmentId);

        assertThat(sampleSubjectEnrollment.getStatus()).isEqualTo(StudentSubjectEnrollmentStatus.DELETED);
        assertThat(sampleSubjectEnrollment.isDeleted()).isTrue();
        verify(subjectEnrollmentRepository).save(sampleSubjectEnrollment);
    }
}
