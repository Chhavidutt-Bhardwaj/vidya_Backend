package com.vidya.master.analytics.service;

import com.vidya.master.analytics.dto.OnboardingFunnelResponse;
import com.vidya.master.analytics.dto.PlatformKpiResponse;
import com.vidya.master.analytics.dto.RevenueTrendResponse;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.repository.CommunityRepository;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.entity.PaymentTransaction;
import com.vidya.master.payment.repository.PaymentTransactionRepository;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.support.entity.QueryStatus;
import com.vidya.master.support.repository.SchoolSupportQueryRepository;
import com.vidya.security.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "masterTransactionManager", readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final SchoolRepository schoolRepository;
    private final CommunityRepository communityRepository;
    private final PlatformUserRepository platformUserRepository;
    private final SchoolSubscriptionRepository schoolSubscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SchoolOnboardingRepository onboardingRepository;
    private final SchoolSupportQueryRepository supportQueryRepository;

    private static final DateTimeFormatter MONTH_NAME_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    @Override
    public PlatformKpiResponse getPlatformKpis() {
        log.info("Computing platform executive KPIs");

        long totalSchools = schoolRepository.count();
        long activeSchools = schoolRepository.countByStatus(SchoolStatus.ACTIVE);
        long draftSchools = schoolRepository.countByStatus(SchoolStatus.DRAFT);

        long pendingOnboardings = onboardingRepository.countByStatusIn(List.of(
                OnboardingStatus.SUBMITTED,
                OnboardingStatus.VERIFICATION_PENDING,
                OnboardingStatus.PLAN_SELECTED,
                OnboardingStatus.PAYMENT_PENDING,
                OnboardingStatus.APPROVAL_PENDING
        ));

        long totalCommunities = communityRepository.count();
        long activeCommunities = communityRepository.countByStatus(CommunityStatus.ACTIVE);
        long totalUsers = platformUserRepository.count();
        long activeSubscriptions = schoolSubscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);

        long totalTransactions = paymentTransactionRepository.count();
        BigDecimal totalSettledRevenue = paymentTransactionRepository.sumAmountByStatus(PaymentStatus.SUCCESS);
        if (totalSettledRevenue == null) {
            totalSettledRevenue = BigDecimal.ZERO;
        }

        // Calculate MRR and ARR from active subscriptions
        List<SchoolSubscription> activeSubs = schoolSubscriptionRepository.findAllActiveSubscriptions();
        BigDecimal mrr = BigDecimal.ZERO;
        for (SchoolSubscription sub : activeSubs) {
            if (sub.getFinalAmount() != null && sub.getBillingCycle() != null) {
                BigDecimal monthlyValue = switch (sub.getBillingCycle()) {
                    case MONTHLY -> sub.getFinalAmount();
                    case QUARTERLY -> sub.getFinalAmount().divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
                    case ANNUAL -> sub.getFinalAmount().divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
                    case ONE_TIME -> BigDecimal.ZERO;
                };
                mrr = mrr.add(monthlyValue);
            }
        }
        BigDecimal arr = mrr.multiply(new BigDecimal("12")).setScale(2, RoundingMode.HALF_UP);

        long openSupportQueries = supportQueryRepository.countByStatusIn(List.of(QueryStatus.OPEN, QueryStatus.IN_PROGRESS));
        long escalatedSupportQueries = supportQueryRepository.countByStatusIn(List.of(QueryStatus.ESCALATED_TO_HUB, QueryStatus.ESCALATED_TO_PLATFORM));
        long resolvedSupportQueries = supportQueryRepository.countByStatusIn(List.of(QueryStatus.RESOLVED, QueryStatus.CLOSED));

        return new PlatformKpiResponse(
                totalSchools,
                activeSchools,
                draftSchools,
                pendingOnboardings,
                totalCommunities,
                activeCommunities,
                totalUsers,
                activeSubscriptions,
                mrr,
                arr,
                totalSettledRevenue,
                totalTransactions,
                openSupportQueries,
                escalatedSupportQueries,
                resolvedSupportQueries
        );
    }

    @Override
    public List<RevenueTrendResponse> getRevenueTrends(int months) {
        int targetMonths = (months <= 0) ? 12 : Math.min(months, 36);
        log.info("Computing revenue trends for past [{}] months", targetMonths);

        YearMonth currentYearMonth = YearMonth.now();
        YearMonth startYearMonth = currentYearMonth.minusMonths(targetMonths - 1);

        // Prepopulate all months in window with zeroes to guarantee contiguous timeline
        Map<YearMonth, BigDecimal> revenueMap = new LinkedHashMap<>();
        Map<YearMonth, Long> txCountMap = new LinkedHashMap<>();
        Map<YearMonth, Long> newSubsMap = new LinkedHashMap<>();

        YearMonth cursor = startYearMonth;
        while (!cursor.isAfter(currentYearMonth)) {
            revenueMap.put(cursor, BigDecimal.ZERO);
            txCountMap.put(cursor, 0L);
            newSubsMap.put(cursor, 0L);
            cursor = cursor.plusMonths(1);
        }

        // Aggregate successful payments
        List<PaymentTransaction> successfulTxs = paymentTransactionRepository.findByStatus(PaymentStatus.SUCCESS);
        for (PaymentTransaction tx : successfulTxs) {
            if (tx.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(tx.getCreatedAt().atZone(ZoneId.systemDefault()));
                if (revenueMap.containsKey(ym)) {
                    revenueMap.put(ym, revenueMap.get(ym).add(tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO));
                    txCountMap.put(ym, txCountMap.get(ym) + 1);
                }
            }
        }

        // Aggregate new subscriptions
        List<SchoolSubscription> allSubs = schoolSubscriptionRepository.findAll();
        for (SchoolSubscription sub : allSubs) {
            if (sub.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(sub.getCreatedAt().atZone(ZoneId.systemDefault()));
                if (newSubsMap.containsKey(ym)) {
                    newSubsMap.put(ym, newSubsMap.get(ym) + 1);
                }
            }
        }

        List<RevenueTrendResponse> result = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal> entry : revenueMap.entrySet()) {
            YearMonth ym = entry.getKey();
            result.add(new RevenueTrendResponse(
                    ym.toString(),
                    ym.format(MONTH_NAME_FORMATTER),
                    entry.getValue().setScale(2, RoundingMode.HALF_UP),
                    txCountMap.getOrDefault(ym, 0L),
                    newSubsMap.getOrDefault(ym, 0L)
            ));
        }

        return result;
    }

    @Override
    public OnboardingFunnelResponse getOnboardingFunnel() {
        log.info("Computing school onboarding pipeline funnel");

        List<SchoolOnboarding> allOnboardings = onboardingRepository.findAll();
        long total = allOnboardings.size();

        Map<String, Long> stageCounts = new LinkedHashMap<>();
        for (OnboardingStatus status : OnboardingStatus.values()) {
            stageCounts.put(status.name(), 0L);
        }

        Map<String, Long> channelDistribution = new LinkedHashMap<>();

        long activeCount = 0;
        for (SchoolOnboarding o : allOnboardings) {
            if (o.getStatus() != null) {
                stageCounts.put(o.getStatus().name(), stageCounts.getOrDefault(o.getStatus().name(), 0L) + 1);
                if (o.getStatus() == OnboardingStatus.ACTIVE || o.getStatus() == OnboardingStatus.PROVISIONED) {
                    activeCount++;
                }
            }
            if (o.getSource() != null) {
                channelDistribution.put(o.getSource().name(), channelDistribution.getOrDefault(o.getSource().name(), 0L) + 1);
            }
        }

        double conversionRate = (total > 0)
                ? BigDecimal.valueOf((double) activeCount / total * 100.0)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return new OnboardingFunnelResponse(
                total,
                activeCount,
                conversionRate,
                stageCounts,
                channelDistribution
        );
    }
}
