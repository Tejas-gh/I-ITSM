package com.iitsm.application.service;

import com.iitsm.application.command.CloseIncidentCommand;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.command.IncidentTextCommand;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentAcknowledged;
import com.iitsm.domain.event.IncidentAssigned;
import com.iitsm.domain.event.IncidentClassified;
import com.iitsm.domain.event.IncidentClosed;
import com.iitsm.domain.event.IncidentCreated;
import com.iitsm.domain.event.IncidentResolved;
import com.iitsm.domain.event.IncidentStatusChanged;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.sla.SlaEvaluation;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test covering complete in-memory incident lifecycles, event publication sequences,
 * and SLA evaluations reaching MET under various lifecycle flows.
 */
class IncidentLifecycleInMemoryTest {

    private ServiceFixture f;
    private SlaEvaluator evaluator;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
        evaluator = new SlaEvaluator();
    }

    @Test
    @DisplayName("complete lifecycle NEW->CLASSIFIED->ASSIGNED->ACKNOWLEDGED->IN_PROGRESS->RESOLVED->VERIFIED->CLOSED asserts exact event sequence and final SLA MET")
    void completeLifecycle_happyPath_transitionsThroughAllStatesPublishesAllEventsAndMeetsSla() {
        CreateIncidentCommand createCmd = new CreateIncidentCommand(
                f.employee.getId(),
                "Production Payment Outage",
                "Checkout service throwing 500",
                f.itService.getId(),
                f.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.CRITICAL,
                Urgency.HIGH,
                100
        );

        Incident created = f.service.createIncident(createCmd);
        String id = created.getId();

        assertEquals(IncidentStatus.ASSIGNED, created.getStatus());
        assertEquals(PriorityLevel.P1, created.getPriority());
        assertTrue(created.getResponseDeadline().isPresent());
        assertTrue(created.getResolutionDeadline().isPresent());
        assertTrue(created.getAssignedTechnician().isPresent());
        assertEquals(f.technician.getId(), created.getAssignedTechnician().get().getId());

        // Advance 5 minutes (exactly at the P1 response deadline)
        f.clock.advance(Duration.ofMinutes(5));
        Incident acked = f.service.acknowledgeIncident(f.technician.getId(), id);
        assertEquals(IncidentStatus.ACKNOWLEDGED, acked.getStatus());
        assertTrue(acked.getAcknowledgedAt().isPresent());

        // Advance 5 minutes
        f.clock.advance(Duration.ofMinutes(5));
        Incident inProgress = f.service.startWork(f.technician.getId(), id);
        assertEquals(IncidentStatus.IN_PROGRESS, inProgress.getStatus());

        // Advance 15 minutes (total 20 min from start, within the 30 min P1 resolution target)
        f.clock.advance(Duration.ofMinutes(15));
        IncidentTextCommand resolveCmd = new IncidentTextCommand(f.technician.getId(), id, "Restarted payment gateway connection pool");
        Incident resolved = f.service.resolveIncident(resolveCmd);
        assertEquals(IncidentStatus.RESOLVED, resolved.getStatus());
        assertTrue(resolved.getResolution().isPresent());
        assertTrue(resolved.getResolvedAt().isPresent());

        // Advance 10 minutes
        f.clock.advance(Duration.ofMinutes(10));
        Incident verified = f.service.verifyResolution(f.employee.getId(), id);
        assertEquals(IncidentStatus.VERIFIED, verified.getStatus());
        assertTrue(verified.getVerifiedAt().isPresent());

        // Advance 5 minutes
        f.clock.advance(Duration.ofMinutes(5));
        CloseIncidentCommand closeCmd = new CloseIncidentCommand(f.admin.getId(), id, "Incident resolved and verified by user");
        Incident closed = f.service.closeIncident(closeCmd);
        assertEquals(IncidentStatus.CLOSED, closed.getStatus());
        assertTrue(closed.getClosedAt().isPresent());
        assertEquals(f.admin.getId(), closed.getClosedById().orElseThrow());
        assertEquals("Incident resolved and verified by user", closed.getClosureNote().orElseThrow());

        // Verify stored incident repository state
        Incident stored = f.incidents.findById(id).orElseThrow();
        assertEquals(IncidentStatus.CLOSED, stored.getStatus());
        assertEquals(closed.getClosedAt(), stored.getClosedAt());

        // Assert exact event sequence (8 events)
        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(8, events.size());

        // 0: IncidentCreated
        assertInstanceOf(IncidentCreated.class, events.get(0));
        IncidentCreated e0 = (IncidentCreated) events.get(0);
        assertEquals(id, e0.incidentId());
        assertNull(e0.previousStatus());
        assertEquals(IncidentStatus.NEW, e0.newStatus());
        assertEquals(f.employee.getId(), e0.reporterId());
        assertEquals(PriorityLevel.P1, e0.priority());

        // 1: IncidentClassified
        assertInstanceOf(IncidentClassified.class, events.get(1));
        IncidentClassified e1 = (IncidentClassified) events.get(1);
        assertEquals(id, e1.incidentId());
        assertEquals(IncidentStatus.NEW, e1.previousStatus());
        assertEquals(IncidentStatus.CLASSIFIED, e1.newStatus());

        // 2: IncidentAssigned
        assertInstanceOf(IncidentAssigned.class, events.get(2));
        IncidentAssigned e2 = (IncidentAssigned) events.get(2);
        assertEquals(id, e2.incidentId());
        assertEquals(IncidentStatus.CLASSIFIED, e2.previousStatus());
        assertEquals(IncidentStatus.ASSIGNED, e2.newStatus());
        assertEquals(f.technician.getId(), e2.technicianId());
        assertTrue(e2.automatic());

        // 3: IncidentAcknowledged
        assertInstanceOf(IncidentAcknowledged.class, events.get(3));
        IncidentAcknowledged e3 = (IncidentAcknowledged) events.get(3);
        assertEquals(id, e3.incidentId());
        assertEquals(IncidentStatus.ASSIGNED, e3.previousStatus());
        assertEquals(IncidentStatus.ACKNOWLEDGED, e3.newStatus());
        assertEquals(f.technician.getId(), e3.technicianId());

        // 4: IncidentStatusChanged (WORK_STARTED)
        assertInstanceOf(IncidentStatusChanged.class, events.get(4));
        IncidentStatusChanged e4 = (IncidentStatusChanged) events.get(4);
        assertEquals(id, e4.incidentId());
        assertEquals(IncidentStatus.ACKNOWLEDGED, e4.previousStatus());
        assertEquals(IncidentStatus.IN_PROGRESS, e4.newStatus());
        assertEquals("WORK_STARTED", e4.action());

        // 5: IncidentResolved
        assertInstanceOf(IncidentResolved.class, events.get(5));
        IncidentResolved e5 = (IncidentResolved) events.get(5);
        assertEquals(id, e5.incidentId());
        assertEquals(IncidentStatus.IN_PROGRESS, e5.previousStatus());
        assertEquals(IncidentStatus.RESOLVED, e5.newStatus());
        assertEquals(f.technician.getId(), e5.resolverId());

        // 6: IncidentStatusChanged (VERIFIED)
        assertInstanceOf(IncidentStatusChanged.class, events.get(6));
        IncidentStatusChanged e6 = (IncidentStatusChanged) events.get(6);
        assertEquals(id, e6.incidentId());
        assertEquals(IncidentStatus.RESOLVED, e6.previousStatus());
        assertEquals(IncidentStatus.VERIFIED, e6.newStatus());
        assertEquals("VERIFIED", e6.action());

        // 7: IncidentClosed
        assertInstanceOf(IncidentClosed.class, events.get(7));
        IncidentClosed e7 = (IncidentClosed) events.get(7);
        assertEquals(id, e7.incidentId());
        assertEquals(IncidentStatus.VERIFIED, e7.previousStatus());
        assertEquals(IncidentStatus.CLOSED, e7.newStatus());
        assertEquals(f.admin.getId(), e7.closedById());

        // Assert final SLA evaluation: response MET, resolution MET, overall MET
        SlaEvaluation evaluation = evaluator.evaluate(closed, f.clock.instant());
        assertEquals(SlaStatus.MET, evaluation.response());
        assertEquals(SlaStatus.MET, evaluation.resolution());
        assertEquals(SlaStatus.MET, evaluation.overall());
    }

    @Test
    @DisplayName("lifecycle with waiting for user pauses SLA clock and accumulates paused duration so SLA is MET")
    void lifecycleWithWaitingForUser_pausesSlaClockAndAccumulatesPauseDuration() {
        CreateIncidentCommand createCmd = new CreateIncidentCommand(
                f.employee.getId(),
                "VPN Disconnect Issue",
                "Gateway drops connection every hour",
                f.itService.getId(),
                f.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.CRITICAL,
                Urgency.HIGH,
                50
        );

        Incident incident = f.service.createIncident(createCmd);
        String id = incident.getId();
        Instant initialResolutionDeadline = incident.getResolutionDeadline().orElseThrow();

        // Acknowledge at +5m
        f.clock.advance(Duration.ofMinutes(5));
        f.service.acknowledgeIncident(f.technician.getId(), id);

        // Start work at +10m
        f.clock.advance(Duration.ofMinutes(5));
        f.service.startWork(f.technician.getId(), id);

        // Request info from user at +10m
        f.service.requestInfo(new IncidentTextCommand(f.technician.getId(), id, "Please attach client VPN logs"));
        Incident waiting = f.incidents.findById(id).orElseThrow();
        assertEquals(IncidentStatus.WAITING_FOR_USER, waiting.getStatus());
        assertTrue(waiting.getWaitingSince().isPresent());

        // Advance clock by 3 hours (180 minutes). P1 resolution deadline is 30 minutes,
        // but the SLA clock is paused while waiting for user.
        f.clock.advance(Duration.ofHours(3));

        // While waiting, evaluate SLA: resolution should NOT breach because clock is paused
        SlaEvaluation waitingEval = evaluator.evaluate(waiting, f.clock.instant());
        assertNotEquals(SlaStatus.BREACHED, waitingEval.resolution());

        // User responds to info request
        f.service.respondToInfoRequest(new IncidentTextCommand(f.employee.getId(), id, "Uploaded vpn_diag.log"));
        Incident resumed = f.incidents.findById(id).orElseThrow();
        assertEquals(IncidentStatus.IN_PROGRESS, resumed.getStatus());
        assertTrue(resumed.getWaitingSince().isEmpty());
        assertEquals(Duration.ofHours(3), resumed.getPausedDuration());

        // Work for another 20 minutes (total active time = 10m + 20m = 30m; total wall clock = 210m = 3.5h)
        f.clock.advance(Duration.ofMinutes(20));
        Incident resolved = f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), id, "Updated routing table"));
        assertEquals(IncidentStatus.RESOLVED, resolved.getStatus());

        // SlaEvaluation: effective resolution deadline shifted by exactly 3 hours (the paused duration)
        SlaEvaluation resolvedEval = evaluator.evaluate(resolved, f.clock.instant());
        assertEquals(initialResolutionDeadline.plus(Duration.ofHours(3)), resolvedEval.effectiveResolutionDeadline());
        assertEquals(SlaStatus.MET, resolvedEval.response());
        assertEquals(SlaStatus.MET, resolvedEval.resolution());
        assertEquals(SlaStatus.MET, resolvedEval.overall());

        // Verify and close to complete lifecycle
        f.clock.advance(Duration.ofMinutes(5));
        f.service.verifyResolution(f.employee.getId(), id);
        f.clock.advance(Duration.ofMinutes(5));
        Incident closed = f.service.closeIncident(new CloseIncidentCommand(f.admin.getId(), id, "Confirmed resolved"));
        assertEquals(IncidentStatus.CLOSED, closed.getStatus());

        SlaEvaluation finalEval = evaluator.evaluate(closed, f.clock.instant());
        assertEquals(SlaStatus.MET, finalEval.overall());
    }

    @Test
    @DisplayName("lifecycle with reject resolution clears resolution and allows re-resolution to meet SLA")
    void lifecycleWithRejectResolution_clearsResolutionAndAllowsReResolution() {
        CreateIncidentCommand createCmd = new CreateIncidentCommand(
                f.employee.getId(),
                "Email Notification Delay",
                "Emails delayed by 15 minutes",
                f.itService.getId(),
                f.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.CRITICAL,
                Urgency.HIGH,
                50
        );

        Incident incident = f.service.createIncident(createCmd);
        String id = incident.getId();

        // Acknowledge at +5m
        f.clock.advance(Duration.ofMinutes(5));
        f.service.acknowledgeIncident(f.technician.getId(), id);

        // Start work at +10m
        f.clock.advance(Duration.ofMinutes(5));
        f.service.startWork(f.technician.getId(), id);

        // Resolve at +20m (within 30 min P1 resolution target)
        f.clock.advance(Duration.ofMinutes(10));
        f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), id, "Flushed queue"));
        Incident initialResolved = f.incidents.findById(id).orElseThrow();
        assertEquals(IncidentStatus.RESOLVED, initialResolved.getStatus());
        assertTrue(initialResolved.getResolution().isPresent());
        assertTrue(initialResolved.getResolvedAt().isPresent());

        SlaEvaluation initialEval = evaluator.evaluate(initialResolved, f.clock.instant());
        assertEquals(SlaStatus.MET, initialEval.resolution());

        // Reporter rejects resolution at +22m: returns to IN_PROGRESS, resolution cleared
        f.clock.advance(Duration.ofMinutes(2));
        f.listener.clear();
        Incident reopened = f.service.rejectResolution(f.employee.getId(), id);
        assertEquals(IncidentStatus.IN_PROGRESS, reopened.getStatus());
        assertTrue(reopened.getResolution().isEmpty());
        assertTrue(reopened.getResolvedAt().isEmpty());

        // Verify rejection event
        List<DomainEvent> rejectEvents = f.listener.getEvents();
        assertEquals(1, rejectEvents.size());
        assertInstanceOf(IncidentStatusChanged.class, rejectEvents.get(0));
        IncidentStatusChanged rejectEvent = (IncidentStatusChanged) rejectEvents.get(0);
        assertEquals("RESOLUTION_REJECTED", rejectEvent.action());
        assertEquals(IncidentStatus.RESOLVED, rejectEvent.previousStatus());
        assertEquals(IncidentStatus.IN_PROGRESS, rejectEvent.newStatus());

        // Re-resolve at +25m (within 30m P1 target)
        f.clock.advance(Duration.ofMinutes(3));
        Incident reResolved = f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), id, "Fixed worker thread pool and flushed queue"));
        assertEquals(IncidentStatus.RESOLVED, reResolved.getStatus());
        assertTrue(reResolved.getResolution().isPresent());
        assertEquals("Fixed worker thread pool and flushed queue", reResolved.getResolution().get().summary());

        // Verify at +60m
        f.clock.advance(Duration.ofMinutes(5));
        Incident verified = f.service.verifyResolution(f.employee.getId(), id);
        assertEquals(IncidentStatus.VERIFIED, verified.getStatus());

        // Close at +65m
        f.clock.advance(Duration.ofMinutes(5));
        Incident closed = f.service.closeIncident(new CloseIncidentCommand(f.admin.getId(), id, "Confirmed solved after re-resolution"));
        assertEquals(IncidentStatus.CLOSED, closed.getStatus());

        // Final SLA evaluation is MET
        SlaEvaluation finalEval = evaluator.evaluate(closed, f.clock.instant());
        assertEquals(SlaStatus.MET, finalEval.response());
        assertEquals(SlaStatus.MET, finalEval.resolution());
        assertEquals(SlaStatus.MET, finalEval.overall());
    }
}

