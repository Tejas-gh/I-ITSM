package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.iitsm.api.dto.SlaAlertResponse;
import com.iitsm.api.dto.SlaMonitorRunResponse;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.ServiceFixture;
import com.iitsm.application.service.SlaMonitorService;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaType;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaAlertRepository;

class SlaApiControllerTest {

    private ServiceFixture fixture;
    private SlaAlertRepository slaAlerts;
    private SlaMonitorService monitorService;
    private SlaApiController controller;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        baseTime = Instant.parse("2026-11-15T12:00:00Z");
        fixture.clock.set(baseTime);

        slaAlerts = new InMemorySlaAlertRepository();
        monitorService = new SlaMonitorService(
                fixture.incidents,
                slaAlerts,
                new SlaEvaluator(),
                fixture.events,
                fixture.clock
        );

        AdminPermissionPolicy permissionPolicy = new AdminPermissionPolicy();
        controller = new SlaApiController(monitorService, fixture.users, permissionPolicy, fixture.clock);
    }

    private Incident createP1Incident() {
        return fixture.service.createIncident(new com.iitsm.application.command.CreateIncidentCommand(
                fixture.employee.getId(),
                "Server crash",
                "High priority outage",
                fixture.itService.getId(),
                fixture.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.CRITICAL,
                Urgency.CRITICAL,
                1000
        ));
    }

    @Test
    void runMonitorSucceedsForAdmin() {
        createP1Incident();
        // Advance clock past 5 min response deadline to 12:06
        Instant runTime = baseTime.plus(Duration.ofMinutes(6));
        fixture.clock.set(runTime);

        Authentication adminAuth = new UsernamePasswordAuthenticationToken(fixture.admin.getId(), "n/a");
        SlaMonitorRunResponse response = controller.runMonitor(adminAuth);

        assertNotNull(response);
        assertEquals(1, response.scannedCount());
        assertEquals(1, response.violationsRaised());
        assertEquals(0, response.warningsRaised());
        assertEquals(0, response.errorCount());
        assertEquals(runTime, response.ranAt());
    }

    @Test
    void runMonitorThrowsUnauthorizedForEmployee() {
        Authentication empAuth = new UsernamePasswordAuthenticationToken(fixture.employee.getId(), "n/a");

        assertThrows(UnauthorizedOperationException.class, () ->
                controller.runMonitor(empAuth));
    }

    @Test
    void runMonitorThrowsUnauthorizedForTechnician() {
        Authentication techAuth = new UsernamePasswordAuthenticationToken(fixture.technician.getId(), "n/a");

        assertThrows(UnauthorizedOperationException.class, () ->
                controller.runMonitor(techAuth));
    }

    @Test
    void getViolationsReturnsRecordedViolationsAndFiltersByIncidentId() {
        Incident inc1 = createP1Incident();
        Incident inc2 = createP1Incident();

        // Breached response deadline
        monitorService.runMonitor(baseTime.plus(Duration.ofMinutes(6)));

        List<SlaAlertResponse> violations = controller.getViolations(null);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().allMatch(v -> v.kind() == SlaAlertKind.VIOLATION));

        // Filter by inc1
        List<SlaAlertResponse> filtered = controller.getViolations(inc1.getId());
        assertEquals(1, filtered.size());
        assertEquals(inc1.getId(), filtered.get(0).incidentId());
    }

    @Test
    void getAtRiskReturnsRecordedWarningsAndFiltersByIncidentId() {
        Incident inc1 = createP1Incident();
        Incident inc2 = createP1Incident();

        // 4m10s: in warning threshold for response (80% elapsed of 5m target)
        monitorService.runMonitor(baseTime.plus(Duration.ofMinutes(4)).plusSeconds(10));

        List<SlaAlertResponse> atRisk = controller.getAtRisk(null);
        assertEquals(2, atRisk.size());
        assertTrue(atRisk.stream().allMatch(v -> v.kind() == SlaAlertKind.WARNING));

        // Filter by inc2
        List<SlaAlertResponse> filtered = controller.getAtRisk(inc2.getId());
        assertEquals(1, filtered.size());
        assertEquals(inc2.getId(), filtered.get(0).incidentId());
    }
}
