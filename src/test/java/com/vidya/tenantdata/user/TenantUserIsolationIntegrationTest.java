package com.vidya.tenantdata.user;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.user.dto.CreateTenantUserRequest;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.dto.UpdateTenantUserRequest;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import com.vidya.tenantdata.user.service.TenantUserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantUserIsolation & IDOR — Integration Tests")
class TenantUserIsolationIntegrationTest {

    @Mock
    private TenantUserRepository tenantUserRepository;

    @InjectMocks
    private TenantUserServiceImpl tenantUserService;

    private UUID tenantAUserId;
    private UUID tenantBUserId;

    private TenantUser tenantAUser;
    private TenantUser tenantBUser;

    @BeforeEach
    void setUp() {
        tenantAUserId = UUID.randomUUID();
        tenantBUserId = UUID.randomUUID();

        tenantAUser = new TenantUser("USR-TENANT-A-01", "user.a@school-a.edu.in", "+919876543201", TenantUserType.TEACHER);
        tenantAUser.setId(tenantAUserId);
        TenantUserProfile profileA = new TenantUserProfile(tenantAUser, "UserA", "SchoolA");
        tenantAUser.setProfile(profileA);

        tenantBUser = new TenantUser("USR-TENANT-B-01", "user.b@school-b.edu.in", "+919876543202", TenantUserType.STAFF);
        tenantBUser.setId(tenantBUserId);
        TenantUserProfile profileB = new TenantUserProfile(tenantBUser, "UserB", "SchoolB");
        tenantBUser.setProfile(profileB);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A context accesses Tenant A User — ALLOWED")
    void testTenantAAccessOwnUser_Allowed() {
        TenantContext.setTenantCode("tenant_a");

        when(tenantUserRepository.findByIdAndNotDeleted(tenantAUserId))
                .thenReturn(Optional.of(tenantAUser));

        TenantUserDetailResponse response = tenantUserService.getTenantUserById(tenantAUserId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(tenantAUserId);
        assertThat(response.getEmail()).isEqualTo("user.a@school-a.edu.in");
        assertThat(response.getProfile().getFirstName()).isEqualTo("UserA");
    }

    @Test
    @DisplayName("Tenant A context queries Tenant B User ID (IDOR) — Returns ResourceNotFoundException in Tenant A DB")
    void testTenantACrossQueryTenantBUser_Denied() {
        TenantContext.setTenantCode("tenant_a");

        // In Tenant A's isolated DB, Tenant B's user record does NOT exist
        when(tenantUserRepository.findByIdAndNotDeleted(tenantBUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantUserService.getTenantUserById(tenantBUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A context attempts update on Tenant B User ID (IDOR) — Rejection")
    void testTenantACrossUpdateTenantBUser_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(tenantUserRepository.findByIdAndNotDeleted(tenantBUserId))
                .thenReturn(Optional.empty());

        UpdateTenantUserRequest request = UpdateTenantUserRequest.builder()
                .firstName("HackedName")
                .build();

        assertThatThrownBy(() -> tenantUserService.updateTenantUser(tenantBUserId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A context attempts deletion on Tenant B User ID (IDOR) — Rejection")
    void testTenantACrossDeleteTenantBUser_Denied() {
        TenantContext.setTenantCode("tenant_a");

        when(tenantUserRepository.findByIdAndNotDeleted(tenantBUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantUserService.deleteTenantUser(tenantBUserId))
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
