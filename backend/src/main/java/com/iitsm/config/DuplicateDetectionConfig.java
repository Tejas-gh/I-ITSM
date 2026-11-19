package com.iitsm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iitsm.application.service.DuplicateDetectionService;
import com.iitsm.domain.repository.IncidentRepository;

/**
 * Spring configuration wiring the advisory duplicate detection service.
 */
@Configuration
public class DuplicateDetectionConfig {

    @Bean
    public DuplicateDetectionService duplicateDetectionService(IncidentRepository incidentRepository) {
        return new DuplicateDetectionService(incidentRepository);
    }
}
