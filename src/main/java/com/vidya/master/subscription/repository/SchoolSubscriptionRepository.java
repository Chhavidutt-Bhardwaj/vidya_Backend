package com.vidya.master.subscription.repository;

import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionStatus;
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
 * Spring Data JPA Repository for {@link SchoolSubscription} entity.
 */
@Repository
public interface SchoolSubscriptionRepository extends JpaRepository<SchoolSubscription, UUID> {

    Optional<SchoolSubscription> findBySubscriptionCode(String subscriptionCode);

    @Query("SELECT s FROM SchoolSubscription s WHERE s.schoolId = :schoolId AND s.status IN ('TRIAL', 'ACTIVE') ORDER BY s.createdAt DESC")
    List<SchoolSubscription> findActiveSubscriptionsBySchoolId(@Param("schoolId") UUID schoolId);

    List<SchoolSubscription> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);

    List<SchoolSubscription> findBySchoolId(UUID schoolId);

    Page<SchoolSubscription> findBySchoolId(UUID schoolId, Pageable pageable);

    Page<SchoolSubscription> findByStatus(SubscriptionStatus status, Pageable pageable);

    long countByStatus(SubscriptionStatus status);

    @Query("SELECT s FROM SchoolSubscription s WHERE s.status IN ('TRIAL', 'ACTIVE')")
    List<SchoolSubscription> findAllActiveSubscriptions();
}
