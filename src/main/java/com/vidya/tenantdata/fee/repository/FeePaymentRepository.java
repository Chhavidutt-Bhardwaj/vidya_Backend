package com.vidya.tenantdata.fee.repository;

import com.vidya.tenantdata.fee.entity.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeePaymentRepository extends JpaRepository<FeePayment, UUID> {

    List<FeePayment> findByLedgerIdOrderByPaidOnAscCreatedAtAsc(UUID ledgerId);
}
