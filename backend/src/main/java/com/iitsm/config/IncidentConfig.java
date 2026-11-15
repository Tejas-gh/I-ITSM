package com.iitsm.config;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iitsm.application.service.IncidentPermissionPolicy;
import com.iitsm.application.service.IncidentSearchService;
import com.iitsm.application.service.IncidentService;
import com.iitsm.domain.assignment.AssignmentStrategy;
import com.iitsm.domain.assignment.LoadBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SkillBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SwitchableAssignmentStrategy;
import com.iitsm.domain.classification.CategoryClassifier;
import com.iitsm.domain.classification.KeywordClassifier;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.factory.IncidentIdGenerator;
import com.iitsm.domain.priority.PriorityStrategy;
import com.iitsm.domain.priority.WeightedPriorityStrategy;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.SlaPolicyRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.domain.sla.SlaCalculator;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaPolicyResolver;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;

/**
 * Wires the plain domain/application classes needed by the incident API (A6) as beans.
 * The event publisher has no listeners yet: audit and notification listeners are subscribed in a later gate.
 */
@Configuration
public class IncidentConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PriorityStrategy priorityStrategy() {
        return new WeightedPriorityStrategy();
    }

    @Bean
    IncidentFactory incidentFactory(IncidentIdGenerator ids, PriorityStrategy priorityStrategy) {
        return new IncidentFactory(ids, priorityStrategy);
    }

    @Bean
    SlaPolicyResolver slaPolicyResolver(SlaPolicyRepository policies) {
        return new SlaPolicyResolver(policies);
    }

    @Bean
    SlaCalculator slaCalculator(SlaPolicyResolver resolver) {
        return new SlaCalculator(resolver);
    }

    @Bean
    SlaEvaluator slaEvaluator() {
        return new SlaEvaluator();
    }

    /**
     * The assignment strategy used by {@link IncidentService}: skill-based by default, switchable at runtime to
     * load-based through {@code AssignmentStrategyAdminService} (the selection is held in memory).
     */
    @Bean
    SwitchableAssignmentStrategy assignmentStrategy() {
        SkillBasedAssignmentStrategy skillBased = new SkillBasedAssignmentStrategy();
        return new SwitchableAssignmentStrategy(List.of(skillBased, new LoadBasedAssignmentStrategy()),
                skillBased.name());
    }

    @Bean
    CategoryClassifier categoryClassifier() {
        return new KeywordClassifier();
    }

    @Bean
    IncidentPermissionPolicy incidentPermissionPolicy() {
        return new IncidentPermissionPolicy();
    }

    @Bean
    DomainEventPublisher domainEventPublisher() {
        return new InMemoryDomainEventPublisher();
    }

    @Bean
    IncidentService incidentService(IncidentRepository incidents, UserRepository users, ItServiceRepository services,
                                    CategoryRepository categories, IncidentFactory incidentFactory,
                                    PriorityStrategy priorityStrategy, SlaCalculator slaCalculator,
                                    AssignmentStrategy assignmentStrategy, CategoryClassifier classifier,
                                    IncidentPermissionPolicy permissions, DomainEventPublisher events, Clock clock) {
        return new IncidentService(incidents, users, services, categories, incidentFactory, priorityStrategy,
                slaCalculator, assignmentStrategy, classifier, permissions, events, clock);
    }

    @Bean
    IncidentSearchService incidentSearchService(IncidentRepository incidents, UserRepository users,
                                                SlaEvaluator slaEvaluator, Clock clock) {
        return new IncidentSearchService(incidents, users, slaEvaluator, clock);
    }
}
