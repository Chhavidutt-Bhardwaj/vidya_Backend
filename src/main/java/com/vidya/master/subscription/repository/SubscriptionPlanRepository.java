package com.vidya.master.subscription.repository;

import com.vidya.master.subscription.entity.PlanStatus;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link SubscriptionPlan} entity.
 */
@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByPlanCodeAndVersion(String planCode, Integer version);

    Optional<SubscriptionPlan> findFirstByPlanCodeAndStatusOrderByVersionDesc(String planCode, PlanStatus status);

    @Query("SELECT MAX(p.version) FROM SubscriptionPlan p WHERE p.planCode = :planCode")
    Integer findMaxVersionByPlanCode(@Param("planCode") String planCode);

    List<SubscriptionPlan> findByStatus(PlanStatus status);

    Page<SubscriptionPlan> findByStatus(PlanStatus status, Pageable pageable);

    @Query("SELECT p FROM SubscriptionPlan p WHERE " +
            "p.status = 'ACTIVE' AND " +
            "p.effectiveFrom <= CURRENT_DATE AND " +
            "(p.effectiveTo IS NULL OR p.effectiveTo >= CURRENT_DATE)")
    List<SubscriptionPlan> findActivePublicPlans();

    @Query("SELECT p FROM SubscriptionPlan p WHERE " +
            "p.planCode = :planCode AND " +
            "p.status = 'ACTIVE' AND " +
            "p.effectiveFrom <= CURRENT_DATE AND " +
            "(p.effectiveTo IS NULL OR p.effectiveTo >= CURRENT_DATE) " +
            "ORDER BY p.version DESC")
    List<SubscriptionPlan> findActiveByPlanCode(@Param("planCode") String planCode);
}
