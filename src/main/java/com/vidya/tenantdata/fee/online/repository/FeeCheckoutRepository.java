package com.vidya.tenantdata.fee.online.repository;

import com.vidya.tenantdata.fee.online.entity.FeeCheckout;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FeeCheckoutRepository extends JpaRepository<FeeCheckout, UUID> {

    Optional<FeeCheckout> findByReference(String reference);

    Optional<FeeCheckout> findFirstByProviderAndGatewayId(GatewayProvider provider, String gatewayId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FeeCheckout c WHERE c.id = :id")
    Optional<FeeCheckout> lock(@Param("id") UUID id);
}
