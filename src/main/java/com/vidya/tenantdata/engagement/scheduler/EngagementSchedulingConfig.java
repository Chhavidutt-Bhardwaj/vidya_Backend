package com.vidya.tenantdata.engagement.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs of parent engagement. Off with {@code vidya.engagement.scheduler.enabled=false} (tests, extra workers). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "vidya.engagement.scheduler.enabled", havingValue = "true")
public class EngagementSchedulingConfig {
}
