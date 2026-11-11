package com.iitsm.application.report;

import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.service.ServiceFixture;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {

    private ServiceFixture f;
    private DashboardService dashboardService;
    private Incident inc1;
    private Incident inc2;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
        dashboardService = new DashboardService(
                f.incidents,
                f.users,
                f.services,
                new com.iitsm.domain.sla.SlaEvaluator(),
                f.clock
        );

        // Assign tech1 to inc1 (P1)
        f.assignmentStrategy.setDesignatedTechnician(f.technician);
        inc1 = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(), "Incident P1", "High severity outage",
                f.itService.getId(), f.category.getId(), null,
                CategorySource.MANUAL, Impact.CRITICAL, Urgency.CRITICAL, 150
        ));

        // Assign tech2 to inc2 (P3)
        f.assignmentStrategy.setDesignatedTechnician(f.technician2);
        inc2 = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(), "Incident P3", "Minor glitch",
                f.itService.getId(), f.category.getId(), null,
                CategorySource.MANUAL, Impact.MEDIUM, Urgency.MEDIUM, 1
        ));
    }

    @Test
    @DisplayName("Admin can get incident overview metrics matching seeded data")
    void getOverview_adminSuccess() {
        IncidentOverview overview = dashboardService.getOverview(f.admin.getId());

        assertNotNull(overview);
        assertEquals(2, overview.totalIncidents());
        assertEquals(2, overview.openIncidents());
        assertEquals(1, overview.criticalIncidents());
        assertEquals(0, overview.resolvedIncidents());
        assertEquals(0, overview.breachedIncidents());

        // Advance clock to trigger SLA breach for P1 (30 min resolution target)
        f.clock.advance(Duration.ofHours(1));
        IncidentOverview breachedOverview = dashboardService.getOverview(f.admin.getId());
        assertEquals(1, breachedOverview.breachedIncidents());
    }

    @Test
    @DisplayName("Technician workloads report returns active counts and capacity utilization")
    void getTechnicianWorkloads_matchesActiveWorkload() {
        List<TechnicianWorkloadEntry> workloads = dashboardService.getTechnicianWorkloads(f.admin.getId());

        assertEquals(2, workloads.size());

        TechnicianWorkloadEntry tech1Entry = workloads.stream()
                .filter(w -> w.technicianId().equals(f.technician.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, tech1Entry.activeIncidents());
        assertEquals(5, tech1Entry.maxActiveIncidents());
        assertEquals(0.2, tech1Entry.capacityUtilization(), 0.001);
    }

    @Test
    @DisplayName("Service incident counts report counts incidents per service")
    void getServiceIncidentCounts_matchesSeededData() {
        List<ServiceIncidentCount> counts = dashboardService.getServiceIncidentCounts(f.admin.getId());

        assertFalse(counts.isEmpty());
        ServiceIncidentCount paymentCount = counts.stream()
                .filter(c -> c.serviceId().equals(f.itService.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, paymentCount.incidentCount());
    }

    @Test
    @DisplayName("Non-admin role throws UnauthorizedOperationException")
    void nonAdmin_accessDenied() {
        assertThrows(UnauthorizedOperationException.class, () ->
                dashboardService.getOverview(f.employee.getId()));

        assertThrows(UnauthorizedOperationException.class, () ->
                dashboardService.getTechnicianWorkloads(f.technician.getId()));

        assertThrows(UnauthorizedOperationException.class, () ->
                dashboardService.getServiceIncidentCounts(f.employee.getId()));
    }

    @Test
    @DisplayName("Invalid and inactive actor inputs are rejected")
    void actorValidation() {
        assertThrows(InvalidInputException.class, () ->
                dashboardService.getOverview("  "));

        assertThrows(UserNotFoundException.class, () ->
                dashboardService.getOverview("NON_EXISTENT_ADMIN"));

        f.admin.deactivate();
        assertThrows(UnauthorizedOperationException.class, () ->
                dashboardService.getOverview(f.admin.getId()));
    }
}
