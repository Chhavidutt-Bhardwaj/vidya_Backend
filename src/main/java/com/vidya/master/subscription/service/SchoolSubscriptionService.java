package com.vidya.master.subscription.service;

import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscribeSchoolRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service managing school subscriptions and lifecycle transitions.
 */
public interface SchoolSubscriptionService {

    SchoolSubscriptionResponse subscribeSchool(SubscribeSchoolRequest request);

    SchoolSubscriptionResponse getActiveSubscriptionForSchool(UUID schoolId);

    Page<SchoolSubscriptionResponse> getSubscriptionHistory(UUID schoolId, Pageable pageable);

    SchoolSubscriptionResponse cancelSubscription(UUID subscriptionId);

    SchoolSubscriptionResponse activateSubscription(UUID subscriptionId);
}
