package com.vidya.tenantdata.staff.mapper;

import com.vidya.tenantdata.staff.dto.CreateStaffRequest;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.dto.StaffResponse;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.mapper.TenantUserMapper;

/**
 * Mapper for converting between Staff entities and DTOs.
 */
public final class StaffMapper {

    private StaffMapper() {}

    public static Staff toEntity(CreateStaffRequest request, TenantUser tenantUser, String staffCode) {
        return new Staff(
                staffCode,
                tenantUser,
                request.getEmployeeCode(),
                request.getJoiningDate(),
                request.getStaffType(),
                request.getDesignation(),
                request.getDepartment()
        );
    }

    public static StaffResponse toResponse(Staff staff) {
        if (staff == null) {
            return null;
        }

        String fullName = null;
        String email = null;
        String phone = null;
        java.util.UUID tenantUserId = null;

        if (staff.getTenantUser() != null) {
            tenantUserId = staff.getTenantUser().getId();
            email = staff.getTenantUser().getEmail();
            phone = staff.getTenantUser().getPhone();
            if (staff.getTenantUser().getProfile() != null) {
                fullName = staff.getTenantUser().getProfile().getFullName();
            }
        }

        return StaffResponse.builder()
                .id(staff.getId())
                .staffCode(staff.getStaffCode())
                .tenantUserId(tenantUserId)
                .employeeCode(staff.getEmployeeCode())
                .staffType(staff.getStaffType())
                .employmentStatus(staff.getEmploymentStatus())
                .designation(staff.getDesignation())
                .department(staff.getDepartment())
                .joiningDate(staff.getJoiningDate())
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .build();
    }

    public static StaffDetailResponse toDetailResponse(Staff staff) {
        if (staff == null) {
            return null;
        }

        return StaffDetailResponse.builder()
                .id(staff.getId())
                .staffCode(staff.getStaffCode())
                .employeeCode(staff.getEmployeeCode())
                .staffType(staff.getStaffType())
                .employmentStatus(staff.getEmploymentStatus())
                .designation(staff.getDesignation())
                .department(staff.getDepartment())
                .joiningDate(staff.getJoiningDate())
                .user(staff.getTenantUser() != null ? TenantUserMapper.toDetailResponse(staff.getTenantUser()) : null)
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .version(staff.getVersion())
                .build();
    }
}
