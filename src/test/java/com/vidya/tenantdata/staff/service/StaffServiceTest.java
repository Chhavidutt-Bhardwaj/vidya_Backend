package com.vidya.tenantdata.staff.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.staff.dto.CreateStaffRequest;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.dto.StaffResponse;
import com.vidya.tenantdata.staff.dto.UpdateStaffRequest;
import com.vidya.tenantdata.staff.dto.UpdateStaffStatusRequest;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaffService — Unit Tests")
class StaffServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @InjectMocks
    private StaffServiceImpl staffService;

    private UUID tenantUserId;
    private UUID staffId;
    private TenantUser sampleUser;
    private Staff sampleStaff;

    @BeforeEach
    void setUp() {
        tenantUserId = UUID.randomUUID();
        staffId = UUID.randomUUID();

        sampleUser = new TenantUser("USR-12345678", "teacher@school.edu.in", "+919876543210", TenantUserType.STAFF);
        sampleUser.setId(tenantUserId);
        TenantUserProfile profile = new TenantUserProfile(sampleUser, "Priya", "Sharma");
        sampleUser.setProfile(profile);

        sampleStaff = new Staff("STF-87654321", sampleUser, "EMP-001",
                LocalDate.of(2026, 4, 1), StaffType.TEACHER, "Senior Teacher", "Mathematics");
        sampleStaff.setId(staffId);
    }

    // -------------------------------------------------------------------------
    // Create Staff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Create staff successfully when valid tenant user and unique codes")
    void createStaff_Success() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantUserId)
                .employeeCode("EMP-001")
                .joiningDate(LocalDate.of(2026, 4, 1))
                .staffType(StaffType.TEACHER)
                .designation("Senior Teacher")
                .department("Mathematics")
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));
        when(staffRepository.existsActiveByTenantUserId(tenantUserId, null)).thenReturn(false);
        when(staffRepository.existsActiveByEmployeeCode("EMP-001", null)).thenReturn(false);
        when(staffRepository.existsByStaffCode(anyString())).thenReturn(false);
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);

        StaffDetailResponse response = staffService.createStaff(request);

        assertThat(response).isNotNull();
        assertThat(response.getStaffCode()).isEqualTo("STF-87654321");
        assertThat(response.getStaffType()).isEqualTo(StaffType.TEACHER);
        verify(staffRepository).save(any(Staff.class));
    }

    @Test
    @DisplayName("Create staff fails when TenantUser not found")
    void createStaff_TenantUserNotFound() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantUserId)
                .staffType(StaffType.TEACHER)
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.createStaff(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create staff fails when TenantUser is of type STUDENT")
    void createStaff_StudentUserRejected() {
        sampleUser.setUserType(TenantUserType.STUDENT);
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantUserId)
                .staffType(StaffType.TEACHER)
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> staffService.createStaff(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("STUDENT");

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create staff fails when TenantUser already has an active Staff record")
    void createStaff_DuplicateTenantUser() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantUserId)
                .staffType(StaffType.TEACHER)
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));
        when(staffRepository.existsActiveByTenantUserId(tenantUserId, null)).thenReturn(true);

        assertThatThrownBy(() -> staffService.createStaff(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create staff fails when employee code is already in use")
    void createStaff_DuplicateEmployeeCode() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantUserId)
                .employeeCode("EMP-999")
                .staffType(StaffType.NON_TEACHING)
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));
        when(staffRepository.existsActiveByTenantUserId(tenantUserId, null)).thenReturn(false);
        when(staffRepository.existsActiveByEmployeeCode("EMP-999", null)).thenReturn(true);

        assertThatThrownBy(() -> staffService.createStaff(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("Staff code generation retries on collision")
    void createStaff_CodeCollisionRetries() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantUserId)
                .staffType(StaffType.ADMINISTRATIVE)
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));
        when(staffRepository.existsActiveByTenantUserId(tenantUserId, null)).thenReturn(false);
        // First code collides, second is unique
        when(staffRepository.existsByStaffCode(anyString())).thenReturn(true, false);
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);

        StaffDetailResponse response = staffService.createStaff(request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Create staff with no employee code succeeds")
    void createStaff_NoEmployeeCode_Success() {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(tenantUserId)
                .staffType(StaffType.SUPPORT)
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleUser));
        when(staffRepository.existsActiveByTenantUserId(tenantUserId, null)).thenReturn(false);
        when(staffRepository.existsByStaffCode(anyString())).thenReturn(false);
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);

        StaffDetailResponse response = staffService.createStaff(request);
        assertThat(response).isNotNull();
        // No employee code uniqueness check when null/blank
        verify(staffRepository, never()).existsActiveByEmployeeCode(any(), any());
    }

    // -------------------------------------------------------------------------
    // Get Staff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Get staff by ID returns detail response")
    void getStaffById_Success() {
        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(sampleStaff));

        StaffDetailResponse response = staffService.getStaffById(staffId);

        assertThat(response).isNotNull();
        assertThat(response.getStaffCode()).isEqualTo("STF-87654321");
    }

    @Test
    @DisplayName("Get staff by ID throws ResourceNotFoundException for unknown ID")
    void getStaffById_NotFound() {
        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getStaffById(staffId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get staff by staff code returns detail response")
    void getStaffByStaffCode_Success() {
        when(staffRepository.findByStaffCodeAndNotDeleted("STF-87654321")).thenReturn(Optional.of(sampleStaff));

        StaffDetailResponse response = staffService.getStaffByStaffCode("STF-87654321");

        assertThat(response).isNotNull();
        assertThat(response.getStaffType()).isEqualTo(StaffType.TEACHER);
    }

    @Test
    @DisplayName("Get staff by tenant user ID returns detail response")
    void getStaffByTenantUserId_Success() {
        when(staffRepository.findByTenantUserIdAndNotDeleted(tenantUserId)).thenReturn(Optional.of(sampleStaff));

        StaffDetailResponse response = staffService.getStaffByTenantUserId(tenantUserId);

        assertThat(response).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Update Staff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Update staff fields successfully")
    void updateStaff_Success() {
        UpdateStaffRequest request = UpdateStaffRequest.builder()
                .designation("Head of Department")
                .department("Science")
                .build();

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);

        StaffDetailResponse response = staffService.updateStaff(staffId, request);
        assertThat(response).isNotNull();
        verify(staffRepository).save(any(Staff.class));
    }

    @Test
    @DisplayName("Update staff fails when employee code conflicts with another staff")
    void updateStaff_DuplicateEmployeeCode() {
        UpdateStaffRequest request = UpdateStaffRequest.builder()
                .employeeCode("EMP-CONFLICT")
                .build();

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.existsActiveByEmployeeCode("EMP-CONFLICT", staffId)).thenReturn(true);

        assertThatThrownBy(() -> staffService.updateStaff(staffId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Update staff fails for unknown staff ID")
    void updateStaff_NotFound() {
        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.updateStaff(staffId, new UpdateStaffRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Status Transitions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Status transition ACTIVE -> SUSPENDED succeeds")
    void updateStaffStatus_ActiveToSuspended_Success() {
        UpdateStaffStatusRequest request = UpdateStaffStatusRequest.builder().status(EmploymentStatus.SUSPENDED).build();

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);

        StaffDetailResponse response = staffService.updateStaffStatus(staffId, request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Status transition ACTIVE -> RESIGNED succeeds")
    void updateStaffStatus_ActiveToResigned_Success() {
        UpdateStaffStatusRequest request = UpdateStaffStatusRequest.builder().status(EmploymentStatus.RESIGNED).build();

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);

        StaffDetailResponse response = staffService.updateStaffStatus(staffId, request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Status transition TERMINATED -> ACTIVE is rejected")
    void updateStaffStatus_TerminatedToActive_Rejected() {
        Staff terminatedStaff = new Staff("STF-TERM", sampleUser, null,
                LocalDate.of(2024, 1, 1), StaffType.TEACHER, "Teacher", "Math");
        terminatedStaff.setId(staffId);
        terminatedStaff.setEmploymentStatus(EmploymentStatus.TERMINATED);

        UpdateStaffStatusRequest request = UpdateStaffStatusRequest.builder().status(EmploymentStatus.ACTIVE).build();

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(terminatedStaff));

        assertThatThrownBy(() -> staffService.updateStaffStatus(staffId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Status transition RETIRED -> ACTIVE is rejected")
    void updateStaffStatus_RetiredToActive_Rejected() {
        Staff retiredStaff = new Staff("STF-RET", sampleUser, null,
                LocalDate.of(2020, 1, 1), StaffType.NON_TEACHING, "Admin", "Admin");
        retiredStaff.setId(staffId);
        retiredStaff.setEmploymentStatus(EmploymentStatus.RETIRED);

        UpdateStaffStatusRequest request = UpdateStaffStatusRequest.builder().status(EmploymentStatus.ACTIVE).build();

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(retiredStaff));

        assertThatThrownBy(() -> staffService.updateStaffStatus(staffId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Status transition SUSPENDED -> ACTIVE succeeds (reactivation from suspension)")
    void updateStaffStatus_SuspendedToActive_Success() {
        Staff suspendedStaff = new Staff("STF-SUSP", sampleUser, null,
                LocalDate.of(2026, 1, 1), StaffType.TEACHER, "Teacher", "English");
        suspendedStaff.setId(staffId);
        suspendedStaff.setEmploymentStatus(EmploymentStatus.SUSPENDED);

        UpdateStaffStatusRequest request = UpdateStaffStatusRequest.builder().status(EmploymentStatus.ACTIVE).build();

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(suspendedStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(suspendedStaff);

        StaffDetailResponse response = staffService.updateStaffStatus(staffId, request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Status transition DELETED -> ACTIVE is rejected")
    void updateStaffStatus_DeletedToActive_Rejected() {
        // DELETED staff won't be found by findByIdAndNotDeleted
        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.empty());

        UpdateStaffStatusRequest request = UpdateStaffStatusRequest.builder().status(EmploymentStatus.ACTIVE).build();

        assertThatThrownBy(() -> staffService.updateStaffStatus(staffId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Delete Staff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Soft delete staff succeeds for active staff")
    void deleteStaff_Success() {
        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(sampleStaff));
        when(staffRepository.save(any(Staff.class))).thenReturn(sampleStaff);

        staffService.deleteStaff(staffId);

        verify(staffRepository).save(any(Staff.class));
    }

    @Test
    @DisplayName("Delete staff throws ResourceNotFoundException for unknown ID")
    void deleteStaff_NotFound() {
        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.deleteStaff(staffId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Search Staff
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Search staff returns paginated results")
    void searchStaff_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Staff> page = new PageImpl<>(List.of(sampleStaff));

        when(staffRepository.searchStaff(null, null, null, null, null, pageable)).thenReturn(page);

        Page<StaffResponse> result = staffService.searchStaff(null, null, null, null, null, pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).getStaffCode()).isEqualTo("STF-87654321");
    }

    @Test
    @DisplayName("Search staff with filters returns filtered results")
    void searchStaff_WithFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Staff> page = new PageImpl<>(List.of(sampleStaff));

        when(staffRepository.searchStaff(EmploymentStatus.ACTIVE, StaffType.TEACHER, "Mathematics", null, "Priya", pageable))
                .thenReturn(page);

        Page<StaffResponse> result = staffService.searchStaff(
                EmploymentStatus.ACTIVE, StaffType.TEACHER, "Mathematics", null, "Priya", pageable);

        assertThat(result).hasSize(1);
    }
}
