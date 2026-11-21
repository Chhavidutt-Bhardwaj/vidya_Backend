package com.vidya.tenantdata.staff.repository;

import com.vidya.tenantdata.staff.entity.DutyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DutyTypeRepository extends JpaRepository<DutyType, UUID> {

    @Query("SELECT t FROM DutyType t WHERE t.deletedAt IS NULL ORDER BY t.category, t.name")
    List<DutyType> findAllNotDeleted();

    @Query("SELECT t FROM DutyType t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<DutyType> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT t FROM DutyType t WHERE upper(t.code) = upper(:code) AND t.deletedAt IS NULL")
    Optional<DutyType> findByCode(@Param("code") String code);

    @Query("SELECT t FROM DutyType t WHERE lower(t.name) = lower(:name) AND t.deletedAt IS NULL")
    Optional<DutyType> findByName(@Param("name") String name);
}
