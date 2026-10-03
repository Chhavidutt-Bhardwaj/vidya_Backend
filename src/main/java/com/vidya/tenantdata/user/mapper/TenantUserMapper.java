package com.vidya.tenantdata.user.mapper;

import com.vidya.tenantdata.user.dto.CreateTenantUserRequest;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.dto.TenantUserProfileDto;
import com.vidya.tenantdata.user.dto.TenantUserResponse;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;

public final class TenantUserMapper {

    private TenantUserMapper() {}

    public static TenantUser toEntity(CreateTenantUserRequest request, String userCode) {
        TenantUser user = new TenantUser(
                userCode,
                request.getMasterUserId(),
                request.getEmail(),
                request.getPhone(),
                request.getUserType()
        );

        TenantUserProfile profile = new TenantUserProfile(user, request.getFirstName(), request.getLastName());
        profile.setGender(request.getGender());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setPostalCode(request.getPostalCode());

        user.setProfile(profile);
        return user;
    }

    public static TenantUserResponse toResponse(TenantUser user) {
        if (user == null) return null;

        String fullName = null;
        if (user.getProfile() != null) {
            fullName = user.getProfile().getFullName();
        }

        return TenantUserResponse.builder()
                .id(user.getId())
                .userCode(user.getUserCode())
                .masterUserId(user.getMasterUserId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(fullName)
                .userType(user.getUserType())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static TenantUserDetailResponse toDetailResponse(TenantUser user) {
        if (user == null) return null;

        TenantUserProfileDto profileDto = null;
        if (user.getProfile() != null) {
            TenantUserProfile p = user.getProfile();
            profileDto = TenantUserProfileDto.builder()
                    .id(p.getId())
                    .firstName(p.getFirstName())
                    .lastName(p.getLastName())
                    .displayName(p.getDisplayName())
                    .gender(p.getGender())
                    .dateOfBirth(p.getDateOfBirth())
                    .addressLine1(p.getAddressLine1())
                    .addressLine2(p.getAddressLine2())
                    .city(p.getCity())
                    .state(p.getState())
                    .postalCode(p.getPostalCode())
                    .country(p.getCountry())
                    .emergencyContactName(p.getEmergencyContactName())
                    .emergencyContactPhone(p.getEmergencyContactPhone())
                    .build();
        }

        return TenantUserDetailResponse.builder()
                .id(user.getId())
                .userCode(user.getUserCode())
                .masterUserId(user.getMasterUserId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .userType(user.getUserType())
                .status(user.getStatus())
                .profile(profileDto)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .updatedBy(user.getUpdatedBy())
                .version(user.getVersion())
                .build();
    }
}
