package com.vidya.master.subscription.repository;

import com.vidya.master.subscription.entity.SubscriptionPlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link SubscriptionPlanFeature} entity.
 */
@Repository
public interface SubscriptionPlanFeatureRepository extends JpaRepository<SubscriptionPlanFeature, UUID> {

    List<SubscriptionPlanFeature> findByPlanId(UUID planId);
}
