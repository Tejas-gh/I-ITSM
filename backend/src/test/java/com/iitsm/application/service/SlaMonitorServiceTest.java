package com.iitsm.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.event.SlaViolated;
import com.iitsm.domain.event.SlaWarning;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaType;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaAlertRepository;

class SlaMonitorServiceTest {

    private ServiceFixture fixture;
    private SlaAlertRepository slaAlerts;
    private SlaEvaluator slaEvaluator;
    private SlaMonitorService monitorService;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        baseTime = Instant.parse("2026-11-15T10:00:00Z");
        fixture.clock.set(baseTime);

        slaAlerts = new InMemorySlaAlertRepository();
        slaEvaluator = new SlaEvaluator();

        monitorService = new SlaMonitorService(
                fixture.incidents,
                slaAlerts,
                slaEvaluator,
                fixture.events,
                fixture.clock
        );

        fixture.listener.clear();
    }

    private Incident createP1Incident() {
        // P1: Response target = 5 min (deadline 10:05), Resolution target = 30 min (deadline 10:30)
        return fixture.service.createIncident(new com.iitsm.application.command.CreateIncidentCommand(
                fixture.employee.getId(),
                "Critical Outage",
                "Payment gateway down",
                fixture.itService.getId(),
                fixture.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.CRITICAL,
                Urgency.CRITICAL,
                500
        ));
    }

    @Test
    void warningGeneratedOnceWhenEnteringWarningThreshold() {
        Incident inc = createP1Incident();
        fixture.listener.clear();

        // P1 response target is 5m, warning threshold is 80% elapsed (4m after baseTime -> 10:04:00Z)
        Instant warnTime = baseTime.plus(Duration.ofMinutes(4)).plusSeconds(10);
        fixture.clock.set(warnTime);

        SlaMonitorResult result = monitorService.runMonitor(warnTime);

        assertEquals(1, result.scannedCount());
        assertEquals(1, result.warningsRaised());
        assertEquals(0, result.violationsRaised());
        assertEquals(0, result.errorCount());

        // Verify alert saved in repository
        assertTrue(slaAlerts.exists(inc.getId(), SlaType.RESPONSE, SlaAlertKind.WARNING));
        assertFalse(slaAlerts.exists(inc.getId(), SlaType.RESPONSE, SlaAlertKind.VIOLATION));

        // Verify domain event published
        List<SlaWarning> warningEvents = fixture.listener.getEventsOfType(SlaWarning.class);
        assertEquals(1, warningEvents.size());
        SlaWarning event = warningEvents.get(0);
        assertEquals(inc.getId(), event.incidentId());
        assertEquals(SlaIncidentProcessor.SYSTEM_ACTOR_ID, event.actorId());
        assertEquals(SlaType.RESPONSE, event.slaType());
        assertEquals(inc.getStatus(), event.previousStatus());
        assertEquals(inc.getStatus(), event.newStatus());
        assertEquals(baseTime.plus(Duration.ofMinutes(5)), event.deadline());
    }

    @Test
    void violationGeneratedOnceWhenPastDeadline() {
        Incident inc = createP1Incident();
        fixture.listener.clear();

        // Move time past response deadline (10:05) -> 10:06:00Z
        Instant breachTime = baseTime.plus(Duration.ofMinutes(6));
        fixture.clock.set(breachTime);

        SlaMonitorResult result = monitorService.runMonitor(breachTime);

        assertEquals(1, result.scannedCount());
        assertEquals(0, result.warningsRaised());
        assertEquals(1, result.violationsRaised());

        // Verify violation saved in repository
        assertTrue(slaAlerts.exists(inc.getId(), SlaType.RESPONSE, SlaAlertKind.VIOLATION));

        // Verify domain event published
        List<SlaViolated> violationEvents = fixture.listener.getEventsOfType(SlaViolated.class);
        assertEquals(1, violationEvents.size());
        SlaViolated event = violationEvents.get(0);
        assertEquals(inc.getId(), event.incidentId());
        assertEquals(SlaIncidentProcessor.SYSTEM_ACTOR_ID, event.actorId());
        assertEquals(SlaType.RESPONSE, event.slaType());
    }

    @Test
    void secondTickDoesNotDuplicateAlertsOrEvents() {
        Incident inc = createP1Incident();
        fixture.listener.clear();

        // 1st tick: Warning threshold
        Instant warnTime = baseTime.plus(Duration.ofMinutes(4)).plusSeconds(10);
        SlaMonitorResult res1 = monitorService.runMonitor(warnTime);
        assertEquals(1, res1.warningsRaised());
        assertEquals(1, fixture.listener.getEventsOfType(SlaWarning.class).size());

        // 2nd tick at same time: must NOT re-raise or duplicate
        SlaMonitorResult res2 = monitorService.runMonitor(warnTime);
        assertEquals(0, res2.warningsRaised());
        assertEquals(0, res2.violationsRaised());
        assertEquals(1, fixture.listener.getEventsOfType(SlaWarning.class).size()); // still only 1 event

        // 3rd tick: Deadline breached
        Instant breachTime = baseTime.plus(Duration.ofMinutes(6));
        SlaMonitorResult res3 = monitorService.runMonitor(breachTime);
        assertEquals(1, res3.violationsRaised());
        assertEquals(1, fixture.listener.getEventsOfType(SlaViolated.class).size());

        // 4th tick: Repeated run past deadline must NOT duplicate violation
        SlaMonitorResult res4 = monitorService.runMonitor(breachTime.plus(Duration.ofMinutes(1)));
        assertEquals(0, res4.violationsRaised());
        assertEquals(1, fixture.listener.getEventsOfType(SlaViolated.class).size()); // still only 1 violation event
    }

    @Test
    void pausedIncidentDoesNotIncorrectlyBreach() {
        Incident inc = createP1Incident();
        String techId = inc.getAssignedTechnician().map(Technician::getId).orElse(fixture.technician.getId());

        // Acknowledge within SLA (at 2 minutes)
        Instant ackTime = baseTime.plus(Duration.ofMinutes(2));
        fixture.clock.set(ackTime);
        fixture.service.acknowledgeIncident(techId, inc.getId());
        fixture.service.startWork(techId, inc.getId());

        // Request info at 10 minutes -> pauses resolution SLA clock (waiting for user)
        Instant pauseTime = baseTime.plus(Duration.ofMinutes(10));
        fixture.clock.set(pauseTime);
        fixture.service.requestInfo(new com.iitsm.application.command.IncidentTextCommand(
                techId, inc.getId(), "Need more logs"));

        // Advance clock to 10:35:00Z (35 min after creation).
        // Original resolution deadline was 10:30 (30m target).
        // But incident has been waiting since 10:10 (25 min paused).
        // Effective resolution deadline is 10:30 + 25m = 10:55:00Z.
        Instant evalTime = baseTime.plus(Duration.ofMinutes(35));
        fixture.clock.set(evalTime);
        fixture.listener.clear();

        SlaMonitorResult result = monitorService.runMonitor(evalTime);

        // Resolution SLA is paused; effective deadline has shifted, so no violation has occurred!
        assertEquals(0, result.violationsRaised());
        assertFalse(slaAlerts.exists(inc.getId(), SlaType.RESOLUTION, SlaAlertKind.VIOLATION));
        assertEquals(0, fixture.listener.getEventsOfType(SlaViolated.class).size());
    }

    @Test
    void closedIncidentSkipped() {
        Incident inc = createP1Incident();
        String techId = inc.getAssignedTechnician().map(Technician::getId).orElse(fixture.technician.getId());

        fixture.service.acknowledgeIncident(techId, inc.getId());
        fixture.service.startWork(techId, inc.getId());
        fixture.service.resolveIncident(new com.iitsm.application.command.IncidentTextCommand(
                techId, inc.getId(), "Resolved"));
        fixture.service.verifyResolution(fixture.employee.getId(), inc.getId());
        fixture.service.closeIncident(new com.iitsm.application.command.CloseIncidentCommand(
                fixture.admin.getId(), inc.getId(), "Closing"));

        // Advance time way past original deadline
        Instant farFuture = baseTime.plus(Duration.ofDays(2));
        fixture.clock.set(farFuture);
        fixture.listener.clear();

        SlaMonitorResult result = monitorService.runMonitor(farFuture);

        assertEquals(0, result.scannedCount());
        assertEquals(0, result.warningsRaised());
        assertEquals(0, result.violationsRaised());
        assertTrue(fixture.listener.getEvents().isEmpty());
    }

    @Test
    void perIncidentFailureIsolated() {
        Incident inc1 = createP1Incident();
        Incident inc2 = createP1Incident();
        Incident inc3 = createP1Incident();

        // Breached time
        Instant breachTime = baseTime.plus(Duration.ofMinutes(6));

        // Create a custom processor that fails on inc2
        SlaIncidentProcessor failingProcessor = new SlaIncidentProcessor(slaAlerts, slaEvaluator, fixture.events) {
            @Override
            public IncidentSlaResult processIncident(Incident incident, Instant now) {
                if (incident.getId().equals(inc2.getId())) {
                    throw new RuntimeException("Simulated failure on inc2");
                }
                return super.processIncident(incident, now);
            }
        };

        SlaMonitorService resilientMonitor = new SlaMonitorService(
                fixture.incidents, slaAlerts, failingProcessor, fixture.clock);

        SlaMonitorResult result = resilientMonitor.runMonitor(breachTime);

        assertEquals(3, result.scannedCount());
        assertEquals(2, result.violationsRaised()); // inc1 and inc3 succeeded
        assertEquals(1, result.errorCount());       // inc2 failed safely

        assertTrue(slaAlerts.exists(inc1.getId(), SlaType.RESPONSE, SlaAlertKind.VIOLATION));
        assertFalse(slaAlerts.exists(inc2.getId(), SlaType.RESPONSE, SlaAlertKind.VIOLATION));
        assertTrue(slaAlerts.exists(inc3.getId(), SlaType.RESPONSE, SlaAlertKind.VIOLATION));
    }

    @Test
    void lateViolationRecordedForResolvedIncident() {
        Incident inc = createP1Incident();
        String techId = inc.getAssignedTechnician().map(Technician::getId).orElse(fixture.technician.getId());

        // Acknowledge on time (at 2 minutes)
        fixture.clock.set(baseTime.plus(Duration.ofMinutes(2)));
        fixture.service.acknowledgeIncident(techId, inc.getId());
        fixture.service.startWork(techId, inc.getId());

        // Resolve late: resolution target is 30m, resolve at 45m (10:45)
        Instant lateResolveTime = baseTime.plus(Duration.ofMinutes(45));
        fixture.clock.set(lateResolveTime);
        fixture.service.resolveIncident(new com.iitsm.application.command.IncidentTextCommand(
                techId, inc.getId(), "Resolved late"));

        // Incident is in RESOLVED status (not CLOSED).
        // SlaEvaluator will detect that resolution was done after effectiveResolutionDeadline.
        fixture.listener.clear();
        SlaMonitorResult result = monitorService.runMonitor(lateResolveTime);

        assertEquals(1, result.violationsRaised());
        assertTrue(slaAlerts.exists(inc.getId(), SlaType.RESOLUTION, SlaAlertKind.VIOLATION));

        List<SlaViolated> violations = fixture.listener.getEventsOfType(SlaViolated.class);
        assertEquals(1, violations.size());
        assertEquals(SlaType.RESOLUTION, violations.get(0).slaType());
        assertEquals(IncidentStatus.RESOLVED, violations.get(0).previousStatus());
    }

    @Test
    void queryMethodsReturnExpectedFilteredAlerts() {
        Incident inc = createP1Incident();

        // 1. Generate warning at 4m10s
        monitorService.runMonitor(baseTime.plus(Duration.ofMinutes(4)).plusSeconds(10));
        assertEquals(1, monitorService.getAtRiskAlerts().size());
        assertEquals(0, monitorService.getViolations().size());

        // 2. Generate violation at 6m
        monitorService.runMonitor(baseTime.plus(Duration.ofMinutes(6)));
        assertEquals(1, monitorService.getAtRiskAlerts().size());
        assertEquals(1, monitorService.getViolations().size());
        assertEquals(2, monitorService.getAllAlerts().size());
        assertEquals(2, monitorService.getAlertsForIncident(inc.getId()).size());
    }
}
