package com.vidya.tenantdata.staff.repository;

import com.vidya.tenantdata.staff.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    @Query("SELECT x FROM Department x WHERE x.deletedAt IS NULL ORDER BY x.sortOrder, x.name")
    List<Department> findAllNotDeleted();

    @Query("SELECT x FROM Department x WHERE x.id = :id AND x.deletedAt IS NULL")
    Optional<Department> findByIdAndNotDeleted(UUID id);

    @Query("SELECT x FROM Department x WHERE upper(x.code) = upper(:code) AND x.deletedAt IS NULL")
    Optional<Department> findByCode(String code);

    @Query("SELECT x FROM Department x WHERE lower(x.name) = lower(:name) AND x.deletedAt IS NULL")
    Optional<Department> findByName(String name);
}
