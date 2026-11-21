package com.vidya.tenantdata.engagement.scheduler;

import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.engagement.notification.NotificationOutboxService;
import com.vidya.tenantdata.engagement.service.DailyReportGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Runs the parent-engagement jobs for every operational school, one tenant at a time, with the tenant context
 * set and always cleared. Safe on several servers: the outbox locks rows (SKIP LOCKED) and a day's report run is
 * claimed through a unique row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidya.engagement.scheduler.enabled", havingValue = "true")
public class EngagementScheduler {

    private static final Set<TenantStatus> OPERATIONAL = EnumSet.of(TenantStatus.ACTIVE, TenantStatus.PROVISIONED);

    private final TenantRepository tenantRepository;
    private final NotificationOutboxService outbox;
    private final DailyReportGenerator generator;

    @Value("${vidya.engagement.dispatch-batch-size:200}")
    private int batchSize;

    /** Delivers due outbox messages. A school with a backlog gets up to 10 batches per round. */
    @Scheduled(fixedDelayString = "${vidya.engagement.dispatch-interval:PT1M}", initialDelayString = "${vidya.engagement.initial-delay:PT30S}")
    public void dispatch() {
        forEachTenant("dispatch", code -> {
            for (int i = 0; i < 10 && outbox.dispatchDue(batchSize) >= batchSize; i++) {
                log.debug("Tenant [{}] has more due messages", code);
            }
        });
    }

    /** Sends today's daily report in schools whose send time has passed and that have not sent it yet. */
    @Scheduled(cron = "${vidya.engagement.daily-report-cron:0 */5 * * * *}", zone = "${vidya.school.timezone:Asia/Kolkata}")
    public void dailyReports() {
        forEachTenant("daily report", code -> {
            var run = generator.runIfDue();
            if (run != null) {
                log.info("Tenant [{}] daily report {}: {} ({} messages)", code, run.reportDate(), run.status(), run.messages());
            }
        });
    }

    void forEachTenant(String job, Consumer<String> work) {
        List<String> codes = tenantRepository.findAll().stream().filter(t -> OPERATIONAL.contains(t.getStatus())).map(Tenant::getTenantCode).toList();
        for (String code : codes) {
            try {
                TenantContext.setTenantCode(code);
                work.accept(code);
            } catch (RuntimeException e) {
                log.error("Engagement {} job failed for tenant [{}]: {}", job, code, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
