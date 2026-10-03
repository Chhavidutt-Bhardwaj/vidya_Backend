package com.vidya.master.school.mapper;

import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolResponse;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBranch;
import org.springframework.stereotype.Component;

/**
 * Maps between School domain entities and DTO records.
 */
@Component
public class SchoolMapper {

    public SchoolResponse toResponse(School entity, long branchCount, SchoolBranch mainBranch) {
        if (entity == null) {
            return null;
        }
        return new SchoolResponse(
                entity.getId(),
                entity.getSchoolCode(),
                entity.getTenantId(),
                entity.getSchoolName(),
                entity.getLegalName(),
                entity.getRegistrationNumber(),
                entity.getBoard(),
                entity.getSchoolType(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getWebsite(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                branchCount,
                toBranchResponse(mainBranch)
        );
    }

    public SchoolBranchResponse toBranchResponse(SchoolBranch branch) {
        if (branch == null) {
            return null;
        }
        return new SchoolBranchResponse(
                branch.getId(),
                branch.getSchool() != null ? branch.getSchool().getId() : null,
                branch.getSchool() != null ? branch.getSchool().getSchoolCode() : null,
                branch.getBranchCode(),
                branch.getBranchName(),
                branch.isMainBranch(),
                branch.getAddressLine1(),
                branch.getAddressLine2(),
                branch.getCity(),
                branch.getState(),
                branch.getPincode(),
                branch.getCountry(),
                branch.getContactEmail(),
                branch.getContactPhone(),
                branch.getStatus(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
