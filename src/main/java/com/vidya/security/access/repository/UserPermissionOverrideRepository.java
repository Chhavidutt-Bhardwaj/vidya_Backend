package com.vidya.security.access.repository;

import com.vidya.security.access.entity.UserPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, UUID> {

    @Query("SELECT o FROM UserPermissionOverride o JOIN FETCH o.permission WHERE o.userId = :userId")
    List<UserPermissionOverride> findByUserIdWithPermission(@Param("userId") UUID userId);

    Optional<UserPermissionOverride> findByUserIdAndPermissionId(UUID userId, UUID permissionId);
}
