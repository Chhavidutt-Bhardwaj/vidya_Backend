package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.FeeInstallment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeInstallmentRepository extends JpaRepository<FeeInstallment, UUID> {

    List<FeeInstallment> findByAcademicYearIdOrderByInstallmentNoAsc(UUID academicYearId);
}
