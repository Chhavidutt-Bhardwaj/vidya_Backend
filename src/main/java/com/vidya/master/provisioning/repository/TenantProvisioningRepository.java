package com.vidya.master.provisioning.repository;

import com.vidya.master.provisioning.entity.TenantProvisioning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TenantProvisioning} records.
 * Wired to the master DataSource via {@code MasterDataSourceConfig}.
 */
@Repository
public interface TenantProvisioningRepository extends JpaRepository<TenantProvisioning, UUID> {

    Optional<TenantProvisioning> findByTenantId(UUID tenantId);

    Optional<TenantProvisioning> findByProvisioningCode(String provisioningCode);

    boolean existsByTenantId(UUID tenantId);
}
