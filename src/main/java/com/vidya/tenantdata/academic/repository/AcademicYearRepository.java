package com.vidya.tenantdata.academic.repository;

import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    Optional<AcademicYear> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT y FROM AcademicYear y WHERE y.id = :id AND y.deletedAt IS NULL")
    Optional<AcademicYear> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT y FROM AcademicYear y WHERE y.code = :code AND y.deletedAt IS NULL")
    Optional<AcademicYear> findByCodeAndNotDeleted(@Param("code") String code);

    @Query("SELECT y FROM AcademicYear y WHERE y.isCurrent = true AND y.deletedAt IS NULL")
    Optional<AcademicYear> findCurrentYear();

    @Query("SELECT y FROM AcademicYear y WHERE y.deletedAt IS NULL " +
           "AND (:status IS NULL OR y.status = :status) " +
           "AND (:isCurrent IS NULL OR y.isCurrent = :isCurrent)")
    Page<AcademicYear> searchAcademicYears(
            @Param("status") AcademicYearStatus status,
            @Param("isCurrent") Boolean isCurrent,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE AcademicYear y SET y.isCurrent = false WHERE y.isCurrent = true AND y.id <> :yearId")
    void unsetCurrentForOtherYears(@Param("yearId") UUID yearId);

    @Modifying
    @Query("UPDATE AcademicYear y SET y.isCurrent = false WHERE y.isCurrent = true")
    void unsetAllCurrent();
}
