package com.vidya.security.access.repository;

import com.vidya.security.access.entity.AccessPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessPermissionRepository extends JpaRepository<AccessPermission, UUID> {

    Optional<AccessPermission> findByCode(String code);

    boolean existsByCode(String code);

    List<AccessPermission> findByCodeIn(Collection<String> codes);

    List<AccessPermission> findByActiveTrue();

    List<AccessPermission> findAllByOrderByModuleCodeAscCodeAsc();

    /** Active permissions granted by any of the given (active) roles. */
    @Query("SELECT DISTINCT p FROM AccessRole r JOIN r.permissions p "
            + "WHERE r.id IN :roleIds AND r.active = true AND p.active = true")
    List<AccessPermission> findActiveGrantedByRoles(@Param("roleIds") Collection<UUID> roleIds);

    /** Matrix cells of the given permission codes, as rows [sectionCode, actionCode]. */
    @Query("SELECT c.sectionCode, c.actionCode FROM AccessPermission p JOIN p.matrixCells c "
            + "WHERE p.code IN :codes AND p.active = true")
    List<Object[]> findMatrixCellsByCodes(@Param("codes") Collection<String> codes);
}
