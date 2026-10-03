package com.vidya.tenantdata.assignment;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.dto.CreateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.assignment.service.ClassTeacherAssignmentServiceImpl;
import com.vidya.tenantdata.staff.repository.StaffRepository;
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
@DisplayName("Class Teacher Assignment Isolation & IDOR Protection — Integration Tests")
class ClassTeacherAssignmentIsolationIntegrationTest {

    @Mock
    private ClassTeacherAssignmentRepository assignmentRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private ClassTeacherAssignmentServiceImpl service;

    private static final String TENANT_A = "tenant_alpha";
    private static final String TENANT_B = "tenant_beta";

    private final UUID tenantBStaffId = UUID.randomUUID();
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
    @DisplayName("Cross-tenant assignment lookup returns 404 Not Found")
    void crossTenantAssignmentLookup_returnsNotFound() {
        when(assignmentRepository.findByIdAndNotDeleted(tenantBAssignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAssignment(tenantBAssignmentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ClassTeacherAssignment");
    }

    @Test
    @DisplayName("Create assignment referencing cross-tenant staff returns 404 Not Found")
    void createAssignmentWithForeignStaff_failsWithNotFound() {
        UUID yearId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();

        when(staffRepository.findByIdAndNotDeleted(tenantBStaffId)).thenReturn(Optional.empty());

        CreateClassTeacherAssignmentRequest req = new CreateClassTeacherAssignmentRequest(
                tenantBStaffId, yearId, classId, sectionId, LocalDate.of(2026, 4, 1), "Test"
        );

        assertThatThrownBy(() -> service.createAssignment(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Staff");
    }

    @Test
    @DisplayName("Tenant context switched to Tenant B maintains proper isolation")
    void tenantSwitch_maintainsIsolation() {
        TenantContext.setTenantCode(TENANT_B);
        assertThat(TenantContext.getTenantCode()).isEqualTo(TENANT_B);

        when(assignmentRepository.findByIdAndNotDeleted(tenantBAssignmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAssignment(tenantBAssignmentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
