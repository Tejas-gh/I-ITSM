package com.iitsm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iitsm.application.service.IncidentPermissionPolicy;
import com.iitsm.application.service.TechnicianRecommendationService;
import com.iitsm.domain.assignment.AssignmentStrategy;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.UserRepository;

/**
 * Spring configuration wiring the smart technician recommendation service.
 */
@Configuration
public class TechnicianRecommendationConfig {

    @Bean
    public TechnicianRecommendationService technicianRecommendationService(IncidentRepository incidents,
                                                                           UserRepository users,
                                                                           AssignmentStrategy assignmentStrategy,
                                                                           IncidentPermissionPolicy permissions) {
        return new TechnicianRecommendationService(incidents, users, assignmentStrategy, permissions);
    }
}
