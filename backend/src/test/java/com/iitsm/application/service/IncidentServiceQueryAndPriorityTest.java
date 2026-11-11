package com.iitsm.application.service;

import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.command.IncidentTextCommand;
import com.iitsm.application.command.UpdatePriorityInputsCommand;
import com.iitsm.domain.classification.CategoryClassifier;
import com.iitsm.domain.classification.CategorySuggestion;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.PriorityChanged;
import com.iitsm.domain.exception.IncidentNotFoundException;
import com.iitsm.domain.exception.InvalidIncidentException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IncidentServiceQueryAndPriorityTest {

    private ServiceFixture f;
    private Incident incident;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();

        // Create incident with Impact.LOW, Urgency.LOW (P4)
        incident = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(), "Email delayed", "Incoming emails take minutes", f.itService.getId(),
                f.category.getId(), null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 5
        ));
        f.listener.clear();
    }

    @Test
    @DisplayName("updatePriorityInputs recalculates score, deadlines, and publishes PriorityChanged event")
    void updatePriorityInputs_changedInputs_recalculatesAndPublishesEvent() {
        int previousScore = incident.getPriorityScore();
        UpdatePriorityInputsCommand cmd = new UpdatePriorityInputsCommand(
                f.admin.getId(), incident.getId(), Impact.CRITICAL, Urgency.CRITICAL
        );

        Incident updated = f.service.updatePriorityInputs(cmd);

        assertEquals(PriorityLevel.P1, updated.getPriority());
        assertTrue(updated.getPriorityScore() > previousScore);

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(1, events.size());
        assertInstanceOf(PriorityChanged.class, events.get(0));
        PriorityChanged event = (PriorityChanged) events.get(0);
        assertEquals(PriorityLevel.P4, event.previousPriority());
        assertEquals(PriorityLevel.P1, event.newPriority());
    }

    @Test
    @DisplayName("updatePriorityInputs with unchanged inputs returns incident with no save and no events")
    void updatePriorityInputs_unchangedInputs_noEventPublished() {
        UpdatePriorityInputsCommand cmd = new UpdatePriorityInputsCommand(
                f.admin.getId(), incident.getId(), Impact.LOW, Urgency.LOW
        );

        Incident updated = f.service.updatePriorityInputs(cmd);
        assertSame(incident, updated);
        assertEquals(0, f.listener.getEvents().size());
    }

    @Test
    @DisplayName("updatePriorityInputs preserves response deadline if incident was already acknowledged")
    void updatePriorityInputs_alreadyAcknowledged_preservesResponseDeadline() {
        f.service.acknowledgeIncident(f.technician.getId(), incident.getId());
        Instant originalResponseDeadline = incident.getResponseDeadline().orElseThrow();
        f.listener.clear();

        // Elevate priority from P4 to P1
        UpdatePriorityInputsCommand cmd = new UpdatePriorityInputsCommand(
                f.admin.getId(), incident.getId(), Impact.CRITICAL, Urgency.CRITICAL
        );
        Incident updated = f.service.updatePriorityInputs(cmd);

        assertEquals(originalResponseDeadline, updated.getResponseDeadline().orElseThrow());
    }

    @Test
    @DisplayName("updatePriorityInputs is forbidden after incident is resolved")
    void updatePriorityInputs_afterResolved_throwsInvalidIncidentException() {
        f.service.acknowledgeIncident(f.technician.getId(), incident.getId());
        f.service.startWork(f.technician.getId(), incident.getId());
        f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), incident.getId(), "Resolved issue"));

        UpdatePriorityInputsCommand cmd = new UpdatePriorityInputsCommand(
                f.admin.getId(), incident.getId(), Impact.HIGH, Urgency.HIGH
        );

        assertThrows(InvalidIncidentException.class, () -> f.service.updatePriorityInputs(cmd));
    }

    @Test
    @DisplayName("getIncident enforces visibility per role")
    void getIncident_roleVisibility_enforced() {
        // Reporter can view
        assertEquals(incident.getId(), f.service.getIncident(f.employee.getId(), incident.getId()).getId());
        // Assigned technician can view
        assertEquals(incident.getId(), f.service.getIncident(f.technician.getId(), incident.getId()).getId());
        // Admin can view
        assertEquals(incident.getId(), f.service.getIncident(f.admin.getId(), incident.getId()).getId());

        // Unassigned technician cannot view
        assertThrows(UnauthorizedOperationException.class,
                () -> f.service.getIncident(f.technician2.getId(), incident.getId()));
    }

    @Test
    @DisplayName("listVisibleIncidents returns filtered lists per user role")
    void listVisibleIncidents_filtersPerRole() {
        // Employee sees own reported
        List<Incident> employeeList = f.service.listVisibleIncidents(f.employee.getId());
        assertEquals(1, employeeList.size());

        // Assigned technician sees assigned
        List<Incident> tech1List = f.service.listVisibleIncidents(f.technician.getId());
        assertEquals(1, tech1List.size());

        // Other technician sees 0
        List<Incident> tech2List = f.service.listVisibleIncidents(f.technician2.getId());
        assertEquals(0, tech2List.size());

        // Admin sees all
        List<Incident> adminList = f.service.listVisibleIncidents(f.admin.getId());
        assertEquals(1, adminList.size());
    }

    @Test
    @DisplayName("getIncident with unknown incidentId throws IncidentNotFoundException")
    void getIncident_unknownId_throwsIncidentNotFoundException() {
        assertThrows(IncidentNotFoundException.class,
                () -> f.service.getIncident(f.admin.getId(), "INC-NONEXISTENT"));
    }

    @Test
    @DisplayName("suggestCategory discards unknown or inactive suggestions and keeps valid ones")
    void suggestCategory_validAndInvalidSuggestions_handledCorrectly() {
        // 1. Default NoOpClassifier returns empty
        Optional<CategorySuggestion> noOp = f.service.suggestCategory(f.employee.getId(), "Title", "Desc");
        assertTrue(noOp.isEmpty());

        // 2. Custom classifier returning valid active category
        CategoryClassifier activeClassifier = (t, d) ->
                Optional.of(new CategorySuggestion(f.category.getId(), 0.9, "Matched keyword", CategorySource.KEYWORD));

        IncidentService customService = new IncidentService(
                f.incidents, f.users, f.services, f.categories, f.incidentFactory,
                f.priorityStrategy, f.slaCalculator, f.assignmentStrategy, activeClassifier,
                f.permissions, f.events, f.clock
        );

        Optional<CategorySuggestion> valid = customService.suggestCategory(f.employee.getId(), "Title", "Desc");
        assertTrue(valid.isPresent());
        assertEquals(f.category.getId(), valid.get().categoryId());

        // 3. Classifier returning unknown category is discarded
        CategoryClassifier unknownClassifier = (t, d) ->
                Optional.of(new CategorySuggestion("CAT-UNKNOWN", 0.8, "Reason", CategorySource.KEYWORD));

        IncidentService unknownService = new IncidentService(
                f.incidents, f.users, f.services, f.categories, f.incidentFactory,
                f.priorityStrategy, f.slaCalculator, f.assignmentStrategy, unknownClassifier,
                f.permissions, f.events, f.clock
        );

        Optional<CategorySuggestion> discarded = unknownService.suggestCategory(f.employee.getId(), "Title", "Desc");
        assertTrue(discarded.isEmpty());
    }
}
