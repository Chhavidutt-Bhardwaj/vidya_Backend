package com.vidya.tenantdata.user.repository;

import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    Optional<TenantUser> findByUserCode(String userCode);

    Optional<TenantUser> findByEmailIgnoreCase(String email);

    Optional<TenantUser> findByMasterUserId(UUID masterUserId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserCode(String userCode);

    @Query("SELECT u FROM TenantUser u WHERE u.deletedAt IS NULL AND (:status IS NULL OR u.status = :status) AND (:userType IS NULL OR u.userType = :userType)")
    Page<TenantUser> findActiveUsers(
            @Param("status") TenantUserStatus status,
            @Param("userType") TenantUserType userType,
            Pageable pageable
    );

    @Query("SELECT u FROM TenantUser u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<TenantUser> findByIdAndNotDeleted(@Param("id") UUID id);
}
