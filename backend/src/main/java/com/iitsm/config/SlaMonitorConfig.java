package com.iitsm.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iitsm.application.service.SlaIncidentProcessor;
import com.iitsm.application.service.SlaMonitorService;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaAlertRepository;

/**
 * Spring configuration wiring the SLA monitor service and incident processor.
 *
 * <p>Provides an in-memory {@link SlaAlertRepository} fallback until Developer A registers the
 * JPA repository in Gate A3.</p>
 */
@Configuration
public class SlaMonitorConfig {

    @Bean
    @ConditionalOnMissingBean(SlaAlertRepository.class)
    public SlaAlertRepository slaAlertRepository() {
        return new InMemorySlaAlertRepository();
    }

    @Bean
    public SlaIncidentProcessor slaIncidentProcessor(SlaAlertRepository slaAlertRepository,
                                                     SlaEvaluator slaEvaluator,
                                                     DomainEventPublisher events) {
        return new SlaIncidentProcessor(slaAlertRepository, slaEvaluator, events);
    }

    @Bean
    public SlaMonitorService slaMonitorService(IncidentRepository incidentRepository,
                                               SlaAlertRepository slaAlertRepository,
                                               SlaIncidentProcessor slaIncidentProcessor,
                                               Clock clock) {
        return new SlaMonitorService(incidentRepository, slaAlertRepository, slaIncidentProcessor, clock);
    }
}
