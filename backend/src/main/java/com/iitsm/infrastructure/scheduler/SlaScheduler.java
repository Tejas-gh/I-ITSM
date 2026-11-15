package com.iitsm.infrastructure.scheduler;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.iitsm.application.service.SlaMonitorResult;
import com.iitsm.application.service.SlaMonitorService;

/**
 * Background scheduler that periodically triggers SLA monitoring to evaluate open incidents,
 * detect warnings and breaches, and record alerts.
 *
 * <p>Enabled only when {@code iitsm.sla.monitor.enabled=true}. In tests and environments where
 * this property is absent or false, this scheduler bean is not registered, leaving the baseline
 * unaffected.</p>
 */
@Component
@ConditionalOnProperty(name = "iitsm.sla.monitor.enabled", havingValue = "true", matchIfMissing = false)
public class SlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

    private final SlaMonitorService monitorService;

    public SlaScheduler(SlaMonitorService monitorService) {
        this.monitorService = Objects.requireNonNull(monitorService, "monitorService must not be null");
    }

    @Scheduled(fixedDelayString = "${iitsm.sla.monitor.interval-ms:30000}")
    public void scheduleSlaMonitor() {
        log.debug("Starting scheduled SLA monitor tick");
        try {
            SlaMonitorResult result = monitorService.runMonitor();
            log.debug("SLA monitor tick finished: scanned={}, warnings={}, violations={}, errors={}",
                    result.scannedCount(), result.warningsRaised(), result.violationsRaised(), result.errorCount());
        } catch (Exception e) {
            log.error("Unexpected error during scheduled SLA monitor execution", e);
        }
    }
}
