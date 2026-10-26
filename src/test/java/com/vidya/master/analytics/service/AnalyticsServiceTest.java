package com.vidya.master.analytics.service;

import com.vidya.master.analytics.dto.OnboardingFunnelResponse;
import com.vidya.master.analytics.dto.PlatformKpiResponse;
import com.vidya.master.analytics.dto.RevenueTrendResponse;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.repository.CommunityRepository;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.entity.PaymentTransaction;
import com.vidya.master.payment.repository.PaymentTransactionRepository;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.support.entity.QueryStatus;
import com.vidya.master.support.repository.SchoolSupportQueryRepository;
import com.vidya.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService — Platform Executive KPIs, Revenue Trends & Funnel Tests")
class AnalyticsServiceTest {

    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private SchoolSubscriptionRepository schoolSubscriptionRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private SchoolOnboardingRepository onboardingRepository;
    @Mock
    private SchoolSupportQueryRepository supportQueryRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    @Test
    @DisplayName("Should aggregate real-time platform KPIs accurately")
    void shouldGetPlatformKpis() {
        when(schoolRepository.count()).thenReturn(15L);
        when(schoolRepository.countByStatus(SchoolStatus.ACTIVE)).thenReturn(10L);
        when(schoolRepository.countByStatus(SchoolStatus.DRAFT)).thenReturn(5L);

        when(onboardingRepository.countByStatusIn(any())).thenReturn(3L);
        when(communityRepository.count()).thenReturn(4L);
        when(communityRepository.countByStatus(CommunityStatus.ACTIVE)).thenReturn(4L);
        when(platformUserRepository.count()).thenReturn(25L);
        when(schoolSubscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)).thenReturn(8L);

        when(paymentTransactionRepository.count()).thenReturn(40L);
        when(paymentTransactionRepository.sumAmountByStatus(PaymentStatus.SUCCESS)).thenReturn(new BigDecimal("150000.00"));

        SchoolSubscription sub1 = mock(SchoolSubscription.class);
        when(sub1.getFinalAmount()).thenReturn(new BigDecimal("12000.00"));
        when(sub1.getBillingCycle()).thenReturn(BillingCycle.ANNUAL); // 12000 / 12 = 1000/mo

        SchoolSubscription sub2 = mock(SchoolSubscription.class);
        when(sub2.getFinalAmount()).thenReturn(new BigDecimal("2000.00"));
        when(sub2.getBillingCycle()).thenReturn(BillingCycle.MONTHLY); // 2000/mo

        when(schoolSubscriptionRepository.findAllActiveSubscriptions()).thenReturn(List.of(sub1, sub2));

        when(supportQueryRepository.countByStatusIn(List.of(QueryStatus.OPEN, QueryStatus.IN_PROGRESS))).thenReturn(2L);
        when(supportQueryRepository.countByStatusIn(List.of(QueryStatus.ESCALATED_TO_HUB, QueryStatus.ESCALATED_TO_PLATFORM))).thenReturn(1L);
        when(supportQueryRepository.countByStatusIn(List.of(QueryStatus.RESOLVED, QueryStatus.CLOSED))).thenReturn(12L);

        PlatformKpiResponse kpis = analyticsService.getPlatformKpis();

        assertThat(kpis).isNotNull();
        assertThat(kpis.totalSchools()).isEqualTo(15L);
        assertThat(kpis.activeSchools()).isEqualTo(10L);
        assertThat(kpis.pendingOnboardings()).isEqualTo(3L);
        assertThat(kpis.totalCommunities()).isEqualTo(4L);
        assertThat(kpis.activeCommunities()).isEqualTo(4L);
        assertThat(kpis.totalUsers()).isEqualTo(25L);
        assertThat(kpis.activeSubscriptions()).isEqualTo(8L);
        assertThat(kpis.totalTransactions()).isEqualTo(40L);
        assertThat(kpis.totalSettledRevenue()).isEqualByComparingTo("150000.00");
        // MRR = 1000 + 2000 = 3000
        assertThat(kpis.monthlyRecurringRevenue()).isEqualByComparingTo("3000.00");
        // ARR = 3000 * 12 = 36000
        assertThat(kpis.annualRecurringRevenue()).isEqualByComparingTo("36000.00");
        assertThat(kpis.openSupportQueries()).isEqualTo(2L);
        assertThat(kpis.escalatedSupportQueries()).isEqualTo(1L);
        assertThat(kpis.resolvedSupportQueries()).isEqualTo(12L);
    }

    @Test
    @DisplayName("Should generate continuous monthly revenue trend time-series")
    void shouldGetRevenueTrends() {
        PaymentTransaction tx = mock(PaymentTransaction.class);
        when(tx.getCreatedAt()).thenReturn(Instant.now());
        when(tx.getAmount()).thenReturn(new BigDecimal("25000.00"));

        when(paymentTransactionRepository.findByStatus(PaymentStatus.SUCCESS)).thenReturn(List.of(tx));

        SchoolSubscription sub = mock(SchoolSubscription.class);
        when(sub.getCreatedAt()).thenReturn(Instant.now());
        when(schoolSubscriptionRepository.findAll()).thenReturn(List.of(sub));

        List<RevenueTrendResponse> trends = analyticsService.getRevenueTrends(6);

        assertThat(trends).hasSize(6);
        RevenueTrendResponse latest = trends.get(trends.size() - 1);
        assertThat(latest.revenue()).isEqualByComparingTo("25000.00");
        assertThat(latest.transactionCount()).isEqualTo(1L);
        assertThat(latest.newSubscriptions()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should evaluate onboarding funnel stages and conversion percentage")
    void shouldGetOnboardingFunnel() {
        SchoolOnboarding o1 = mock(SchoolOnboarding.class);
        when(o1.getStatus()).thenReturn(OnboardingStatus.SUBMITTED);
        when(o1.getSource()).thenReturn(OnboardingSource.WEBSITE);

        SchoolOnboarding o2 = mock(SchoolOnboarding.class);
        when(o2.getStatus()).thenReturn(OnboardingStatus.ACTIVE);
        when(o2.getSource()).thenReturn(OnboardingSource.COMMUNITY);

        when(onboardingRepository.findAll()).thenReturn(List.of(o1, o2));

        OnboardingFunnelResponse funnel = analyticsService.getOnboardingFunnel();

        assertThat(funnel).isNotNull();
        assertThat(funnel.totalApplications()).isEqualTo(2L);
        assertThat(funnel.activeSchools()).isEqualTo(1L);
        assertThat(funnel.conversionRate()).isEqualTo(50.0);
        assertThat(funnel.stageCounts()).containsEntry("SUBMITTED", 1L);
        assertThat(funnel.stageCounts()).containsEntry("ACTIVE", 1L);
        assertThat(funnel.channelDistribution()).containsEntry("WEBSITE", 1L);
        assertThat(funnel.channelDistribution()).containsEntry("COMMUNITY", 1L);
    }
}
