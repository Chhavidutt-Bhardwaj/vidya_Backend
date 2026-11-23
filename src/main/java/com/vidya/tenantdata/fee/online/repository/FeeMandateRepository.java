package com.vidya.tenantdata.fee.online.repository;

import com.vidya.tenantdata.fee.online.entity.FeeMandate;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeMandateRepository extends JpaRepository<FeeMandate, UUID> {

    Optional<FeeMandate> findByReference(String reference);

    Optional<FeeMandate> findFirstByProviderAndGatewayMandateId(GatewayProvider provider, String gatewayMandateId);

    Optional<FeeMandate> findFirstByProviderAndGatewayAuthId(GatewayProvider provider, String gatewayAuthId);

    List<FeeMandate> findByProviderAndGatewayCustomerIdAndStatusOrderByCreatedAtDesc(GatewayProvider provider, String customerId, MandateStatus status);

    List<FeeMandate> findByStatusIn(Collection<MandateStatus> statuses);

    Page<FeeMandate> findAllByOrderByCreatedAtDesc(Pageable page);

    Page<FeeMandate> findByStatusInOrderByCreatedAtDesc(Collection<MandateStatus> statuses, Pageable page);

    long countByStatus(MandateStatus status);

    List<FeeMandate> findByGuardianUserIdInOrderByCreatedAtDesc(Collection<UUID> guardianUserIds);

    /** Status changes (webhook, sync, cancel) hold this lock so two webhooks cannot race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM FeeMandate m WHERE m.id = :id")
    Optional<FeeMandate> lock(@Param("id") UUID id);
}
