package com.vidya.tenantdata.fee.online.repository;

import com.vidya.tenantdata.fee.online.entity.FeeGatewayAccount;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeeGatewayAccountRepository extends JpaRepository<FeeGatewayAccount, UUID> {

    Optional<FeeGatewayAccount> findByProvider(GatewayProvider provider);
}
