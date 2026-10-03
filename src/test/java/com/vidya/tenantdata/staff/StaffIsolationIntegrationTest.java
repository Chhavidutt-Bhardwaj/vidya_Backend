package com.vidya.tenantdata.staff;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.staff.dto.CreateStaffRequest;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.dto.UpdateStaffStatusRequest;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.service.StaffServiceImpl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenant Isolation & IDOR Protection tests for the Staff domain.
 *
 * <p>The TenantRoutingDataSource ensures these tests work in unit test mode with mocked
 * repositories — in production, the routing datasource physically restricts queries to
 * the authenticated tenant's database, making cross-tenant access physically impossible.
 *
 * <p>These tests verify that the application layer correctly uses ResourceNotFoundException
 * for cross-tenant IDs (which will return empty Optional from the tenant-scoped repository)
 * and never exposes data from another tenant.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Staff Isolation & IDOR Protection — Integration Tests")
class StaffIsolationIntegrationTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @InjectMocks
    private StaffServiceImpl staffService;

    private UUID tenantAUserId;
    private UUID tenantBUserId;
    private UUID staffAId;
    private UUID staffBId;

    private TenantUser tenantAUser;
    private TenantUser tenantBUser;
    private Staff staffA;
    private Staff staffB;

    @BeforeEach
    void setUp() {
        tenantAUserId = UUID.randomUUID();
        tenantBUserId = UUID.randomUUID();
        staffAId = UUID.randomUUID();
        staffBId = UUID.randomUUID();

        // Tenant A — Teacher
        tenantAUser = new TenantUser("USR-STAFF-A-01", "teacher.a@school-a.edu.in", "+919876543201", TenantUserType.STAFF);
        tenantAUser.setId(tenantAUserId);
        TenantUserProfile profileA = new TenantUserProfile(tenantAUser, "TeacherA", "SchoolA");
        tenantAUser.setProfile(profileA);

        staffA = new Staff("STF-TENANT-A-01", tenantAUser, "EMP-A-001",
                LocalDate.of(2026, 4, 1), StaffType.TEACHER, "Teacher", "Mathematics");
        staffA.setId(staffAId);

        // Tenant B — Admin Staff
        tenantBUser = new TenantUser("USR-STAFF-B-01", "admin.b@school-b.edu.in", "+919876543202", TenantUserType.STAFF);
        tenantBUser.setId(tenantBUserId);
        TenantUserProfile profileB = new TenantUserProfile(tenantBUser, "AdminB", "SchoolB");
        tenantBUser.setProfile(profileB);

        staffB = new Staff("STF-TENANT-B-01", tenantBUser, "EMP-B-001",
                LocalDate.of(2026, 6, 1), StaffType.ADMINISTRATIVE, "Administrator", "Admin");
        staffB.setId(staffBId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // IDOR: GET by ID
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant A context accesses own Staff by ID — ALLOWED")
    void getStaffById_TenantA_OwnStaff_Allowed() {
        TenantContext.setTenantCode("tenant_a");
        when(staffRepository.findByIdAndNotDeleted(staffAId)).thenReturn(Optional.of(staffA));

        StaffDetailResponse response = staffService.getStaffById(staffAId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(staffAId);
        assertThat(response.getStaffCode()).isEqualTo("STF-TENANT-A-01");
    }

    @Test
    @DisplayName("Tenant A context accesses Tenant B Staff by ID — SAFE FAILURE (404)")
    void getStaffById_TenantA_CrossTenantB_Blocked() {
        TenantContext.setTenantCode("tenant_a");
        // The tenant-scoped repository won't find Tenant B's staff in Tenant A's DB
        when(staffRepository.findByIdAndNotDeleted(staffBId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getStaffById(staffBId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant B context accesses own Staff by ID — ALLOWED")
    void getStaffById_TenantB_OwnStaff_Allowed() {
        TenantContext.setTenantCode("tenant_b");
        when(staffRepository.findByIdAndNotDeleted(staffBId)).thenReturn(Optional.of(staffB));

        StaffDetailResponse response = staffService.getStaffById(staffBId);

        assertThat(response).isNotNull();
        assertThat(response.getStaffCode()).isEqualTo("STF-TENANT-B-01");
    }

    @Test
    @DisplayName("Tenant B context accesses Tenant A Staff by ID — SAFE FAILURE (404)")
    void getStaffById_TenantB_CrossTenantA_Blocked() {
        TenantContext.setTenantCode("tenant_b");
        when(staffRepository.findByIdAndNotDeleted(staffAId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getStaffById(staffAId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // IDOR: GET by Staff Code
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant A accesses Tenant B Staff by staff code — SAFE FAILURE (404)")
    void getStaffByCode_CrossTenant_Blocked() {
        TenantContext.setTenantCode("tenant_a");
        when(staffRepository.findByStaffCodeAndNotDeleted("STF-TENANT-B-01")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getStaffByStaffCode("STF-TENANT-B-01"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A accesses own Staff by staff code — ALLOWED")
    void getStaffByCode_OwnStaff_Allowed() {
        TenantContext.setTenantCode("tenant_a");
        when(staffRepository.findByStaffCodeAndNotDeleted("STF-TENANT-A-01")).thenReturn(Optional.of(staffA));

        StaffDetailResponse response = staffService.getStaffByStaffCode("STF-TENANT-A-01");
        assertThat(response.getStaffCode()).isEqualTo("STF-TENANT-A-01");
    }

    // -------------------------------------------------------------------------
    // IDOR: GET by TenantUser ID
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant A looks up Tenant B TenantUser ID for Staff — SAFE FAILURE (404)")
    void getStaffByTenantUserId_CrossTenant_Blocked() {
        TenantContext.setTenantCode("tenant_a");
        // Tenant B's tenantUserId resolves to nothing in Tenant A's DB
        when(staffRepository.findByTenantUserIdAndNotDeleted(tenantBUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getStaffByTenantUserId(tenantBUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // IDOR: UPDATE
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant A attempts to update Tenant B Staff — SAFE FAILURE (404)")
    void updateStaff_CrossTenant_Blocked() {
        TenantContext.setTenantCode("tenant_a");
        when(staffRepository.findByIdAndNotDeleted(staffBId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.updateStaff(staffBId,
                com.vidya.tenantdata.staff.dto.UpdateStaffRequest.builder().designation("Hacked").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // IDOR: PATCH STATUS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant A attempts to change Tenant B Staff status — SAFE FAILURE (404)")
    void updateStaffStatus_CrossTenant_Blocked() {
        TenantContext.setTenantCode("tenant_a");
        when(staffRepository.findByIdAndNotDeleted(staffBId)).thenReturn(Optional.empty());

        UpdateStaffStatusRequest request = new UpdateStaffStatusRequest(EmploymentStatus.TERMINATED, null);

        assertThatThrownBy(() -> staffService.updateStaffStatus(staffBId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // IDOR: DELETE
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant A attempts to delete Tenant B Staff — SAFE FAILURE (404)")
    void deleteStaff_CrossTenant_Blocked() {
        TenantContext.setTenantCode("tenant_a");
        when(staffRepository.findByIdAndNotDeleted(staffBId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.deleteStaff(staffBId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Cross-Tenant Identity Attack
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tenant A creates Staff with Tenant B TenantUser ID — SAFE FAILURE (404)")
    void createStaff_CrossTenantIdentityAttack_Blocked() {
        TenantContext.setTenantCode("tenant_a");

        // Tenant B's TenantUser ID is not visible in Tenant A's DB
        when(tenantUserRepository.findByIdAndNotDeleted(tenantBUserId)).thenReturn(Optional.empty());

        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantBUserId)  // Attacker provides Tenant B's user ID
                .staffType(StaffType.TEACHER)
                .build();

        assertThatThrownBy(() -> staffService.createStaff(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tenant A creates Staff with own TenantUser — ALLOWED")
    void createStaff_OwnTenantUser_Allowed() {
        TenantContext.setTenantCode("tenant_a");

        when(tenantUserRepository.findByIdAndNotDeleted(tenantAUserId)).thenReturn(Optional.of(tenantAUser));
        when(staffRepository.existsActiveByTenantUserId(tenantAUserId, null)).thenReturn(false);
        when(staffRepository.existsByStaffCode(anyString())).thenReturn(false);
        when(staffRepository.save(any(Staff.class))).thenReturn(staffA);

        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantAUserId)
                .staffType(StaffType.TEACHER)
                .build();

        StaffDetailResponse response = staffService.createStaff(request);
        assertThat(response).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Student TenantUser Blocked from Staff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TenantUser of type STUDENT cannot be registered as Staff — Rejected")
    void createStaff_StudentUserType_Rejected() {
        TenantContext.setTenantCode("tenant_a");

        TenantUser studentUser = new TenantUser("USR-STUDENT-01", "student@school.edu.in", "+91123456789", TenantUserType.STUDENT);
        studentUser.setId(tenantAUserId);

        when(tenantUserRepository.findByIdAndNotDeleted(tenantAUserId)).thenReturn(Optional.of(studentUser));

        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantAUserId)
                .staffType(StaffType.TEACHER)
                .build();

        assertThatThrownBy(() -> staffService.createStaff(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("STUDENT");

        verify(staffRepository, never()).save(any());
    }
}
