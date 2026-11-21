package com.vidya.tenantdata.assessment.online;

import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.tenant.context.TenantContext;
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
import java.util.UUID;

/**
 * Submits online-exam attempts whose time ran out (students who closed the browser). Attempts are also submitted lazily
 * whenever the student or a teacher touches them, so this job only keeps results timely. Off with
 * {@code vidya.assessment.scheduler.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidya.assessment.scheduler.enabled", havingValue = "true")
public class AssessmentScheduler {

    private static final Set<TenantStatus> OPERATIONAL = EnumSet.of(TenantStatus.ACTIVE, TenantStatus.PROVISIONED);

    private final TenantRepository tenantRepository;
    private final OnlineExamService service;

    @Scheduled(fixedDelayString = "${vidya.assessment.auto-submit-interval:PT1M}", initialDelayString = "PT45S")
    public void autoSubmit() {
        List<String> codes = tenantRepository.findAll().stream().filter(t -> OPERATIONAL.contains(t.getStatus())).map(Tenant::getTenantCode).toList();
        for (String code : codes) {
            try {
                TenantContext.setTenantCode(code);
                int done = 0;
                for (UUID id : service.expiredAttempts(500)) {
                    try {
                        if (service.autoSubmit(id)) {
                            done++;
                        }
                    } catch (RuntimeException e) {
                        log.warn("Tenant [{}] could not auto-submit attempt {}: {}", code, id, e.getMessage());
                    }
                }
                if (done > 0) {
                    log.info("Tenant [{}] auto-submitted {} online exam attempt(s)", code, done);
                }
            } catch (RuntimeException e) {
                log.error("Online exam auto-submit failed for tenant [{}]: {}", code, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "vidya.assessment.scheduler.enabled", havingValue = "true")
    static class SchedulingConfig {
    }
}
