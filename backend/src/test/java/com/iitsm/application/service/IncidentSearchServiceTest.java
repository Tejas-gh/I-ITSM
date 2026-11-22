package com.iitsm.application.service;

import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.infrastructure.persistence.memory.InMemoryEscalationRepository;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.*;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaStatus;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentSearchServiceTest {

    private ServiceFixture f;
    private SlaEvaluator slaEvaluator;
    private IncidentSearchService searchService;
    private InMemoryEscalationRepository escalations;
    private Technician teamLead;

    private Incident inc1; // Reported by EMP01, assigned to TECH01, P1
    private Incident inc2; // Reported by EMP02, assigned to TECH02, P2
    private Incident inc3; // Reported by EMP01, unassigned, P3
    private Employee emp2;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
        slaEvaluator = new SlaEvaluator();
        escalations = new InMemoryEscalationRepository();
        teamLead = TestData.teamLead("LEAD01");
        f.users.save(teamLead);
        searchService = new IncidentSearchService(f.incidents, f.users, slaEvaluator, f.clock, escalations);

        emp2 = TestData.employee("EMP02");
        f.users.save(emp2);

        // Deactivate technicians for inc3 so it stays unassigned (CLASSIFIED)
        f.technician.deactivate();
        f.technician2.deactivate();

        // Create inc3 first
        inc3 = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(), "Incident 3", "Desc 3",
                f.itService.getId(), f.category.getId(), null,
                CategorySource.MANUAL, Impact.MEDIUM, Urgency.MEDIUM, 1
        ));

        // Reactivate technicians for inc1 and inc2
        f.technician.activate();
        f.technician2.activate();

        // Assign strategy picks tech1 for inc1
        f.assignmentStrategy.setDesignatedTechnician(f.technician);
        inc1 = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(), "Incident 1", "Desc 1",
                f.itService.getId(), f.category.getId(), null,
                CategorySource.MANUAL, Impact.CRITICAL, Urgency.CRITICAL, 100
        ));

        // Assign strategy picks tech2 for inc2
        f.assignmentStrategy.setDesignatedTechnician(f.technician2);
        inc2 = f.service.createIncident(new CreateIncidentCommand(
                emp2.getId(), "Incident 2", "Desc 2",
                f.itService.getId(), f.category.getId(), null,
                CategorySource.MANUAL, Impact.MEDIUM, Urgency.HIGH, 20
        ));
    }

    @Test
    @DisplayName("Empty query returns exactly listVisibleIncidents for each role")
    void emptyQuery_returnsVisibleIncidents() {
        // Employee sees only their own reported incidents (inc3, inc1)
        List<Incident> empResults = searchService.search(f.employee.getId(), IncidentSearchQuery.empty());
        assertEquals(2, empResults.size());
        assertTrue(empResults.stream().allMatch(i -> i.getReporter().getId().equals(f.employee.getId())));

        // Technician sees only incidents assigned to them (inc1)
        List<Incident> techResults = searchService.search(f.technician.getId(), IncidentSearchQuery.empty());
        assertEquals(1, techResults.size());
        assertEquals(inc1.getId(), techResults.get(0).getId());

        // Admin sees all incidents (inc3, inc1, inc2) in insertion order
        List<Incident> adminResults = searchService.search(f.admin.getId(), null);
        assertEquals(3, adminResults.size());
        assertEquals(inc3.getId(), adminResults.get(0).getId());
        assertEquals(inc1.getId(), adminResults.get(1).getId());
        assertEquals(inc2.getId(), adminResults.get(2).getId());
    }

    @Test
    @DisplayName("Technician passing another technician's id gets an empty result")
    void technician_passingAnotherTechnicianId_returnsEmpty() {
        IncidentSearchQuery query = new IncidentSearchQuery(
                null, null, null, null, null, null, null, f.technician2.getId(), null
        );

        List<Incident> results = searchService.search(f.technician.getId(), query);
        assertTrue(results.isEmpty(), "Technician must not see incidents assigned to other technicians");
    }

    @Test
    @DisplayName("Technician passing their own technician id gets their assigned incidents")
    void technician_passingOwnTechnicianId_returnsAssigned() {
        IncidentSearchQuery query = new IncidentSearchQuery(
                null, null, null, null, null, null, null, f.technician.getId(), null
        );

        List<Incident> results = searchService.search(f.technician.getId(), query);
        assertEquals(1, results.size());
        assertEquals(inc1.getId(), results.get(0).getId());
    }

    @Test
    @DisplayName("Filters by priority and status")
    void search_byPriorityAndStatus() {
        IncidentSearchQuery queryP1 = new IncidentSearchQuery(
                null, null, null, null, PriorityLevel.P1, null, null, null, null
        );
        List<Incident> p1Results = searchService.search(f.admin.getId(), queryP1);
        assertEquals(1, p1Results.size());
        assertEquals(inc1.getId(), p1Results.get(0).getId());

        IncidentSearchQuery queryAssigned = new IncidentSearchQuery(
                null, null, null, IncidentStatus.ASSIGNED, null, null, null, null, null
        );
        List<Incident> assignedResults = searchService.search(f.admin.getId(), queryAssigned);
        assertEquals(2, assignedResults.size());
        assertTrue(assignedResults.stream().allMatch(i -> i.getStatus() == IncidentStatus.ASSIGNED));
    }

    @Test
    @DisplayName("Filters by exact incident ID")
    void search_byExactIncidentId() {
        IncidentSearchQuery query = new IncidentSearchQuery(
                inc2.getId(), null, null, null, null, null, null, null, null
        );
        List<Incident> results = searchService.search(f.admin.getId(), query);
        assertEquals(1, results.size());
        assertEquals(inc2.getId(), results.get(0).getId());
    }

    @Test
    @DisplayName("SLA status filter identifies BREACHED incidents when clock advances past deadline")
    void search_bySlaStatus_breached() {
        // Advance clock by 2 hours so P1 deadlines (response 5 min, resolution 30 min) breach
        f.clock.advance(Duration.ofHours(2));

        IncidentSearchQuery query = new IncidentSearchQuery(
                null, null, null, null, null, null, null, null, SlaStatus.BREACHED
        );
        List<Incident> breached = searchService.search(f.admin.getId(), query);
        assertFalse(breached.isEmpty());
        assertTrue(breached.stream().anyMatch(i -> i.getId().equals(inc1.getId())));
    }

    @Test
    @DisplayName("SLA filter present: an incident without deadlines is safely excluded without exception")
    void search_slaFilter_incidentWithoutDeadlines_safelyExcluded() {
        // Create an incident draft directly and save without SLA deadlines
        IncidentDraft draft = TestData.draft(
                f.employee, f.itService, f.category,
                Impact.LOW, Urgency.LOW, 1
        );
        Incident noDeadlinesInc = f.incidentFactory.create(draft, f.clock.instant());
        f.incidents.save(noDeadlinesInc);

        IncidentSearchQuery query = new IncidentSearchQuery(
                null, null, null, null, null, null, null, null, SlaStatus.ON_TRACK
        );

        assertDoesNotThrow(() -> {
            List<Incident> results = searchService.search(f.admin.getId(), query);
            // Must not contain the incident with missing SLA deadlines
            assertFalse(results.stream().anyMatch(i -> i.getId().equals(noDeadlinesInc.getId())));
        });
    }

    @Test
    @DisplayName("No SLA filter: an incident without deadlines is included normally")
    void search_noSlaFilter_incidentWithoutDeadlines_includedNormally() {
        IncidentDraft draft = TestData.draft(
                f.employee, f.itService, f.category,
                Impact.LOW, Urgency.LOW, 1
        );
        Incident noDeadlinesInc = f.incidentFactory.create(draft, f.clock.instant());
        f.incidents.save(noDeadlinesInc);

        List<Incident> results = searchService.search(f.admin.getId(), IncidentSearchQuery.empty());
        assertTrue(results.stream().anyMatch(i -> i.getId().equals(noDeadlinesInc.getId())));
    }

    @Test
    @DisplayName("Validates actor status and invalid date ranges")
    void validation_errors() {
        // Blank actorId
        assertThrows(InvalidInputException.class, () ->
                searchService.search("", IncidentSearchQuery.empty()));

        // Unknown actor
        assertThrows(UserNotFoundException.class, () ->
                searchService.search("UNKNOWN_USER", IncidentSearchQuery.empty()));

        // Inactive actor
        f.employee.deactivate();
        assertThrows(UnauthorizedOperationException.class, () ->
                searchService.search(f.employee.getId(), IncidentSearchQuery.empty()));

        // createdFrom after createdTo
        Instant now = f.clock.instant();
        assertThrows(InvalidInputException.class, () ->
                new IncidentSearchQuery(null, now.plusSeconds(10), now, null, null, null, null, null, null));
    }
    @Test
    @DisplayName("Team Lead sees assigned incidents and not unrelated incidents")
    void teamLead_seesAssignedOnlyWithoutEscalation() {
        // Move inc1 to the Team Lead so it is normally visible by assignment.
        inc1.unassign();
        inc1.assign(teamLead, f.clock.instant());

        List<Incident> results = searchService.search(teamLead.getId(), IncidentSearchQuery.empty());

        assertEquals(1, results.size());
        assertEquals(inc1.getId(), results.get(0).getId());
    }

    @Test
    @DisplayName("Team Lead sees an unassigned incident when latest escalation targets Team Lead")
    void teamLead_seesUnassignedIncidentEscalatedToTeamLead() {
        Instant now = f.clock.instant();
        escalations.save(EscalationRecord.create(inc3.getId(), 1, Role.TEAM_LEAD, now, "L1 escalation"));

        List<Incident> results = searchService.search(teamLead.getId(), IncidentSearchQuery.empty());

        assertEquals(1, results.size());
        assertEquals(inc3.getId(), results.get(0).getId());
    }

    @Test
    @DisplayName("Latest L2 escalation removes Team Lead scoped discovery")
    void teamLead_losesScopedVisibilityWhenL2BecomesLatest() {
        Instant now = f.clock.instant();
        escalations.save(EscalationRecord.create(inc3.getId(), 1, Role.TEAM_LEAD, now, "L1 escalation"));
        escalations.save(EscalationRecord.create(inc3.getId(), 2, Role.ADMINISTRATOR, now.plus(Duration.ofMinutes(1)), "L2 escalation"));

        List<Incident> results = searchService.search(teamLead.getId(), IncidentSearchQuery.empty());

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Escalated visibility still respects normal search filters")
    void teamLead_escalatedVisibilityRespectsSearchFilters() {
        Instant now = f.clock.instant();
        escalations.save(EscalationRecord.create(inc3.getId(), 1, Role.TEAM_LEAD, now, "L1 escalation"));

        IncidentSearchQuery matching = new IncidentSearchQuery(
                null, null, null, null, inc3.getPriority(), null, null, null, null
        );
        IncidentSearchQuery nonMatching = new IncidentSearchQuery(
                null, null, null, null, inc1.getPriority(), null, null, null, null
        );

        assertEquals(List.of(inc3.getId()), searchService.search(teamLead.getId(), matching)
                .stream().map(Incident::getId).toList());
        assertTrue(searchService.search(teamLead.getId(), nonMatching).isEmpty());
    }

}
