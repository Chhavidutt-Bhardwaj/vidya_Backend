package com.vidya.tenantdata.user.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.user.dto.CreateTenantUserRequest;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.dto.TenantUserResponse;
import com.vidya.tenantdata.user.dto.UpdateTenantUserRequest;
import com.vidya.tenantdata.user.dto.UpdateTenantUserStatusRequest;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.mapper.TenantUserMapper;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserServiceImpl implements TenantUserService {

    private final TenantUserRepository tenantUserRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TenantUserDetailResponse createTenantUser(CreateTenantUserRequest request) {
        log.info("Creating tenant operational user for email: {}", request.getEmail());

        String email = request.getEmail().trim().toLowerCase();
        if (tenantUserRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("TenantUser", "email", email);
        }

        String userCode = CodeGenerator.userCode();
        while (tenantUserRepository.existsByUserCode(userCode)) {
            userCode = CodeGenerator.userCode();
        }

        TenantUser user = TenantUserMapper.toEntity(request, userCode);
        TenantUser saved = tenantUserRepository.save(user);

        log.info("Created tenant user with ID: {} and code: {}", saved.getId(), saved.getUserCode());
        return TenantUserMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TenantUserDetailResponse getTenantUserById(UUID id) {
        TenantUser user = tenantUserRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantUser", id));
        return TenantUserMapper.toDetailResponse(user);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TenantUserDetailResponse getTenantUserByUserCode(String userCode) {
        TenantUser user = tenantUserRepository.findByUserCode(userCode)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("TenantUser", userCode));
        return TenantUserMapper.toDetailResponse(user);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TenantUserDetailResponse getTenantUserByEmail(String email) {
        TenantUser user = tenantUserRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("TenantUser", email));
        return TenantUserMapper.toDetailResponse(user);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TenantUserDetailResponse updateTenantUser(UUID id, UpdateTenantUserRequest request) {
        log.info("Updating tenant operational user ID: {}", id);

        TenantUser user = tenantUserRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantUser", id));

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getUserType() != null) {
            user.setUserType(request.getUserType());
        }

        TenantUserProfile profile = user.getProfile();
        if (profile == null) {
            profile = new TenantUserProfile(user, request.getFirstName() != null ? request.getFirstName() : "User", request.getLastName());
            user.setProfile(profile);
        } else {
            if (request.getFirstName() != null) profile.setFirstName(request.getFirstName().trim());
            if (request.getLastName() != null) profile.setLastName(request.getLastName().trim());
        }

        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getDateOfBirth() != null) profile.setDateOfBirth(request.getDateOfBirth());
        if (request.getAddressLine1() != null) profile.setAddressLine1(request.getAddressLine1());
        if (request.getAddressLine2() != null) profile.setAddressLine2(request.getAddressLine2());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getState() != null) profile.setState(request.getState());
        if (request.getPostalCode() != null) profile.setPostalCode(request.getPostalCode());
        if (request.getEmergencyContactName() != null) profile.setEmergencyContactName(request.getEmergencyContactName());
        if (request.getEmergencyContactPhone() != null) profile.setEmergencyContactPhone(request.getEmergencyContactPhone());

        TenantUser saved = tenantUserRepository.save(user);
        log.info("Successfully updated tenant user ID: {}", saved.getId());
        return TenantUserMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TenantUserDetailResponse updateTenantUserStatus(UUID id, UpdateTenantUserStatusRequest request) {
        log.info("Changing tenant user ID: {} status to: {}", id, request.getStatus());

        TenantUser user = tenantUserRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantUser", id));

        user.updateStatus(request.getStatus());
        TenantUser saved = tenantUserRepository.save(user);

        log.info("Updated tenant user ID: {} status to: {}", saved.getId(), saved.getStatus());
        return TenantUserMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteTenantUser(UUID id) {
        log.info("Soft-deleting tenant user ID: {}", id);

        TenantUser user = tenantUserRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantUser", id));

        user.softDelete();
        tenantUserRepository.save(user);
        log.info("Soft-deleted tenant user ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<TenantUserResponse> listTenantUsers(TenantUserStatus status, TenantUserType userType, Pageable pageable) {
        return tenantUserRepository.findActiveUsers(status, userType, pageable)
                .map(TenantUserMapper::toResponse);
    }
}
