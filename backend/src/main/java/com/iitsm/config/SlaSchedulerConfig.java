package com.iitsm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring configuration enabling background task scheduling conditionally.
 *
 * <p>Only activates when {@code iitsm.sla.monitor.enabled=true}, ensuring scheduling is completely
 * disabled in default test suites to keep the test baseline unaffected.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "iitsm.sla.monitor.enabled", havingValue = "true", matchIfMissing = false)
public class SlaSchedulerConfig {
}
