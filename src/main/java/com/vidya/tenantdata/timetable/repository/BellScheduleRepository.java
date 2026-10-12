package com.vidya.tenantdata.timetable.repository;

import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BellScheduleRepository extends JpaRepository<BellSchedule, UUID> {

    @Query("SELECT b FROM BellSchedule b WHERE b.id = :id AND b.deletedAt IS NULL")
    Optional<BellSchedule> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT b FROM BellSchedule b WHERE b.scheduleCode = :scheduleCode AND b.deletedAt IS NULL")
    Optional<BellSchedule> findByScheduleCodeAndNotDeleted(@Param("scheduleCode") String scheduleCode);

    boolean existsByScheduleCode(String scheduleCode);

    @Query("SELECT COUNT(b) > 0 FROM BellSchedule b " +
           "WHERE LOWER(b.name) = LOWER(:name) " +
           "AND b.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR b.id <> :excludeId)")
    boolean existsByNameIgnoreCaseAndNotDeleted(@Param("name") String name, @Param("excludeId") UUID excludeId);

    @Query("SELECT b FROM BellSchedule b " +
           "WHERE b.deletedAt IS NULL " +
           "AND (CAST(:search AS string) IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(b.scheduleCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:status IS NULL OR b.status = :status)")
    Page<BellSchedule> searchSchedules(
            @Param("search") String search,
            @Param("status") BellScheduleStatus status,
            Pageable pageable
    );

    @Query("SELECT b FROM BellSchedule b WHERE b.isDefault = true AND b.status = 'ACTIVE' AND b.deletedAt IS NULL")
    Optional<BellSchedule> findDefaultSchedule();

    @Query("SELECT b FROM BellSchedule b WHERE b.status = 'ACTIVE' AND b.deletedAt IS NULL ORDER BY b.name ASC")
    List<BellSchedule> findAllActive();
}
