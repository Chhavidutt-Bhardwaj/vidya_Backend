package com.vidya.tenantdata.user.repository;

import com.vidya.tenantdata.user.entity.TenantUserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUserProfileRepository extends JpaRepository<TenantUserProfile, UUID> {

    Optional<TenantUserProfile> findByUserId(UUID userId);
}
