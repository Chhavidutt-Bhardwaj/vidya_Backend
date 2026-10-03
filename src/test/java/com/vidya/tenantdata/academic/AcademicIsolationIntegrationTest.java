package com.vidya.tenantdata.academic;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.academic.dto.AcademicClassResponse;
import com.vidya.tenantdata.academic.dto.AcademicYearResponse;
import com.vidya.tenantdata.academic.dto.AssignStudentRequest;
import com.vidya.tenantdata.academic.dto.SectionResponse;
import com.vidya.tenantdata.academic.dto.StudentAcademicAssignmentResponse;
import com.vidya.tenantdata.academic.dto.UpdateSectionRequest;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.StudentAcademicAssignment;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StudentAcademicAssignmentRepository;
import com.vidya.tenantdata.academic.service.AcademicClassServiceImpl;
import com.vidya.tenantdata.academic.service.AcademicYearServiceImpl;
import com.vidya.tenantdata.academic.service.SectionServiceImpl;
import com.vidya.tenantdata.academic.service.StudentAcademicAssignmentServiceImpl;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
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
@DisplayName("Academic Structure — Tenant Isolation & IDOR Protection Tests")
class AcademicIsolationIntegrationTest {

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private StudentAcademicAssignmentRepository assignmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private AcademicYearServiceImpl academicYearService;

    @InjectMocks
    private AcademicClassServiceImpl academicClassService;

    @InjectMocks
    private SectionServiceImpl sectionService;

    @InjectMocks
    private StudentAcademicAssignmentServiceImpl assignmentService;

    private UUID tenantAYearId;
    private UUID tenantBYearId;
    private UUID tenantAClassId;
    private UUID tenantBClassId;
    private UUID tenantASectionId;
    private UUID tenantBSectionId;
    private UUID tenantAStudentId;
    private UUID tenantAAssignmentId;
    private UUID tenantBAssignmentId;

    private AcademicYear yearA;
    private AcademicClass classA;
    private Section sectionA;
    private Student studentA;
    private StudentAcademicAssignment assignmentA;

    @BeforeEach
    void setUp() {
        tenantAYearId = UUID.randomUUID();
        tenantBYearId = UUID.randomUUID();
        tenantAClassId = UUID.randomUUID();
        tenantBClassId = UUID.randomUUID();
        tenantASectionId = UUID.randomUUID();
        tenantBSectionId = UUID.randomUUID();
        tenantAStudentId = UUID.randomUUID();
        tenantAAssignmentId = UUID.randomUUID();
        tenantBAssignmentId = UUID.randomUUID();

        yearA = new AcademicYear("AY-A-2026", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        yearA.setId(tenantAYearId);

        classA = new AcademicClass("CLASS_5", "Class 5", "Grade 5", 5);
        classA.setId(tenantAClassId);

        sectionA = new Section(yearA, classA, "A", "Section A", 40);
        sectionA.setId(tenantASectionId);

        TenantUser userA = new TenantUser("USR-A", "userA@schoola.edu.in", null, TenantUserType.STUDENT);
        userA.setId(UUID.randomUUID());
        studentA = new Student("STU-A", userA, "ADM-A", LocalDate.of(2026, 4, 1));
        studentA.setId(tenantAStudentId);

        assignmentA = new StudentAcademicAssignment(studentA, yearA, classA, sectionA, "1", LocalDate.of(2026, 4, 1));
        assignmentA.setId(tenantAAssignmentId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A accesses own Academic Year — ALLOWED")
    void testTenantAAccessOwnAcademicYear_Allowed() {
        TenantContext.setTenantCode("tenant_a");

        when(academicYearRepository.findByIdAndNotDeleted(tenantAYearId)).thenReturn(Optional.of(yearA));

        AcademicYearResponse response = academicYearService.getAcademicYearById(tenantAYearId);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(tenantAYearId);
        assertThat(response.getCode()).isEqualTo("AY-A-2026");
    }

    @Test
    @DisplayName("Tenant A queries Tenant B Academic Year (IDOR) — ResourceNotFoundException")
    void testTenantACrossQueryTenantBAcademicYear_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(academicYearRepository.findByIdAndNotDeleted(tenantBYearId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicYearService.getAcademicYearById(tenantBYearId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A queries Tenant B Academic Class (IDOR) — ResourceNotFoundException")
    void testTenantACrossQueryTenantBClass_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(academicClassRepository.findByIdAndNotDeleted(tenantBClassId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicClassService.getAcademicClassById(tenantBClassId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A queries Tenant B Section (IDOR) — ResourceNotFoundException")
    void testTenantACrossQueryTenantBSection_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(sectionRepository.findByIdAndNotDeleted(tenantBSectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sectionService.getSectionById(tenantBSectionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A attempts update on Tenant B Section (IDOR) — ResourceNotFoundException")
    void testTenantACrossUpdateTenantBSection_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(sectionRepository.findByIdAndNotDeleted(tenantBSectionId)).thenReturn(Optional.empty());

        UpdateSectionRequest request = UpdateSectionRequest.builder().name("Hacked").build();

        assertThatThrownBy(() -> sectionService.updateSection(tenantBSectionId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A attempts deletion on Tenant B Class (IDOR) — ResourceNotFoundException")
    void testTenantACrossDeleteTenantBClass_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(academicClassRepository.findByIdAndNotDeleted(tenantBClassId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicClassService.deleteAcademicClass(tenantBClassId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A attempts to assign Tenant A Student to Tenant B Section — Fails (Section not in Tenant A DB)")
    void testTenantACrossAssignStudentToTenantBSection_Denied() {
        TenantContext.setTenantCode("tenant_a");

        AssignStudentRequest request = AssignStudentRequest.builder()
                .academicYearId(tenantAYearId)
                .academicClassId(tenantAClassId)
                .sectionId(tenantBSectionId)
                .build();

        when(studentRepository.findByIdAndNotDeleted(tenantAStudentId)).thenReturn(Optional.of(studentA));
        when(academicYearRepository.findByIdAndNotDeleted(tenantAYearId)).thenReturn(Optional.of(yearA));
        when(academicClassRepository.findByIdAndNotDeleted(tenantAClassId)).thenReturn(Optional.of(classA));
        when(sectionRepository.findByIdAndNotDeleted(tenantBSectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.assignStudent(tenantAStudentId, request))
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
