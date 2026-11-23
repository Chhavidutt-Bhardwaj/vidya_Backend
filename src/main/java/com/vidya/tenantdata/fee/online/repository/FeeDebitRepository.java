package com.vidya.tenantdata.fee.online.repository;

import com.vidya.tenantdata.fee.online.entity.FeeDebit;
import com.vidya.tenantdata.fee.online.enums.DebitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeDebitRepository extends JpaRepository<FeeDebit, UUID> {

    Optional<FeeDebit> findByReference(String reference);

    Optional<FeeDebit> findFirstByGatewayOrderId(String gatewayOrderId);

    Optional<FeeDebit> findFirstByGatewayPaymentId(String gatewayPaymentId);

    List<FeeDebit> findByStatusAndScheduledOnLessThanEqualOrderByScheduledOnAscSiblingRankAsc(DebitStatus status, LocalDate on);

    List<FeeDebit> findByStatusAndScheduledOnIn(DebitStatus status, Collection<LocalDate> dates);

    List<FeeDebit> findByMandateIdAndCycleMonth(UUID mandateId, LocalDate cycleMonth);

    List<FeeDebit> findByMandateIdAndStatusIn(UUID mandateId, Collection<DebitStatus> statuses);

    List<FeeDebit> findByStudentIdOrderByScheduledOnDesc(UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM FeeDebit d WHERE d.id = :id")
    Optional<FeeDebit> lock(@Param("id") UUID id);
}
