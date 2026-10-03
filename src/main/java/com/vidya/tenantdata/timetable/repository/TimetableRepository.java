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
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(t.timetableCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Timetable> searchTimetables(
            @Param("academicYearId") UUID academicYearId,
            @Param("status") TimetableStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT t FROM Timetable t " +
           "WHERE t.academicYear.id = :academicYearId " +
           "AND t.status = 'ACTIVE' " +
           "AND t.deletedAt IS NULL")
    List<Timetable> findActiveByAcademicYear(@Param("academicYearId") UUID academicYearId);
}
