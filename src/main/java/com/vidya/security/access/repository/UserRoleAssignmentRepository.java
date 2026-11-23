package com.vidya.security.access.repository;

import com.vidya.security.access.entity.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

    @Query("SELECT a FROM UserRoleAssignment a JOIN FETCH a.role WHERE a.userId = :userId")
    List<UserRoleAssignment> findByUserIdWithRole(@Param("userId") UUID userId);

    @Query("SELECT a FROM UserRoleAssignment a JOIN FETCH a.role WHERE a.schoolId = :schoolId")
    List<UserRoleAssignment> findBySchoolIdWithRole(@Param("schoolId") UUID schoolId);

    Optional<UserRoleAssignment> findByUserIdAndRoleId(UUID userId, UUID roleId);

    @Query("SELECT DISTINCT a.userId FROM UserRoleAssignment a WHERE a.role.id = :roleId")
    List<UUID> findUserIdsByRoleId(@Param("roleId") UUID roleId);
}
