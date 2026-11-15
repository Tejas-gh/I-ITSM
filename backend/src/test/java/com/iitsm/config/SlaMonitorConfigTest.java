package com.iitsm.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.iitsm.application.service.SlaIncidentProcessor;
import com.iitsm.application.service.SlaMonitorService;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaAlertRepository;

class SlaMonitorConfigTest {

    @Test
    void testBeanWiring() {
        SlaMonitorConfig config = new SlaMonitorConfig();

        IncidentRepository incidents = new InMemoryIncidentRepository();
        SlaAlertRepository alerts = config.slaAlertRepository();
        assertNotNull(alerts);

        SlaEvaluator evaluator = new SlaEvaluator();
        DomainEventPublisher events = new InMemoryDomainEventPublisher();
        Clock clock = Clock.systemUTC();

        SlaIncidentProcessor processor = config.slaIncidentProcessor(alerts, evaluator, events);
        assertNotNull(processor);

        SlaMonitorService monitorService = config.slaMonitorService(incidents, alerts, processor, clock);
        assertNotNull(monitorService);
    }
}
