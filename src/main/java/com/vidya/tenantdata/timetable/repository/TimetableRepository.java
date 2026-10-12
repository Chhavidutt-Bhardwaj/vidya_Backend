package com.vidya.tenantdata.timetable.repository;

import com.vidya.tenantdata.timetable.entity.Timetable;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
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
public interface TimetableRepository extends JpaRepository<Timetable, UUID> {

    @Query("SELECT t FROM Timetable t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Timetable> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT t FROM Timetable t WHERE t.timetableCode = :timetableCode AND t.deletedAt IS NULL")
    Optional<Timetable> findByTimetableCodeAndNotDeleted(@Param("timetableCode") String timetableCode);

    boolean existsByTimetableCode(String timetableCode);

    @Query("SELECT t FROM Timetable t " +
           "WHERE t.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR t.academicYear.id = :academicYearId) " +
           "AND (:bellScheduleId IS NULL OR t.bellSchedule.id = :bellScheduleId) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (CAST(:search AS string) IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.timetableCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Timetable> searchTimetables(
            @Param("academicYearId") UUID academicYearId,
            @Param("bellScheduleId") UUID bellScheduleId,
            @Param("status") TimetableStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT t FROM Timetable t " +
           "WHERE t.academicYear.id = :academicYearId " +
           "AND t.status = 'ACTIVE' " +
           "AND t.deletedAt IS NULL")
    List<Timetable> findActiveByAcademicYear(@Param("academicYearId") UUID academicYearId);

    @Query("SELECT COUNT(t) > 0 FROM Timetable t " +
           "WHERE t.bellSchedule.id = :scheduleId " +
           "AND t.deletedAt IS NULL " +
           "AND t.status <> com.vidya.tenantdata.timetable.enums.TimetableStatus.ARCHIVED")
    boolean isBellScheduleUsedInActiveTimetables(@Param("scheduleId") UUID scheduleId);

    @Query("SELECT COUNT(t) > 0 FROM Timetable t " +
           "WHERE LOWER(t.name) = LOWER(:name) " +
           "AND t.academicYear.id = :academicYearId " +
           "AND (:id IS NULL OR t.id <> :id) " +
           "AND t.deletedAt IS NULL")
    boolean existsByNameIgnoreCaseAndAcademicYearIdAndNotDeleted(
            @Param("name") String name,
            @Param("academicYearId") UUID academicYearId,
            @Param("id") UUID id
    );
}
