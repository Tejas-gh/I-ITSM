package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.iitsm.api.dto.TechnicianRecommendationResponse;
import com.iitsm.application.service.ServiceFixture;
import com.iitsm.application.service.TechnicianRecommendationService;
import com.iitsm.domain.assignment.SkillBasedAssignmentStrategy;
import com.iitsm.domain.exception.IncidentNotFoundException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.support.TestData;

class TechnicianRecommendationControllerTest {

    private ServiceFixture fixture;
    private TechnicianRecommendationService recommendationService;
    private TechnicianRecommendationController controller;
    private Incident incident;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();

        // Deactivate default seeded technicians so our test technicians are isolated
        fixture.technician.deactivate();
        fixture.technician2.deactivate();
        fixture.users.save(fixture.technician);
        fixture.users.save(fixture.technician2);

        SkillBasedAssignmentStrategy strategy = new SkillBasedAssignmentStrategy();
        recommendationService = new TechnicianRecommendationService(
                fixture.incidents,
                fixture.users,
                strategy,
                fixture.permissions
        );
        controller = new TechnicianRecommendationController(recommendationService);

        fixture.users.save(fixture.admin);
        fixture.users.save(fixture.employee);

        Category netCat = TestData.category("NETWORK");
        fixture.categories.save(netCat);

        Technician t1 = new Technician("T1", "Tech One", "t1@corp.com", Role.TECHNICIAN,
                Map.of("NETWORK", 4), 6, 5);
        Technician t2 = new Technician("T2", "Tech Two", "t2@corp.com", Role.TECHNICIAN,
                Map.of("NETWORK", 2), 3, 5);
        fixture.users.save(t1);
        fixture.users.save(t2);

        incident = fixture.service.createIncident(new com.iitsm.application.command.CreateIncidentCommand(
                fixture.employee.getId(),
                "Network switch failure",
                "Switch down in DC1",
                fixture.itService.getId(),
                netCat.getId(),
                null,
                com.iitsm.domain.model.CategorySource.MANUAL,
                Impact.HIGH,
                Urgency.HIGH,
                100
        ));
    }

    @Test
    void getRecommendationsReturnsRankedTechniciansForAdmin() {
        Authentication auth = new UsernamePasswordAuthenticationToken(fixture.admin.getId(), "n/a");

        List<TechnicianRecommendationResponse> response = controller.getRecommendations(incident.getId(), auth);

        assertNotNull(response);
        assertEquals(2, response.size());

        // T1 has skill 4, T2 has skill 2
        assertEquals("T1", response.get(0).technicianId());
        assertEquals("Tech One", response.get(0).name());
        assertEquals(4.0, response.get(0).score());
        assertEquals(5, response.get(0).maxCapacity());
        assertEquals(6, response.get(0).yearsOfExperience());
        assertTrue(response.get(0).reason().contains("Skill level 4"));

        assertEquals("T2", response.get(1).technicianId());
        assertEquals("Tech Two", response.get(1).name());
        assertEquals(2.0, response.get(1).score());
    }

    @Test
    void getRecommendationsThrowsUnauthorizedForEmployee() {
        Authentication auth = new UsernamePasswordAuthenticationToken(fixture.employee.getId(), "n/a");

        assertThrows(UnauthorizedOperationException.class, () ->
                controller.getRecommendations(incident.getId(), auth));
    }

    @Test
    void getRecommendationsThrowsNotFoundForMissingIncident() {
        Authentication auth = new UsernamePasswordAuthenticationToken(fixture.admin.getId(), "n/a");

        assertThrows(IncidentNotFoundException.class, () ->
                controller.getRecommendations("INC-DOES-NOT-EXIST", auth));
    }
}
