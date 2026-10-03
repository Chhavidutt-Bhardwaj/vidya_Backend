package com.vidya.master.subscription.repository;

import com.vidya.master.subscription.entity.FeatureCategory;
import com.vidya.master.subscription.entity.FeatureStatus;
import com.vidya.master.subscription.entity.SubscriptionFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link SubscriptionFeature} entity.
 */
@Repository
public interface SubscriptionFeatureRepository extends JpaRepository<SubscriptionFeature, UUID> {

    Optional<SubscriptionFeature> findByFeatureCode(String featureCode);

    List<SubscriptionFeature> findByStatus(FeatureStatus status);

    List<SubscriptionFeature> findByCategory(FeatureCategory category);
}
