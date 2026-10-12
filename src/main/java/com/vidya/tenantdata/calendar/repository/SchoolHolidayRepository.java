package com.vidya.tenantdata.calendar.repository;

import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolHolidayRepository extends JpaRepository<SchoolHoliday, UUID> {

    @Query("SELECT h FROM SchoolHoliday h WHERE h.id = :id AND h.deletedAt IS NULL")
    Optional<SchoolHoliday> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT h FROM SchoolHoliday h WHERE h.holidayCode = :code AND h.deletedAt IS NULL")
    Optional<SchoolHoliday> findByHolidayCodeAndNotDeleted(@Param("code") String code);

    @Query("SELECT h FROM SchoolHoliday h WHERE h.academicYear.id = :academicYearId " +
           "AND h.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR h.id <> :excludeId) " +
           "AND h.startDate <= :endDate AND h.endDate >= :startDate")
    List<SchoolHoliday> findOverlappingHolidays(
            @Param("academicYearId") UUID academicYearId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT h FROM SchoolHoliday h WHERE h.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR h.academicYear.id = :academicYearId) " +
           "AND (:type IS NULL OR h.holidayType = :type) " +
           "AND (cast(:dateFrom as date) IS NULL OR h.endDate >= :dateFrom) " +
           "AND (cast(:dateTo as date) IS NULL OR h.startDate <= :dateTo)")
    Page<SchoolHoliday> searchHolidays(
            @Param("academicYearId") UUID academicYearId,
            @Param("type") HolidayType type,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable
    );

    @Query("SELECT h FROM SchoolHoliday h WHERE h.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR h.academicYear.id = :academicYearId) " +
           "AND :date >= h.startDate AND :date <= h.endDate")
    List<SchoolHoliday> findHolidaysForDate(
            @Param("academicYearId") UUID academicYearId,
            @Param("date") LocalDate date
    );

    @Query("SELECT h FROM SchoolHoliday h WHERE h.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR h.academicYear.id = :academicYearId) " +
           "AND h.startDate <= :to AND h.endDate >= :from " +
           "ORDER BY h.startDate ASC")
    List<SchoolHoliday> findActiveHolidaysInRange(
            @Param("academicYearId") UUID academicYearId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
