package com.vidya.tenantdata.staff.repository;

import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Staff tenant entities.
 *
 * <p>All queries are tenant-scoped by virtue of the {@code TenantRoutingDataSource}.
 * Soft-deleted records (where {@code deletedAt IS NOT NULL}) are excluded from active queries.
 */
@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {

    Optional<Staff> findByStaffCode(String staffCode);

    Optional<Staff> findByEmployeeCode(String employeeCode);

    Optional<Staff> findByTenantUserId(UUID tenantUserId);

    boolean existsByStaffCode(String staffCode);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByTenantUserId(UUID tenantUserId);

    @Query("SELECT s FROM Staff s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Staff> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT s FROM Staff s WHERE s.staffCode = :staffCode AND s.deletedAt IS NULL")
    Optional<Staff> findByStaffCodeAndNotDeleted(@Param("staffCode") String staffCode);

    @Query("SELECT s FROM Staff s WHERE s.employeeCode = :employeeCode AND s.deletedAt IS NULL")
    Optional<Staff> findByEmployeeCodeAndNotDeleted(@Param("employeeCode") String employeeCode);

    @Query("SELECT s FROM Staff s WHERE s.tenantUser.id = :tenantUserId AND s.deletedAt IS NULL")
    Optional<Staff> findByTenantUserIdAndNotDeleted(@Param("tenantUserId") UUID tenantUserId);

    /**
     * Checks whether a non-deleted staff record already exists for the given tenant user,
     * optionally excluding a specific staff ID (used for update idempotency checks).
     */
    @Query("SELECT COUNT(s) > 0 FROM Staff s WHERE s.tenantUser.id = :tenantUserId AND s.deletedAt IS NULL AND (:excludeId IS NULL OR s.id <> :excludeId)")
    boolean existsActiveByTenantUserId(@Param("tenantUserId") UUID tenantUserId, @Param("excludeId") UUID excludeId);

    /**
     * Checks whether a non-deleted staff record already has the given employee code,
     * optionally excluding a specific staff ID (used for update checks).
     */
    @Query("SELECT COUNT(s) > 0 FROM Staff s WHERE s.employeeCode = :employeeCode AND s.deletedAt IS NULL AND (:excludeId IS NULL OR s.id <> :excludeId)")
    boolean existsActiveByEmployeeCode(@Param("employeeCode") String employeeCode, @Param("excludeId") UUID excludeId);

    /**
     * Full-featured search with optional filters and pagination.
     * Searches staffCode, employeeCode, designation, department, and TenantUser profile names.
     */
    @Query("SELECT s FROM Staff s LEFT JOIN s.tenantUser u LEFT JOIN u.profile p " +
           "WHERE s.deletedAt IS NULL " +
           "AND (:status IS NULL OR s.employmentStatus = :status) " +
           "AND (:staffType IS NULL OR s.staffType = :staffType) " +
           "AND (CAST(:department AS string) IS NULL OR LOWER(s.department) LIKE LOWER(CONCAT('%', CAST(:department AS string), '%'))) " +
           "AND (CAST(:designation AS string) IS NULL OR LOWER(s.designation) LIKE LOWER(CONCAT('%', CAST(:designation AS string), '%'))) " +
           "AND (CAST(:searchQuery AS string) IS NULL " +
           "     OR LOWER(s.staffCode) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')) " +
           "     OR LOWER(s.employeeCode) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')) " +
           "     OR (p.firstName IS NOT NULL AND LOWER(p.firstName) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%'))) " +
           "     OR (p.lastName IS NOT NULL AND LOWER(p.lastName) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%'))) " +
           "     OR (u.email IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%'))))")
    Page<Staff> searchStaff(
            @Param("status") EmploymentStatus status,
            @Param("staffType") StaffType staffType,
            @Param("department") String department,
            @Param("designation") String designation,
            @Param("searchQuery") String searchQuery,
            Pageable pageable
    );
}
