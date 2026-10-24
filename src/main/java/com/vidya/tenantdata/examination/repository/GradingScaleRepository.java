package com.vidya.tenantdata.examination.repository;

import com.vidya.tenantdata.examination.entity.GradingScale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {

    @Query("SELECT g FROM GradingScale g WHERE g.id = :id AND g.deletedAt IS NULL")
    Optional<GradingScale> findActiveById(@Param("id") UUID id);

    @Query("SELECT g FROM GradingScale g WHERE g.scaleCode = :code AND g.deletedAt IS NULL")
    Optional<GradingScale> findActiveByCode(@Param("code") String code);

    @Query("SELECT g FROM GradingScale g WHERE g.isDefault = true AND g.deletedAt IS NULL")
    Optional<GradingScale> findDefaultScale();

    @Query("SELECT g FROM GradingScale g WHERE g.deletedAt IS NULL")
    Page<GradingScale> findAllActive(Pageable pageable);

    @Query("SELECT COUNT(g) > 0 FROM GradingScale g WHERE LOWER(g.name) = LOWER(:name) AND g.deletedAt IS NULL")
    boolean existsByNameIgnoreCase(@Param("name") String name);
}
