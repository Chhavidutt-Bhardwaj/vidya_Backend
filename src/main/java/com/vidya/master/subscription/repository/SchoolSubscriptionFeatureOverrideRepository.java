package com.vidya.master.subscription.repository;

import com.vidya.master.subscription.entity.SchoolSubscriptionFeatureOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolSubscriptionFeatureOverrideRepository extends JpaRepository<SchoolSubscriptionFeatureOverride, UUID> {

    List<SchoolSubscriptionFeatureOverride> findBySchoolId(UUID schoolId);

    Optional<SchoolSubscriptionFeatureOverride> findBySchoolIdAndFeatureCode(UUID schoolId, String featureCode);

    void deleteBySchoolIdAndFeatureCode(UUID schoolId, String featureCode);
}
