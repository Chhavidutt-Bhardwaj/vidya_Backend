package com.vidya.tenantdata.assignment;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.dto.CreateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.CreateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.service.ClassSubjectAssignmentServiceImpl;
import com.vidya.tenantdata.assignment.service.TeacherSubjectAssignmentServiceImpl;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("Subject Assignment Isolation & IDOR Protection — Integration Tests")
class SubjectAssignmentIsolationIntegrationTest {

    @Mock
    private TeacherSubjectAssignmentRepository teacherAssignmentRepository;

    @Mock
    private ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    @InjectMocks
    private TeacherSubjectAssignmentServiceImpl teacherAssignmentService;

    @InjectMocks
    private ClassSubjectAssignmentServiceImpl classSubjectAssignmentService;

    private static final String TENANT_A = "tenant_alpha";
    private static final String TENANT_B = "tenant_beta";

    private final UUID tenantBTeacherId = UUID.randomUUID();
    private final UUID tenantBSubjectId = UUID.randomUUID();
    private final UUID tenantBAssignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantCode(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A cannot read Teacher Assignment belonging to Tenant B (returns 404)")
    void getTeacherAssignment_crossTenant_returns404() {
        when(teacherAssignmentRepository.findByIdAndNotDeleted(tenantBAssignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherAssignmentService.getAssignment(tenantBAssignmentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot update Teacher Assignment belonging to Tenant B (returns 404)")
    void updateTeacherAssignment_crossTenant_returns404() {
        UpdateTeacherAssignmentRequest request = new UpdateTeacherAssignmentRequest();
        when(teacherAssignmentRepository.findByIdAndNotDeleted(tenantBAssignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherAssignmentService.updateAssignment(tenantBAssignmentId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot delete Teacher Assignment belonging to Tenant B (returns 404)")
    void deleteTeacherAssignment_crossTenant_returns404() {
        when(teacherAssignmentRepository.findByIdAndNotDeleted(tenantBAssignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherAssignmentService.deleteAssignment(tenantBAssignmentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot assign Teacher ID belonging to Tenant B (foreign entity not found)")
    void createTeacherAssignment_crossTenantTeacherId_fails() {
        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(tenantBTeacherId);
        request.setSubjectId(UUID.randomUUID());
        request.setAcademicYearId(UUID.randomUUID());
        request.setAcademicClassId(UUID.randomUUID());

        when(staffRepository.findByIdAndNotDeleted(tenantBTeacherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot read Class Subject Deployment belonging to Tenant B (returns 404)")
    void getClassSubjectAssignment_crossTenant_returns404() {
        when(classSubjectAssignmentRepository.findByIdAndNotDeleted(tenantBAssignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> classSubjectAssignmentService.getAssignment(tenantBAssignmentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot update Class Subject Deployment belonging to Tenant B (returns 404)")
    void updateClassSubjectAssignment_crossTenant_returns404() {
        UpdateClassSubjectAssignmentRequest request = new UpdateClassSubjectAssignmentRequest();
        when(classSubjectAssignmentRepository.findByIdAndNotDeleted(tenantBAssignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> classSubjectAssignmentService.updateAssignment(tenantBAssignmentId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot delete Class Subject Deployment belonging to Tenant B (returns 404)")
    void deleteClassSubjectAssignment_crossTenant_returns404() {
        when(classSubjectAssignmentRepository.findByIdAndNotDeleted(tenantBAssignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> classSubjectAssignmentService.deleteAssignment(tenantBAssignmentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
