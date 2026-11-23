package com.vidya.tenantdata.fee.online.service;

import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.RunView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Daily auto-debit job (plan, invitations, pre-debit notices, submissions) and the hourly mandate status poll, for every operational
 * school with the tenant context set. Safe on several servers (unique debit attempts, row locks, deduplicated messages).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidya.fees.online.scheduler.enabled", havingValue = "true")
public class OnlineFeeScheduler {

    private static final Set<TenantStatus> OPERATIONAL = EnumSet.of(TenantStatus.ACTIVE, TenantStatus.PROVISIONED);

    private final TenantRepository tenantRepository;
    private final AutoDebitService autoDebit;
    private final MandateService mandates;

    @Scheduled(cron = "${vidya.fees.online.autodebit-cron:0 30 7 * * *}", zone = "${vidya.school.timezone:Asia/Kolkata}")
    public void autoDebit() {
        forEachTenant("auto-debit", code -> {
            RunView r = autoDebit.run("SCHEDULED", null);
            if (r != null) {
                log.info("Tenant [{}] auto-debit: planned {}, invites {}, notices {}, submitted {}, skipped {}, errors {} ({})", code, r.planned(),
                        r.invites(), r.notices(), r.submitted(), r.skipped(), r.errors(), r.status());
            }
        });
    }

    @Scheduled(cron = "${vidya.fees.online.mandate-sync-cron:0 20 * * * *}", zone = "${vidya.school.timezone:Asia/Kolkata}")
    public void syncMandates() {
        forEachTenant("mandate sync", code -> {
            int n = mandates.syncPending();
            if (n > 0) {
                log.info("Tenant [{}] mandates synced: {}", code, n);
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
                log.error("Online fee {} job failed for tenant [{}]: {}", job, code, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Turns scheduling on for these jobs (off in tests and on API-only workers). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "vidya.fees.online.scheduler.enabled", havingValue = "true")
    static class SchedulingConfig {
    }
}
