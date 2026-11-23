package com.vidya.security.access.repository;

import com.vidya.security.access.entity.AccessRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRoleRepository extends JpaRepository<AccessRole, UUID> {

    @Query("SELECT r FROM AccessRole r WHERE r.code = :code AND r.schoolId IS NULL")
    Optional<AccessRole> findGlobalByCode(@Param("code") String code);

    Optional<AccessRole> findByCodeAndSchoolId(String code, UUID schoolId);

    @Query("SELECT r FROM AccessRole r WHERE r.schoolId IS NULL ORDER BY r.scope, r.code")
    List<AccessRole> findAllGlobal();

    /** Roles a school may use: its own custom roles plus global roles of school scope. */
    @Query("SELECT r FROM AccessRole r WHERE r.schoolId = :schoolId "
            + "OR (r.schoolId IS NULL AND r.scope IN (com.vidya.security.access.AccessScope.SCHOOL, "
            + "com.vidya.security.access.AccessScope.SCHOOL_PORTAL)) ORDER BY r.schoolId NULLS FIRST, r.code")
    List<AccessRole> findAvailableForSchool(@Param("schoolId") UUID schoolId);

    @EntityGraph(attributePaths = "permissions")
    @Query("SELECT r FROM AccessRole r WHERE r.id = :id")
    Optional<AccessRole> findWithPermissionsById(@Param("id") UUID id);
}
