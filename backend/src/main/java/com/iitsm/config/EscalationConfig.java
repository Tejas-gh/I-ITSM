package com.iitsm.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iitsm.api.EscalationController;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.EscalationService;
import com.iitsm.domain.escalation.EscalationRepository;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryEscalationRepository;

/**
 * Spring configuration wiring the escalation repository, service, and controller.
 *
 * <p>Provides an in-memory {@link EscalationRepository} fallback; Developer A's
 * primary {@code JpaEscalationRepository} (A7) will supersede it at runtime.</p>
 */
@Configuration
public class EscalationConfig {

    @Bean
    @ConditionalOnMissingBean(EscalationRepository.class)
    public EscalationRepository escalationRepository() {
        return new InMemoryEscalationRepository();
    }

    @Bean
    public EscalationService escalationService(IncidentRepository incidentRepository,
                                               EscalationRepository escalationRepository,
                                               DomainEventPublisher eventPublisher,
                                               Clock clock) {
        return new EscalationService(incidentRepository, escalationRepository, eventPublisher, clock);
    }

    @Bean
    public EscalationController escalationController(EscalationService escalationService,
                                                     UserRepository userRepository,
                                                     AdminPermissionPolicy adminPermissionPolicy) {
        return new EscalationController(escalationService, userRepository, adminPermissionPolicy);
    }
}
