package com.vidya.tenantdata.fee.repository;

import com.vidya.tenantdata.fee.entity.FeePaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeePaymentAllocationRepository extends JpaRepository<FeePaymentAllocation, UUID> {

    List<FeePaymentAllocation> findByPaymentId(UUID paymentId);

    List<FeePaymentAllocation> findByPaymentIdIn(Collection<UUID> paymentIds);

    boolean existsByLedgerLineId(UUID ledgerLineId);
}
