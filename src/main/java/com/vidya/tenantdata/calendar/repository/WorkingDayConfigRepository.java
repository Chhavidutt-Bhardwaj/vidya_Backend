package com.vidya.tenantdata.calendar.repository;

import com.vidya.tenantdata.calendar.entity.WorkingDayConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkingDayConfigRepository extends JpaRepository<WorkingDayConfig, UUID> {

    @Query("SELECT c FROM WorkingDayConfig c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<WorkingDayConfig> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT c FROM WorkingDayConfig c WHERE c.academicYear.id = :academicYearId AND c.deletedAt IS NULL")
    Optional<WorkingDayConfig> findByAcademicYearIdAndNotDeleted(@Param("academicYearId") UUID academicYearId);
}
