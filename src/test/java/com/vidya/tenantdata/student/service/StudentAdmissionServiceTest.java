package com.vidya.tenantdata.student.service;

import com.vidya.tenantdata.student.dto.AdmissionResponse;
import com.vidya.tenantdata.student.dto.CreateAdmissionRequest;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.CreateStudentGuardianRequest;
import com.vidya.tenantdata.student.entity.GuardianRelationshipType;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentGuardian;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.repository.StudentSubjectEnrollmentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentAdmissionService — Unit Tests")
class StudentAdmissionServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private StudentEnrollmentService enrollmentService;

    @Mock
    private StudentGuardianService guardianService;

    @Mock
    private StudentSubjectEnrollmentService subjectEnrollmentService;

    @Mock
    private StudentSubjectEnrollmentRepository subjectEnrollmentRepository;

    @InjectMocks
    private StudentAdmissionServiceImpl admissionService;

    @Test
    @DisplayName("Admit student orchestrates user, student, enrollment, subject, and guardian creation atomically")
    void admitStudent_Success() {
        UUID academicYearId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        CreateAdmissionRequest request = CreateAdmissionRequest.builder()
                .firstName("Rahul")
                .lastName("Sharma")
                .dateOfBirth(LocalDate.of(2013, 5, 12))
                .admissionNumber("ADM-2026-0099")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .academicYearId(academicYearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("10")
                .autoAssignCompulsorySubjects(true)
                .guardians(List.of(
                        CreateStudentGuardianRequest.builder()
                                .guardianName("Sunita Sharma")
                                .relationshipType(GuardianRelationshipType.MOTHER)
                                .isPrimary(true)
                                .build()
                ))
                .build();

        TenantUser savedUser = new TenantUser("USR-12345678", "rahul@school.internal", null, TenantUserType.STUDENT);
        savedUser.setId(UUID.randomUUID());
        savedUser.setProfile(new TenantUserProfile(savedUser, "Rahul", "Sharma"));

        Student savedStudent = new Student("STU-12345678", savedUser, "ADM-2026-0099", LocalDate.of(2026, 6, 1));
        savedStudent.setId(studentId);

        StudentAcademicEnrollment mockEnrollment = mock(StudentAcademicEnrollment.class);
        when(mockEnrollment.getId()).thenReturn(enrollmentId);
        when(mockEnrollment.getEnrollmentCode()).thenReturn("ENR-12345678");
        when(mockEnrollment.getRollNumber()).thenReturn("10");
        when(mockEnrollment.getEnrollmentStatus()).thenReturn(StudentEnrollmentStatus.ACTIVE);

        StudentGuardian mockGuardian = mock(StudentGuardian.class);
        when(mockGuardian.getGuardianName()).thenReturn("Sunita Sharma");
        when(mockGuardian.getRelationshipType()).thenReturn(GuardianRelationshipType.MOTHER);

        when(studentRepository.existsByAdmissionNumber("ADM-2026-0099")).thenReturn(false);
        when(studentRepository.existsByStudentCode(anyString())).thenReturn(false);
        when(tenantUserRepository.existsByUserCode(anyString())).thenReturn(false);
        when(tenantUserRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(tenantUserRepository.save(any(TenantUser.class))).thenReturn(savedUser);
        when(studentRepository.save(any(Student.class))).thenReturn(savedStudent);

        when(enrollmentService.createEnrollmentEntity(eq(savedStudent), any(CreateEnrollmentRequest.class))).thenReturn(mockEnrollment);
        when(subjectEnrollmentService.autoEnrollCompulsorySubjects(mockEnrollment)).thenReturn(List.of());
        when(guardianService.createGuardianEntity(eq(savedStudent), any(CreateStudentGuardianRequest.class))).thenReturn(mockGuardian);

        AdmissionResponse response = admissionService.admitStudent(request);

        assertThat(response).isNotNull();
        assertThat(response.getStudent()).isNotNull();
        assertThat(response.getStudent().getId()).isEqualTo(studentId);
        assertThat(response.getEnrollment()).isNotNull();
        assertThat(response.getEnrollment().getId()).isEqualTo(enrollmentId);
        assertThat(response.getGuardians()).hasSize(1);
    }
}
