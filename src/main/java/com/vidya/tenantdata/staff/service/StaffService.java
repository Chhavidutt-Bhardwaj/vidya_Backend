package com.vidya.tenantdata.staff.service;

import com.vidya.tenantdata.staff.dto.CreateStaffRequest;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.dto.StaffResponse;
import com.vidya.tenantdata.staff.dto.UpdateStaffRequest;
import com.vidya.tenantdata.staff.dto.UpdateStaffStatusRequest;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Business operations for Staff management in the tenant database.
 */
public interface StaffService {

    StaffDetailResponse createStaff(CreateStaffRequest request);

    StaffDetailResponse getStaffById(UUID id);

    StaffDetailResponse getStaffByStaffCode(String staffCode);

    StaffDetailResponse getStaffByTenantUserId(UUID tenantUserId);

    StaffDetailResponse updateStaff(UUID id, UpdateStaffRequest request);

    StaffDetailResponse updateStaffStatus(UUID id, UpdateStaffStatusRequest request);

    void deleteStaff(UUID id);

    Page<StaffResponse> searchStaff(EmploymentStatus status, StaffType staffType,
                                    String department, String designation,
                                    String searchQuery, Pageable pageable);
}
