package com.vidya.tenantdata.timetable.repository;

import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BellSchedulePeriodRepository extends JpaRepository<BellSchedulePeriod, UUID> {

    @Query("SELECT p FROM BellSchedulePeriod p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<BellSchedulePeriod> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT p FROM BellSchedulePeriod p WHERE p.periodCode = :periodCode AND p.deletedAt IS NULL")
    Optional<BellSchedulePeriod> findByPeriodCodeAndNotDeleted(@Param("periodCode") String periodCode);

    @Query("SELECT p FROM BellSchedulePeriod p WHERE p.bellSchedule.id = :bellScheduleId AND p.deletedAt IS NULL ORDER BY p.periodSequence ASC")
    List<BellSchedulePeriod> findByBellScheduleIdAndNotDeletedOrderBySequence(@Param("bellScheduleId") UUID bellScheduleId);

    boolean existsByPeriodCode(String periodCode);
}
