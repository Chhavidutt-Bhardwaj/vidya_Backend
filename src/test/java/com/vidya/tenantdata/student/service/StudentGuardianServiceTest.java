package com.vidya.tenantdata.student.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.student.dto.CreateStudentGuardianRequest;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentGuardianRequest;
import com.vidya.tenantdata.student.entity.GuardianRelationshipType;
import com.vidya.tenantdata.student.entity.GuardianStatus;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentGuardian;
import com.vidya.tenantdata.student.repository.StudentGuardianRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentGuardianService — Unit Tests")
class StudentGuardianServiceTest {

    @Mock
    private StudentGuardianRepository guardianRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentGuardianServiceImpl guardianService;

    private UUID studentId;
    private UUID guardianId;
    private Student sampleStudent;
    private StudentGuardian sampleGuardian;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        guardianId = UUID.randomUUID();

        TenantUser studentUser = new TenantUser("USR-STU1234", "student@school.edu.in", "+919876543210", TenantUserType.STUDENT);
        studentUser.setId(UUID.randomUUID());
        TenantUserProfile studentProfile = new TenantUserProfile(studentUser, "Aarav", "Sharma");
        studentUser.setProfile(studentProfile);

        sampleStudent = new Student("STU-87654321", studentUser, "ADM-2026-001", LocalDate.of(2026, 6, 1));
        sampleStudent.setId(studentId);

        sampleGuardian = new StudentGuardian(
                "GDN-12345678",
                sampleStudent,
                "Rajesh Sharma",
                GuardianRelationshipType.FATHER,
                "+919876543211",
                "parent@school.edu.in",
                "Engineer",
                new BigDecimal("1200000.00"),
                "123 Street",
                null,
                "Delhi",
                "Delhi",
                "110001",
                true,
                true
        );
        sampleGuardian.setId(guardianId);
    }

    @Test
    @DisplayName("Create guardian successfully")
    void createGuardian_Success() {
        CreateStudentGuardianRequest request = CreateStudentGuardianRequest.builder()
                .guardianName("Rajesh Sharma")
                .relationshipType(GuardianRelationshipType.FATHER)
                .phone("+919876543211")
                .email("parent@school.edu.in")
                .isPrimary(true)
                .isEmergencyContact(true)
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(guardianRepository.existsByGuardianCode(anyString())).thenReturn(false);
        when(guardianRepository.save(any(StudentGuardian.class))).thenAnswer(inv -> {
            StudentGuardian g = inv.getArgument(0);
            g.setId(guardianId);
            return g;
        });

        StudentGuardianResponse response = guardianService.createGuardian(studentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(guardianId);
        assertThat(response.getRelationshipType()).isEqualTo(GuardianRelationshipType.FATHER);
        assertThat(response.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("Get guardians by student ID returns list")
    void getGuardiansByStudentId_Success() {
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(guardianRepository.findAllByStudentId(studentId)).thenReturn(List.of(sampleGuardian));

        List<StudentGuardianResponse> responses = guardianService.getGuardiansByStudentId(studentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getGuardianName()).isEqualTo("Rajesh Sharma");
    }

    @Test
    @DisplayName("Update guardian details")
    void updateGuardian_Success() {
        UpdateStudentGuardianRequest request = UpdateStudentGuardianRequest.builder()
                .relationshipType(GuardianRelationshipType.LEGAL_GUARDIAN)
                .isEmergencyContact(false)
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(guardianRepository.findByIdAndNotDeleted(guardianId)).thenReturn(Optional.of(sampleGuardian));
        when(guardianRepository.save(any(StudentGuardian.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentGuardianResponse response = guardianService.updateGuardian(studentId, guardianId, request);

        assertThat(response).isNotNull();
        assertThat(response.getRelationshipType()).isEqualTo(GuardianRelationshipType.LEGAL_GUARDIAN);
        assertThat(response.isEmergencyContact()).isFalse();
    }

    @Test
    @DisplayName("Delete guardian soft deletes record")
    void deleteGuardian_Success() {
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(guardianRepository.findByIdAndNotDeleted(guardianId)).thenReturn(Optional.of(sampleGuardian));
        when(guardianRepository.save(any(StudentGuardian.class))).thenAnswer(inv -> inv.getArgument(0));

        guardianService.deleteGuardian(studentId, guardianId);

        assertThat(sampleGuardian.getStatus()).isEqualTo(GuardianStatus.DELETED);
        assertThat(sampleGuardian.isDeleted()).isTrue();
        verify(guardianRepository).save(sampleGuardian);
    }
}
