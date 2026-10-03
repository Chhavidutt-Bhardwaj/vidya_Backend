package com.vidya.master.school.repository;

import com.vidya.master.school.entity.SchoolBranch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link SchoolBranch} entity.
 */
@Repository
public interface SchoolBranchRepository extends JpaRepository<SchoolBranch, UUID> {

    Optional<SchoolBranch> findByBranchCode(String branchCode);

    List<SchoolBranch> findBySchoolId(UUID schoolId);

    Page<SchoolBranch> findBySchoolId(UUID schoolId, Pageable pageable);

    Optional<SchoolBranch> findBySchoolIdAndMainBranchTrue(UUID schoolId);

    long countBySchoolId(UUID schoolId);
}
