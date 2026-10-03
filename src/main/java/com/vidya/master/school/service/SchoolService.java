package com.vidya.master.school.service;

import com.vidya.master.school.dto.CreateBranchRequest;
import com.vidya.master.school.dto.CreateSchoolRequest;
import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolResponse;
import com.vidya.master.school.dto.UpdateBranchRequest;
import com.vidya.master.school.dto.UpdateSchoolRequest;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for School and Branch management operations.
 */
public interface SchoolService {

    SchoolResponse createSchool(CreateSchoolRequest request);

    SchoolResponse getSchoolById(UUID id);

    SchoolResponse getSchoolByCode(String code);

    SchoolResponse getSchoolByTenantId(UUID tenantId);

    Page<SchoolResponse> getAllSchools(Pageable pageable, SchoolStatus status, SchoolBoard board, String search);

    SchoolResponse updateSchool(UUID id, UpdateSchoolRequest request);

    SchoolResponse changeSchoolStatus(UUID id, SchoolStatus newStatus);

    SchoolResponse linkTenant(UUID schoolId, UUID tenantId);

    SchoolBranchResponse addBranch(UUID schoolId, CreateBranchRequest request);

    SchoolBranchResponse updateBranch(UUID schoolId, UUID branchId, UpdateBranchRequest request);

    Page<SchoolBranchResponse> getSchoolBranches(UUID schoolId, Pageable pageable);

    SchoolBranchResponse getBranchById(UUID schoolId, UUID branchId);
}
