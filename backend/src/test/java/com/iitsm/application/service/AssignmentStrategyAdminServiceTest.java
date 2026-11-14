package com.iitsm.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.domain.assignment.LoadBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SkillBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SwitchableAssignmentStrategy;
import com.iitsm.domain.classification.NoOpClassifier;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
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

class AssignmentStrategyAdminServiceTest {

    private static final String ADMIN = "ADMIN01";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final SwitchableAssignmentStrategy switchable = new SwitchableAssignmentStrategy(
            List.of(new SkillBasedAssignmentStrategy(), new LoadBasedAssignmentStrategy()), "skill-based");
    private final AssignmentStrategyAdminService service =
            new AssignmentStrategyAdminService(users, new AdminPermissionPolicy(), switchable);

    AssignmentStrategyAdminServiceTest() {
        users.save(TestData.admin(ADMIN));
        users.save(TestData.employee("EMP01"));
        users.save(TestData.technician("TECH01", 3));
    }

    /** An IncidentService wired exactly like production: it holds the same switchable instance. */
    private IncidentService incidentServiceUsing(SwitchableAssignmentStrategy strategy) {
        InMemoryItServiceRepository services = new InMemoryItServiceRepository();
        InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
        InMemorySlaPolicyRepository slaPolicies = new InMemorySlaPolicyRepository();
        services.save(TestData.service("SRV1", BusinessCriticality.HIGH));
        for (Category category : TestData.defaultCategories()) {
            categories.save(category);
        }
        for (SlaPolicy policy : TestData.defaultSlaPolicies()) {
            slaPolicies.save(policy);
        }
        PriorityStrategy priority = new WeightedPriorityStrategy();
        return new IncidentService(new InMemoryIncidentRepository(), users, services, categories,
                new IncidentFactory(new InMemoryIncidentIdGenerator(), priority), priority,
                new SlaCalculator(new SlaPolicyResolver(slaPolicies)), strategy, new NoOpClassifier(),
                new IncidentPermissionPolicy(), new InMemoryDomainEventPublisher(), new TestClock());
    }

    private static CreateIncidentCommand networkIncident() {
        return new CreateIncidentCommand("EMP01", "VPN down", "Nobody can connect to the VPN", "SRV1", "NETWORK",
                null, CategorySource.MANUAL, Impact.HIGH, Urgency.HIGH, 1);
    }

    private static String assignee(Incident incident) {
        return incident.getAssignedTechnician().orElseThrow().getId();
    }

    @Test
    void getSelection_returnsTheActiveAndAvailableStrategies() {
        AssignmentStrategyAdminService.Selection selection = service.getSelection(ADMIN);

        assertEquals("skill-based", selection.selected());
        assertEquals(List.of("skill-based", "load-based"), selection.available());
    }

    @Test
    void select_changesTheActiveStrategy() {
        AssignmentStrategyAdminService.Selection selection = service.select(ADMIN, "load-based");

        assertEquals("load-based", selection.selected());
        assertEquals("load-based", service.getSelection(ADMIN).selected());
        assertEquals("load-based", switchable.selectedName());
    }

    @Test
    void select_switchingStrategy_changesTheAutomaticAssignmentResult() {
        users.save(new Technician("T-LOW", "Low", "low@corp.com", Role.TECHNICIAN, Map.of("NETWORK", 3), 5, 5));
        users.save(new Technician("T-HIGH", "High", "high@corp.com", Role.TECHNICIAN, Map.of("NETWORK", 5), 5, 5));
        IncidentService incidents = incidentServiceUsing(switchable);

        // skill-based (default): the most skilled technician gets every network incident
        Incident first = incidents.createIncident(networkIncident());
        Incident second = incidents.createIncident(networkIncident());
        assertEquals("T-HIGH", assignee(first));
        assertEquals("T-HIGH", assignee(second));

        // load-based: T-HIGH now has 2 active incidents, T-LOW none, so the work moves to T-LOW
        service.select(ADMIN, "load-based");
        Incident third = incidents.createIncident(networkIncident());
        assertEquals("T-LOW", assignee(third));

        // back to skill-based: the most skilled technician again
        service.select(ADMIN, "skill-based");
        Incident fourth = incidents.createIncident(networkIncident());
        assertEquals("T-HIGH", assignee(fourth));
        assertEquals(IncidentStatus.ASSIGNED, fourth.getStatus());
    }

    @Test
    void select_unknownOrBlankName_isRejectedAndKeepsTheCurrentStrategy() {
        assertThrows(InvalidInputException.class, () -> service.select(ADMIN, "round-robin"));
        assertThrows(InvalidInputException.class, () -> service.select(ADMIN, " "));
        assertThrows(InvalidInputException.class, () -> service.select(ADMIN, null));

        assertEquals("skill-based", service.getSelection(ADMIN).selected());
    }

    @Test
    void nonAdministrator_cannotReadOrChangeTheStrategy() {
        for (String actor : List.of("EMP01", "TECH01")) {
            assertThrows(UnauthorizedOperationException.class, () -> service.getSelection(actor));
            assertThrows(UnauthorizedOperationException.class, () -> service.select(actor, "load-based"));
        }

        assertEquals("skill-based", switchable.selectedName());
    }

    @Test
    void itManager_isAnAdministrator_andUnknownOrInactiveActorsAreRejected() {
        users.save(new Administrator("MGR", "Manager", "mgr@corp.com", Role.IT_MANAGER));
        Administrator inactive = new Administrator("OLD", "Old", "old@corp.com", Role.ADMINISTRATOR);
        inactive.deactivate();
        users.save(inactive);

        assertEquals("load-based", service.select("MGR", "load-based").selected());
        assertThrows(UnauthorizedOperationException.class, () -> service.select("OLD", "skill-based"));
        assertThrows(UserNotFoundException.class, () -> service.select("NOBODY", "skill-based"));
        assertThrows(InvalidInputException.class, () -> service.select(" ", "skill-based"));
    }

    @Test
    void constructor_nullDependency_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> new AssignmentStrategyAdminService(null, new AdminPermissionPolicy(), switchable));
        assertThrows(InvalidInputException.class,
                () -> new AssignmentStrategyAdminService(users, null, switchable));
        assertThrows(InvalidInputException.class,
                () -> new AssignmentStrategyAdminService(users, new AdminPermissionPolicy(), null));
    }
}
