package com.vidya.tenantdata.staff.repository;

import com.vidya.tenantdata.staff.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    @Query("SELECT x FROM Designation x WHERE x.deletedAt IS NULL ORDER BY x.rank, x.name")
    List<Designation> findAllNotDeleted();

    @Query("SELECT x FROM Designation x WHERE x.id = :id AND x.deletedAt IS NULL")
    Optional<Designation> findByIdAndNotDeleted(UUID id);

    @Query("SELECT x FROM Designation x WHERE upper(x.code) = upper(:code) AND x.deletedAt IS NULL")
    Optional<Designation> findByCode(String code);

    @Query("SELECT x FROM Designation x WHERE lower(x.name) = lower(:name) AND x.deletedAt IS NULL")
    Optional<Designation> findByName(String name);
}
