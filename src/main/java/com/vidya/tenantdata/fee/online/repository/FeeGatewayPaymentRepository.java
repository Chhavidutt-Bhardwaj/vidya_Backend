package com.vidya.tenantdata.fee.online.repository;

import com.vidya.tenantdata.fee.online.entity.FeeGatewayPayment;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeGatewayPaymentRepository extends JpaRepository<FeeGatewayPayment, UUID> {

    boolean existsByProviderAndGatewayPaymentId(GatewayProvider provider, String gatewayPaymentId);

    List<FeeGatewayPayment> findByCheckoutIdOrderByCreatedAtAsc(UUID checkoutId);
}
