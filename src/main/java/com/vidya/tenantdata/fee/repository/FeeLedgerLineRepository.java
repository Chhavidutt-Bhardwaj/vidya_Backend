package com.vidya.tenantdata.fee.repository;

import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeeLedgerLineRepository extends JpaRepository<FeeLedgerLine, UUID> {

    /** Oldest due first: the order in which an unallocated payment is applied. */
    List<FeeLedgerLine> findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(UUID ledgerId);

    List<FeeLedgerLine> findByLedgerIdIn(Collection<UUID> ledgerIds);
}
