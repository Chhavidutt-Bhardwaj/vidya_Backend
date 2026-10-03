package com.vidya.tenantdata.user.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.user.dto.CreateTenantUserRequest;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.dto.TenantUserResponse;
import com.vidya.tenantdata.user.dto.UpdateTenantUserRequest;
import com.vidya.tenantdata.user.dto.UpdateTenantUserStatusRequest;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantUserService — Unit Tests")
class TenantUserServiceTest {

    @Mock
    private TenantUserRepository tenantUserRepository;

    @InjectMocks
    private TenantUserServiceImpl tenantUserService;

    private TenantUser sampleUser;
    private TenantUserProfile sampleProfile;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = new TenantUser("USR-ABC12345", "teacher@greenwood.edu.in", "+919876543210", TenantUserType.TEACHER);
        sampleUser.setId(userId);

        sampleProfile = new TenantUserProfile(sampleUser, "Ramesh", "Sharma");
        sampleProfile.setId(UUID.randomUUID());
        sampleUser.setProfile(sampleProfile);
    }

    @Test
    @DisplayName("Create Tenant User — Success")
    void createTenantUser_Success() {
        CreateTenantUserRequest request = CreateTenantUserRequest.builder()
                .email("newteacher@greenwood.edu.in")
                .firstName("Anita")
                .lastName("Desai")
                .phone("+919876543211")
                .userType(TenantUserType.TEACHER)
                .city("Bengaluru")
                .build();

        when(tenantUserRepository.existsByEmailIgnoreCase("newteacher@greenwood.edu.in")).thenReturn(false);
        when(tenantUserRepository.existsByUserCode(anyString())).thenReturn(false);
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(invocation -> {
            TenantUser u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        TenantUserDetailResponse response = tenantUserService.createTenantUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("newteacher@greenwood.edu.in");
        assertThat(response.getUserType()).isEqualTo(TenantUserType.TEACHER);
        assertThat(response.getProfile()).isNotNull();
        assertThat(response.getProfile().getFirstName()).isEqualTo("Anita");
        verify(tenantUserRepository).save(any(TenantUser.class));
    }

    @Test
    @DisplayName("Create Tenant User — Duplicate Email throws DuplicateResourceException")
    void createTenantUser_DuplicateEmail() {
        CreateTenantUserRequest request = CreateTenantUserRequest.builder()
                .email("teacher@greenwood.edu.in")
                .firstName("Ramesh")
                .build();

        when(tenantUserRepository.existsByEmailIgnoreCase("teacher@greenwood.edu.in")).thenReturn(true);

        assertThatThrownBy(() -> tenantUserService.createTenantUser(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("TenantUser already exists with email");
    }

    @Test
    @DisplayName("Get Tenant User by ID — Success")
    void getTenantUserById_Success() {
        when(tenantUserRepository.findByIdAndNotDeleted(userId)).thenReturn(Optional.of(sampleUser));

        TenantUserDetailResponse response = tenantUserService.getTenantUserById(userId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("teacher@greenwood.edu.in");
        assertThat(response.getProfile().getFirstName()).isEqualTo("Ramesh");
    }

    @Test
    @DisplayName("Get Tenant User by ID — Not Found throws ResourceNotFoundException")
    void getTenantUserById_NotFound() {
        when(tenantUserRepository.findByIdAndNotDeleted(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantUserService.getTenantUserById(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Update Tenant User — Success")
    void updateTenantUser_Success() {
        UpdateTenantUserRequest request = UpdateTenantUserRequest.builder()
                .firstName("Ramesh Kumar")
                .phone("+919999999999")
                .city("Mysuru")
                .build();

        when(tenantUserRepository.findByIdAndNotDeleted(userId)).thenReturn(Optional.of(sampleUser));
        when(tenantUserRepository.save(any(TenantUser.class))).thenReturn(sampleUser);

        TenantUserDetailResponse response = tenantUserService.updateTenantUser(userId, request);

        assertThat(response).isNotNull();
        assertThat(sampleUser.getPhone()).isEqualTo("+919999999999");
        assertThat(sampleUser.getProfile().getFirstName()).isEqualTo("Ramesh Kumar");
        assertThat(sampleUser.getProfile().getCity()).isEqualTo("Mysuru");
    }

    @Test
    @DisplayName("Update Status — Success & Invalid Transition")
    void updateTenantUserStatus() {
        when(tenantUserRepository.findByIdAndNotDeleted(userId)).thenReturn(Optional.of(sampleUser));
        when(tenantUserRepository.save(any(TenantUser.class))).thenReturn(sampleUser);

        UpdateTenantUserStatusRequest request = UpdateTenantUserStatusRequest.builder()
                .status(TenantUserStatus.SUSPENDED)
                .build();

        TenantUserDetailResponse response = tenantUserService.updateTenantUserStatus(userId, request);
        assertThat(response.getStatus()).isEqualTo(TenantUserStatus.SUSPENDED);

        // Now from SUSPENDED, transition to INACTIVE is invalid
        UpdateTenantUserStatusRequest invalidRequest = UpdateTenantUserStatusRequest.builder()
                .status(TenantUserStatus.INACTIVE)
                .build();

        assertThatThrownBy(() -> tenantUserService.updateTenantUserStatus(userId, invalidRequest))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Delete Tenant User — Soft Delete")
    void deleteTenantUser_SoftDelete() {
        when(tenantUserRepository.findByIdAndNotDeleted(userId)).thenReturn(Optional.of(sampleUser));
        when(tenantUserRepository.save(any(TenantUser.class))).thenReturn(sampleUser);

        tenantUserService.deleteTenantUser(userId);

        assertThat(sampleUser.getStatus()).isEqualTo(TenantUserStatus.DELETED);
        assertThat(sampleUser.isDeleted()).isTrue();
        verify(tenantUserRepository).save(sampleUser);
    }

    @Test
    @DisplayName("List Tenant Users — Paginated Search")
    void listTenantUsers_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<TenantUser> page = new PageImpl<>(List.of(sampleUser), pageable, 1);

        when(tenantUserRepository.findActiveUsers(eq(TenantUserStatus.ACTIVE), eq(TenantUserType.TEACHER), eq(pageable)))
                .thenReturn(page);

        Page<TenantUserResponse> result = tenantUserService.listTenantUsers(TenantUserStatus.ACTIVE, TenantUserType.TEACHER, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("teacher@greenwood.edu.in");
    }
}
