package com.iitsm.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.iitsm.application.service.SlaMonitorResult;
import com.iitsm.application.service.SlaMonitorService;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaAlertRepository;

class SlaSchedulerTest {

    @Test
    void scheduleSlaMonitorInvokesMonitorService() {
        AtomicInteger callCount = new AtomicInteger();
        SlaMonitorService stubMonitor = new SlaMonitorService(
                new InMemoryIncidentRepository(),
                new InMemorySlaAlertRepository(),
                new SlaEvaluator(),
                new InMemoryDomainEventPublisher(),
                Clock.systemUTC()
        ) {
            @Override
            public SlaMonitorResult runMonitor() {
                callCount.incrementAndGet();
                return new SlaMonitorResult(5, 1, 0, 0);
            }
        };

        SlaScheduler scheduler = new SlaScheduler(stubMonitor);
        scheduler.scheduleSlaMonitor();

        assertEquals(1, callCount.get());
    }

    @Test
    void scheduleSlaMonitorCatchesExceptionWithoutRethrowing() {
        SlaMonitorService failingMonitor = new SlaMonitorService(
                new InMemoryIncidentRepository(),
                new InMemorySlaAlertRepository(),
                new SlaEvaluator(),
                new InMemoryDomainEventPublisher(),
                Clock.systemUTC()
        ) {
            @Override
            public SlaMonitorResult runMonitor() {
                throw new RuntimeException("Simulated unexpected failure");
            }
        };

        SlaScheduler scheduler = new SlaScheduler(failingMonitor);

        // Must not throw exception
        assertDoesNotThrow(() -> scheduler.scheduleSlaMonitor());
    }
}
