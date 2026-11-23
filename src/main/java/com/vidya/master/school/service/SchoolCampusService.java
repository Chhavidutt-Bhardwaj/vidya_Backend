package com.vidya.master.school.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.exception.TenantNotFoundException;
import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolCampusesResponse;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBranch;
import com.vidya.master.school.mapper.SchoolMapper;
import com.vidya.master.school.repository.SchoolBranchRepository;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Campuses of the school behind the current request's tenant (already resolved and authorised by the tenant filter). */
@Service
@RequiredArgsConstructor
public class SchoolCampusService {

    private final TenantRegistryService tenantRegistryService;
    private final SchoolRepository schoolRepository;
    private final SchoolBranchRepository branchRepository;
    private final SchoolMapper mapper;

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public SchoolCampusesResponse currentSchoolCampuses() {
        String code = TenantContext.getTenantCode();
        UUID schoolId = tenantRegistryService.findByTenantCode(code).map(TenantRegistration::schoolId)
                .orElseThrow(() -> new TenantNotFoundException(code));
        School school = schoolRepository.findById(schoolId).orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
        List<SchoolBranchResponse> campuses = branchRepository.findBySchoolId(schoolId).stream()
                .sorted(Comparator.comparing((SchoolBranch b) -> !b.isMainBranch())
                        .thenComparing(SchoolBranch::getBranchName, String.CASE_INSENSITIVE_ORDER))
                .map(mapper::toBranchResponse)
                .toList();
        return new SchoolCampusesResponse(school.getId(), school.getSchoolCode(), school.getSchoolName(),
                school.getBoard() == null ? null : school.getBoard().name(), school.getStatus() == null ? null : school.getStatus().name(), campuses);
    }
}
