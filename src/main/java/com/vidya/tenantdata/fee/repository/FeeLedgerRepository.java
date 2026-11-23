package com.vidya.tenantdata.fee.repository;

import com.vidya.tenantdata.fee.entity.FeeLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeLedgerRepository extends JpaRepository<FeeLedger, UUID> {

    Optional<FeeLedger> findByStudentIdAndAcademicYearId(UUID studentId, UUID academicYearId);

    List<FeeLedger> findByAcademicYearIdAndStudentIdIn(UUID academicYearId, Collection<UUID> studentIds);

    /** Every change to a ledger's lines (payment, concession, charge, cancel) holds this row lock until commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM FeeLedger l WHERE l.id = :id")
    Optional<FeeLedger> lock(@Param("id") UUID id);
}
