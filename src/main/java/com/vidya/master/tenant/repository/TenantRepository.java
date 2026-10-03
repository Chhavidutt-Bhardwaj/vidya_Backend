package com.vidya.master.tenant.repository;

import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link Tenant} master entity.
 *
 * <p>This repository is wired to the <strong>master DataSource</strong>
 * via {@code MasterDataSourceConfig} which scans {@code com.vidya.master.*}.
 *
 * <p>Never inject this repository in tenant-scoped services.
 * It is strictly a platform/master DB concern.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    Optional<Tenant> findBySchoolId(UUID schoolId);

    Optional<Tenant> findByDatabaseName(String databaseName);

    boolean existsByTenantCode(String tenantCode);

    boolean existsByDatabaseName(String databaseName);

    @Query("SELECT t FROM Tenant t WHERE t.schoolId = :schoolId AND t.status = :status")
    Optional<Tenant> findBySchoolIdAndStatus(@Param("schoolId") UUID schoolId,
                                             @Param("status") TenantStatus status);
}
