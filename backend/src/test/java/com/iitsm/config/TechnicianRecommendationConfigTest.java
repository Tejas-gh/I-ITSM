package com.iitsm.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.iitsm.application.service.IncidentPermissionPolicy;
import com.iitsm.application.service.TechnicianRecommendationService;
import com.iitsm.domain.assignment.LoadBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SkillBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SwitchableAssignmentStrategy;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;

class TechnicianRecommendationConfigTest {

    @Test
    void testBeanWiring() {
        TechnicianRecommendationConfig config = new TechnicianRecommendationConfig();

        IncidentRepository incidents = new InMemoryIncidentRepository();
        UserRepository users = new InMemoryUserRepository();
        SkillBasedAssignmentStrategy skillStrategy = new SkillBasedAssignmentStrategy();
        SwitchableAssignmentStrategy strategy = new SwitchableAssignmentStrategy(
                List.of(skillStrategy, new LoadBasedAssignmentStrategy()), skillStrategy.name());
        IncidentPermissionPolicy permissions = new IncidentPermissionPolicy();

        TechnicianRecommendationService service = config.technicianRecommendationService(
                incidents, users, strategy, permissions);

        assertNotNull(service);
    }
}
