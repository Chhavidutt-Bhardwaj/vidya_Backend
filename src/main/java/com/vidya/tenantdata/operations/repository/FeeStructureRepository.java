package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, UUID> {

    List<FeeStructure> findByAcademicYearIdAndDeletedAtIsNull(UUID academicYearId);

    Optional<FeeStructure> findByIdAndDeletedAtIsNull(UUID id);
}
