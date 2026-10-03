package com.vidya.tenantdata.academic.repository;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
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
public interface AcademicClassRepository extends JpaRepository<AcademicClass, UUID> {

    Optional<AcademicClass> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT c FROM AcademicClass c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<AcademicClass> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT c FROM AcademicClass c WHERE c.code = :code AND c.deletedAt IS NULL")
    Optional<AcademicClass> findByCodeAndNotDeleted(@Param("code") String code);

    @Query("SELECT c FROM AcademicClass c WHERE c.deletedAt IS NULL " +
           "AND (:status IS NULL OR c.status = :status)")
    Page<AcademicClass> searchAcademicClasses(
            @Param("status") AcademicClassStatus status,
            Pageable pageable
    );

    List<AcademicClass> findByDeletedAtIsNullOrderBySortOrderAsc();
}
