package com.vidya.tenantdata.examination.repository;

import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradingScaleTierRepository extends JpaRepository<GradingScaleTier, UUID> {

    List<GradingScaleTier> findByGradingScaleIdOrderByMinPercentageDesc(UUID gradingScaleId);

    @Query("SELECT t FROM GradingScaleTier t WHERE t.gradingScale.id = :scaleId " +
           "AND :percentage >= t.minPercentage AND :percentage <= t.maxPercentage")
    Optional<GradingScaleTier> findMatchingTier(@Param("scaleId") UUID scaleId,
                                                @Param("percentage") BigDecimal percentage);
}
