package com.iitsm.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.assignment.LoadBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SkillBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SwitchableAssignmentStrategy;
import com.iitsm.domain.exception.IncidentNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;

class TechnicianRecommendationServiceTest {

    private ServiceFixture fixture;
    private SwitchableAssignmentStrategy switchableStrategy;
    private SkillBasedAssignmentStrategy skillStrategy;
    private LoadBasedAssignmentStrategy loadStrategy;
    private TechnicianRecommendationService recommendationService;

    private Incident incident;
    private Category networkCat;
    private Technician techHighSkill;
    private Technician techMedSkill;
    private Technician techLowSkill;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();

        networkCat = TestData.category("NETWORK");
        fixture.categories.save(networkCat);

        skillStrategy = new SkillBasedAssignmentStrategy();
        loadStrategy = new LoadBasedAssignmentStrategy();
        switchableStrategy = new SwitchableAssignmentStrategy(List.of(skillStrategy, loadStrategy), skillStrategy.name());

        // Deactivate default seeded technicians so our test technicians are the only candidates
        fixture.technician.deactivate();
        fixture.technician2.deactivate();
        fixture.users.save(fixture.technician);
        fixture.users.save(fixture.technician2);

        recommendationService = new TechnicianRecommendationService(
                fixture.incidents,
                fixture.users,
                switchableStrategy,
                fixture.permissions
        );

        // Tech 1: Skill level 5, 10 years exp, max 5 incidents
        techHighSkill = new Technician("TECH_HIGH", "Alice High", "alice@corp.com", Role.TECHNICIAN,
                Map.of("NETWORK", 5), 10, 5);
        // Tech 2: Skill level 3, 5 years exp, max 5 incidents
        techMedSkill = new Technician("TECH_MED", "Bob Med", "bob@corp.com", Role.TECHNICIAN,
                Map.of("NETWORK", 3), 5, 5);
        // Tech 3: Skill level 1, 2 years exp, max 5 incidents
        techLowSkill = new Technician("TECH_LOW", "Charlie Low", "charlie@corp.com", Role.TECHNICIAN,
                Map.of("NETWORK", 1), 2, 5);

        fixture.users.save(techHighSkill);
        fixture.users.save(techMedSkill);
        fixture.users.save(techLowSkill);

        // Create an incident in NETWORK category
        incident = fixture.service.createIncident(new com.iitsm.application.command.CreateIncidentCommand(
                fixture.employee.getId(),
                "VPN gateway down",
                "Cannot access internal network",
                fixture.itService.getId(),
                networkCat.getId(),
                null,
                com.iitsm.domain.model.CategorySource.MANUAL,
                Impact.HIGH,
                Urgency.HIGH,
                50
        ));
    }

    @Test
    void ranksTechniciansBySkillLevelDescendingWhenSkillBasedStrategyActive() {
        switchableStrategy.select(skillStrategy.name());

        List<TechnicianRecommendation> recommendations =
                recommendationService.getRecommendations(fixture.admin.getId(), incident.getId());

        assertNotNull(recommendations);
        assertEquals(3, recommendations.size());

        // 1st: Alice High (skill 5)
        assertEquals("TECH_HIGH", recommendations.get(0).technicianId());
        assertEquals("Alice High", recommendations.get(0).name());
        assertEquals(5.0, recommendations.get(0).score());
        assertTrue(recommendations.get(0).reason().contains("Skill level 5"));

        // 2nd: Bob Med (skill 3)
        assertEquals("TECH_MED", recommendations.get(1).technicianId());
        assertEquals(3.0, recommendations.get(1).score());

        // 3rd: Charlie Low (skill 1)
        assertEquals("TECH_LOW", recommendations.get(2).technicianId());
        assertEquals(1.0, recommendations.get(2).score());
    }

    @Test
    void ranksTechniciansByWorkloadAscendingWhenLoadBasedStrategyActive() {
        switchableStrategy.select(loadStrategy.name());

        List<TechnicianRecommendation> recommendations =
                recommendationService.getRecommendations(fixture.admin.getId(), incident.getId());

        assertNotNull(recommendations);
        assertEquals(3, recommendations.size());

        // techHighSkill was auto-assigned the incident during setup (workload = 1).
        // techMedSkill and techLowSkill have 0 active incidents, so they rank higher.
        // Between techMedSkill and techLowSkill (both workload 0), tie is broken by skill level (3 vs 1).
        assertEquals("TECH_MED", recommendations.get(0).technicianId());
        assertEquals(0, recommendations.get(0).activeWorkload());
        assertEquals(1.0, recommendations.get(0).score());

        assertEquals("TECH_LOW", recommendations.get(1).technicianId());
        assertEquals(0, recommendations.get(1).activeWorkload());
        assertEquals(1.0, recommendations.get(1).score());

        assertEquals("TECH_HIGH", recommendations.get(2).technicianId());
        assertEquals(1, recommendations.get(2).activeWorkload());
        assertEquals(0.5, recommendations.get(2).score());
    }

    @Test
    void filtersOutUnavailableTechnicians() {
        techHighSkill.markUnavailable();
        fixture.users.save(techHighSkill);

        List<TechnicianRecommendation> recommendations =
                recommendationService.getRecommendations(fixture.admin.getId(), incident.getId());

        assertEquals(2, recommendations.size());
        assertFalse(recommendations.stream().anyMatch(r -> r.technicianId().equals("TECH_HIGH")));
        assertEquals("TECH_MED", recommendations.get(0).technicianId());
    }

    @Test
    void filtersOutOverloadedTechnicians() {
        // Tech with max capacity 1
        Technician techBusy = new Technician("TECH_BUSY", "Busy Tech", "busy@corp.com", Role.TECHNICIAN,
                Map.of("NETWORK", 5), 8, 1);
        fixture.users.save(techBusy);

        // Assign one active incident to techBusy so they reach max capacity
        Incident anotherIncident = fixture.service.createIncident(new com.iitsm.application.command.CreateIncidentCommand(
                fixture.employee.getId(),
                "Another incident",
                "Desc",
                fixture.itService.getId(),
                networkCat.getId(),
                null,
                com.iitsm.domain.model.CategorySource.MANUAL,
                Impact.LOW,
                Urgency.LOW,
                1
        ));
        fixture.service.reassignIncident(new com.iitsm.application.command.AssignIncidentCommand(
                fixture.admin.getId(),
                anotherIncident.getId(),
                techBusy.getId()
        ));

        // Now techBusy has activeWorkload = 1, maxCapacity = 1 -> overloaded
        List<TechnicianRecommendation> recommendations =
                recommendationService.getRecommendations(fixture.admin.getId(), incident.getId());

        assertFalse(recommendations.stream().anyMatch(r -> r.technicianId().equals("TECH_BUSY")));
    }

    @Test
    void returnsEmptyListWhenNoCandidatesMatch() {
        // Create an incident in an unknown category for which no technician has skills
        Category securityCat = TestData.category("SECURITY");
        fixture.categories.save(securityCat);

        Incident secIncident = fixture.service.createIncident(new com.iitsm.application.command.CreateIncidentCommand(
                fixture.employee.getId(),
                "Security alert",
                "Firewall audit",
                fixture.itService.getId(),
                securityCat.getId(),
                null,
                com.iitsm.domain.model.CategorySource.MANUAL,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                10
        ));

        switchableStrategy.select(skillStrategy.name());
        List<TechnicianRecommendation> recommendations =
                recommendationService.getRecommendations(fixture.admin.getId(), secIncident.getId());

        assertTrue(recommendations.isEmpty());
    }

    @Test
    void throwsExceptionWhenActorIsNotAuthorizedToAssign() {
        // Employee does not have ASSIGN permission
        assertThrows(UnauthorizedOperationException.class, () ->
                recommendationService.getRecommendations(fixture.employee.getId(), incident.getId()));
    }

    @Test
    void throwsExceptionWhenActorIsInactive() {
        fixture.admin.deactivate();
        fixture.users.save(fixture.admin);

        assertThrows(UnauthorizedOperationException.class, () ->
                recommendationService.getRecommendations(fixture.admin.getId(), incident.getId()));
    }

    @Test
    void throwsExceptionWhenIncidentIsNotFound() {
        assertThrows(IncidentNotFoundException.class, () ->
                recommendationService.getRecommendations(fixture.admin.getId(), "NON_EXISTENT_INC"));
    }

    @Test
    void throwsExceptionWhenActorIsNotFound() {
        assertThrows(UserNotFoundException.class, () ->
                recommendationService.getRecommendations("UNKNOWN_USER", incident.getId()));
    }

    @Test
    void throwsExceptionWhenIncidentIsClosed() {
        String assignedTechId = incident.getAssignedTechnician().map(Technician::getId).orElse(techHighSkill.getId());
        fixture.service.acknowledgeIncident(assignedTechId, incident.getId());
        fixture.service.startWork(assignedTechId, incident.getId());
        fixture.service.resolveIncident(new com.iitsm.application.command.IncidentTextCommand(
                assignedTechId, incident.getId(), "Fixed"));
        fixture.service.verifyResolution(fixture.employee.getId(), incident.getId());
        fixture.service.closeIncident(new com.iitsm.application.command.CloseIncidentCommand(
                fixture.admin.getId(), incident.getId(), "Closing"));

        assertThrows(InvalidInputException.class, () ->
                recommendationService.getRecommendations(fixture.admin.getId(), incident.getId()));
    }

    @Test
    void validatesBlankParameters() {
        assertThrows(InvalidInputException.class, () ->
                recommendationService.getRecommendations("", incident.getId()));
        assertThrows(InvalidInputException.class, () ->
                recommendationService.getRecommendations(fixture.admin.getId(), "   "));
    }
}
