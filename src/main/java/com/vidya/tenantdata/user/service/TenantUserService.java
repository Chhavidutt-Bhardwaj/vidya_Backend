package com.vidya.tenantdata.user.service;

import com.vidya.tenantdata.user.dto.CreateTenantUserRequest;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.dto.TenantUserResponse;
import com.vidya.tenantdata.user.dto.UpdateTenantUserRequest;
import com.vidya.tenantdata.user.dto.UpdateTenantUserStatusRequest;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TenantUserService {

    TenantUserDetailResponse createTenantUser(CreateTenantUserRequest request);

    TenantUserDetailResponse getTenantUserById(UUID id);

    TenantUserDetailResponse getTenantUserByUserCode(String userCode);

    TenantUserDetailResponse getTenantUserByEmail(String email);

    TenantUserDetailResponse updateTenantUser(UUID id, UpdateTenantUserRequest request);

    TenantUserDetailResponse updateTenantUserStatus(UUID id, UpdateTenantUserStatusRequest request);

    void deleteTenantUser(UUID id);

    Page<TenantUserResponse> listTenantUsers(TenantUserStatus status, TenantUserType userType, Pageable pageable);
}
