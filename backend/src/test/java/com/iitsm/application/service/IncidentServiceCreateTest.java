package com.iitsm.application.service;

import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.domain.event.AssignmentFailed;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentAssigned;
import com.iitsm.domain.event.IncidentClassified;
import com.iitsm.domain.event.IncidentCreated;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.exception.ServiceNotFoundException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentServiceCreateTest {

    private ServiceFixture f;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
    }

    @Test
    @DisplayName("createIncident successfully creates, calculates priority, sets SLA, classifies, auto-assigns, and publishes events in order")
    void createIncident_validCommand_createsClassifiesAndAutoAssigns() {
        CreateIncidentCommand cmd = new CreateIncidentCommand(
                f.employee.getId(),
                "Payment Failure",
                "Transactions are timing out",
                f.itService.getId(),
                f.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.CRITICAL,
                Urgency.HIGH,
                50
        );

        Incident incident = f.service.createIncident(cmd);

        assertNotNull(incident);
        assertEquals("INC1001", incident.getId());
        assertEquals(PriorityLevel.P1, incident.getPriority());
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertTrue(incident.getAssignedTechnician().isPresent());
        assertEquals(f.technician.getId(), incident.getAssignedTechnician().get().getId());
        assertTrue(incident.getResponseDeadline().isPresent());
        assertTrue(incident.getResolutionDeadline().isPresent());

        // Assert event sequence
        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(3, events.size());
        assertInstanceOf(IncidentCreated.class, events.get(0));
        assertInstanceOf(IncidentClassified.class, events.get(1));
        assertInstanceOf(IncidentAssigned.class, events.get(2));

        IncidentAssigned assignedEvent = (IncidentAssigned) events.get(2);
        assertTrue(assignedEvent.automatic());
        assertEquals("SYSTEM", assignedEvent.actorId());
    }

    @Test
    @DisplayName("missing SLA policy fails before anything is saved or published")
    void createIncident_missingSla_failsBeforeSaveOrPublish() {
        // Clear all SLA policies to induce MissingSlaException
        ServiceFixture emptySlaFixture = new ServiceFixture();
        // Remove policies from repository by testing with a new unseeded repository fixture
        ServiceFixture freshFixture = new ServiceFixture();
        // Remove default policies by overwriting repo or creating an empty policy repo service
        IncidentService serviceWithNoSla = new IncidentService(
                freshFixture.incidents,
                freshFixture.users,
                freshFixture.services,
                freshFixture.categories,
                freshFixture.incidentFactory,
                freshFixture.priorityStrategy,
                new com.iitsm.domain.sla.SlaCalculator(new com.iitsm.domain.sla.SlaPolicyResolver(new com.iitsm.infrastructure.persistence.memory.InMemorySlaPolicyRepository())),
                freshFixture.assignmentStrategy,
                freshFixture.classifier,
                freshFixture.permissions,
                freshFixture.events,
                freshFixture.clock
        );

        CreateIncidentCommand cmd = new CreateIncidentCommand(
                freshFixture.employee.getId(),
                "Title",
                "Desc",
                freshFixture.itService.getId(),
                freshFixture.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.LOW,
                Urgency.LOW,
                1
        );

        assertThrows(MissingSlaException.class, () -> serviceWithNoSla.createIncident(cmd));
        assertEquals(0, freshFixture.incidents.findAll().size(), "Nothing should be saved to repository");
        assertEquals(0, freshFixture.listener.getEvents().size(), "No events should be published");
    }

    @Test
    @DisplayName("unknown service throws ServiceNotFoundException")
    void createIncident_unknownService_throwsServiceNotFoundException() {
        CreateIncidentCommand cmd = new CreateIncidentCommand(
                f.employee.getId(), "Title", "Desc", "SRV-NONEXISTENT",
                f.category.getId(), null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1
        );

        assertThrows(ServiceNotFoundException.class, () -> f.service.createIncident(cmd));
    }

    @Test
    @DisplayName("unknown category throws CategoryNotFoundException")
    void createIncident_unknownCategory_throwsCategoryNotFoundException() {
        CreateIncidentCommand cmd = new CreateIncidentCommand(
                f.employee.getId(), "Title", "Desc", f.itService.getId(),
                "CAT-NONEXISTENT", null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1
        );

        assertThrows(CategoryNotFoundException.class, () -> f.service.createIncident(cmd));
    }

    @Test
    @DisplayName("non-employee actor is rejected with UnauthorizedOperationException")
    void createIncident_nonEmployee_throwsUnauthorizedOperationException() {
        CreateIncidentCommand cmd = new CreateIncidentCommand(
                f.admin.getId(), "Title", "Desc", f.itService.getId(),
                f.category.getId(), null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1
        );

        assertThrows(UnauthorizedOperationException.class, () -> f.service.createIncident(cmd));
    }

    @Test
    @DisplayName("inactive actor is rejected with UnauthorizedOperationException")
    void createIncident_inactiveActor_throwsUnauthorizedOperationException() {
        f.employee.deactivate();
        CreateIncidentCommand cmd = new CreateIncidentCommand(
                f.employee.getId(), "Title", "Desc", f.itService.getId(),
                f.category.getId(), null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1
        );

        assertThrows(UnauthorizedOperationException.class, () -> f.service.createIncident(cmd));
    }

    @Test
    @DisplayName("auto-assign with no available candidate leaves incident CLASSIFIED and publishes AssignmentFailed")
    void createIncident_noCandidateAvailable_leavesIncidentClassifiedAndPublishesAssignmentFailed() {
        // Deactivate all technicians so no candidates are available
        f.technician.deactivate();
        f.technician2.deactivate();

        CreateIncidentCommand cmd = new CreateIncidentCommand(
                f.employee.getId(), "Title", "Desc", f.itService.getId(),
                f.category.getId(), null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1
        );

        Incident incident = f.service.createIncident(cmd);

        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
        assertTrue(incident.getAssignedTechnician().isEmpty());

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(3, events.size());
        assertInstanceOf(IncidentCreated.class, events.get(0));
        assertInstanceOf(IncidentClassified.class, events.get(1));
        assertInstanceOf(AssignmentFailed.class, events.get(2));
        assertEquals("SYSTEM", events.get(2).actorId());
    }

    @Test
    @DisplayName("unknown suggested category throws CategoryNotFoundException")
    void createIncident_unknownSuggestedCategory_throwsCategoryNotFoundException() {
        CreateIncidentCommand cmd = new CreateIncidentCommand(
                f.employee.getId(), "Title", "Desc", f.itService.getId(),
                f.category.getId(), "SUGGESTED-UNKNOWN", CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1
        );

        assertThrows(CategoryNotFoundException.class, () -> f.service.createIncident(cmd));
    }

    @Test
    @DisplayName("suggested-category invariants from draft rules are enforced")
    void createIncident_suggestedCategoryInvariants_enforced() {
        // If categorySource != MANUAL, suggestedCategory must equal category
        Category otherCategory = f.categories.findById("DATABASE").orElseThrow();

        CreateIncidentCommand mismatchCmd = new CreateIncidentCommand(
                f.employee.getId(), "Title", "Desc", f.itService.getId(),
                f.category.getId(), otherCategory.getId(), CategorySource.KEYWORD, Impact.LOW, Urgency.LOW, 1
        );

        assertThrows(InvalidInputException.class, () -> f.service.createIncident(mismatchCmd));
    }
}
