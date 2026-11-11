package com.iitsm.application.service;

import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.domain.assignment.AssignmentStrategy;
import com.iitsm.domain.assignment.LoadBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SkillBasedAssignmentStrategy;
import com.iitsm.domain.classification.NoOpClassifier;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.priority.PriorityStrategy;
import com.iitsm.domain.priority.WeightedPriorityStrategy;
import com.iitsm.domain.sla.SlaCalculator;
import com.iitsm.domain.sla.SlaPolicyResolver;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;
import com.iitsm.infrastructure.persistence.memory.InMemoryCategoryRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentIdGenerator;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryItServiceRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaPolicyRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestClock;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real skill-based and load-based strategies through {@link IncidentService},
 * using only in-memory repositories (no Spring context).
 */
class AssignmentStrategiesAutoAssignTest {

    private static final String EMPLOYEE_ID = "EMP01";
    private static final String ADMIN_ID = "ADMIN01";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();

    private IncidentService serviceWith(AssignmentStrategy strategy) {
        InMemoryItServiceRepository services = new InMemoryItServiceRepository();
        InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
        InMemorySlaPolicyRepository slaPolicies = new InMemorySlaPolicyRepository();

        users.save(TestData.employee(EMPLOYEE_ID));
        users.save(TestData.admin(ADMIN_ID));
        services.save(TestData.service("SRV1", BusinessCriticality.HIGH));
        for (Category category : TestData.defaultCategories()) {
            categories.save(category);
        }
        for (SlaPolicy policy : TestData.defaultSlaPolicies()) {
            slaPolicies.save(policy);
        }

        PriorityStrategy priority = new WeightedPriorityStrategy();
        return new IncidentService(
                incidents, users, services, categories,
                new IncidentFactory(new InMemoryIncidentIdGenerator(), priority),
                priority,
                new SlaCalculator(new SlaPolicyResolver(slaPolicies)),
                strategy,
                new NoOpClassifier(),
                new IncidentPermissionPolicy(),
                new InMemoryDomainEventPublisher(),
                new TestClock());
    }

    private Technician addTechnician(String id, Map<String, Integer> skills, int maxActive) {
        Technician technician = new Technician(id, "Tech " + id, id.toLowerCase() + "@corp.com",
                Role.TECHNICIAN, skills, 5, maxActive);
        users.save(technician);
        return technician;
    }

    private static CreateIncidentCommand networkIncident() {
        return new CreateIncidentCommand(EMPLOYEE_ID, "VPN down", "Nobody can connect to the VPN",
                "SRV1", "NETWORK", null, CategorySource.MANUAL, Impact.HIGH, Urgency.HIGH, 1);
    }

    private static String assigneeOf(Incident incident) {
        return incident.getAssignedTechnician().orElseThrow().getId();
    }

    @Test
    void skillBased_createIncident_assignsMostSkilledTechnicianForCategory() {
        addTechnician("T-LOW", Map.of("NETWORK", 3), 5);
        addTechnician("T-HIGH", Map.of("NETWORK", 5), 5);
        addTechnician("T-DB", Map.of("DATABASE", 5), 5);
        IncidentService service = serviceWith(new SkillBasedAssignmentStrategy());

        Incident incident = service.createIncident(networkIncident());

        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertEquals("T-HIGH", assigneeOf(incident));
    }

    @Test
    void skillBased_noTechnicianWithTheSkill_incidentStaysClassified() {
        addTechnician("T-DB", Map.of("DATABASE", 5), 5);
        IncidentService service = serviceWith(new SkillBasedAssignmentStrategy());

        Incident incident = service.createIncident(networkIncident());

        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
        assertTrue(incident.getAssignedTechnician().isEmpty());
    }

    @Test
    void loadBased_createIncident_spreadsIncidentsAcrossTechnicians() {
        addTechnician("T-A", Map.of("NETWORK", 3), 5);
        addTechnician("T-B", Map.of("NETWORK", 5), 5);
        IncidentService service = serviceWith(new LoadBasedAssignmentStrategy());

        Incident first = service.createIncident(networkIncident());
        Incident second = service.createIncident(networkIncident());
        Incident third = service.createIncident(networkIncident());

        assertEquals("T-B", assigneeOf(first));   // equal load, higher skill wins the tie
        assertEquals("T-A", assigneeOf(second));  // T-B now has 1 active incident
        assertEquals("T-B", assigneeOf(third));   // 1 vs 1 again, skill wins the tie
    }

    @Test
    void loadBased_overloadedTechnicianIsSkipped() {
        addTechnician("T-A", Map.of("NETWORK", 1), 5);
        addTechnician("T-B", Map.of("NETWORK", 5), 1);
        IncidentService service = serviceWith(new LoadBasedAssignmentStrategy());

        Incident first = service.createIncident(networkIncident());
        Incident second = service.createIncident(networkIncident());

        assertEquals("T-B", assigneeOf(first));
        assertEquals("T-A", assigneeOf(second));  // T-B reached maxActiveIncidents = 1
    }

    @Test
    void autoAssignIncident_adminActor_assignsOnceATechnicianBecomesAvailable() {
        Technician technician = addTechnician("T-A", Map.of("NETWORK", 3), 5);
        technician.markUnavailable();
        IncidentService service = serviceWith(new SkillBasedAssignmentStrategy());
        Incident created = service.createIncident(networkIncident());
        assertEquals(IncidentStatus.CLASSIFIED, created.getStatus());

        technician.markAvailable();
        Incident assigned = service.autoAssignIncident(ADMIN_ID, created.getId());

        assertEquals(IncidentStatus.ASSIGNED, assigned.getStatus());
        assertEquals("T-A", assigneeOf(assigned));
    }
}
