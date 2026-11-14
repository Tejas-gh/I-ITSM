package com.iitsm.integration;

import com.iitsm.application.command.AssignIncidentCommand;
import com.iitsm.application.command.CloseIncidentCommand;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.command.IncidentTextCommand;
import com.iitsm.application.command.SubmitFeedbackCommand;
import com.iitsm.application.service.AuditQueryService;
import com.iitsm.application.service.IncidentService;
import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate B7: End-to-end integration test proving the audit trail through the full incident lifecycle.
 * Verifies that all 7 core audit fields are populated into the database for each lifecycle event.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:b7-audit-trail;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext
@Transactional
public class AuditTrailIntegrationTest {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private AuditQueryService auditQueryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItServiceRepository itServiceRepository;

    private static final String EMP_ID = "EMP_AUDIT_1";
    private static final String TECH_ID = "TECH_AUDIT_1";
    private static final String ADMIN_ID = "ADMIN_AUDIT_1";
    private static final String SERVICE_ID = "SVC_AUDIT_NET";

    private Technician technician;

    @BeforeEach
    void setUp() {
        Employee employee = TestData.employee(EMP_ID);
        technician = TestData.technician(TECH_ID, 10);
        Administrator admin = TestData.admin(ADMIN_ID);
        ItService service = TestData.service(SERVICE_ID, BusinessCriticality.HIGH);

        userRepository.save(employee);
        userRepository.save(technician);
        userRepository.save(admin);
        itServiceRepository.save(service);
    }

    @Test
    void fullIncidentLifecycle_generatesCompleteAuditTrail() {
        // Temporarily set technician unavailable to prevent auto-assign during creation,
        // allowing manual assignment to be tested cleanly in the lifecycle
        technician.markUnavailable();
        userRepository.save(technician);

        // 1. Create incident -> status is CLASSIFIED
        CreateIncidentCommand createCmd = new CreateIncidentCommand(
                EMP_ID,
                "VPN gateway unresponsive",
                "Users in Building A cannot connect",
                SERVICE_ID,
                "NETWORK",
                null,
                CategorySource.MANUAL,
                Impact.HIGH,
                Urgency.HIGH,
                25
        );
        Incident incident = incidentService.createIncident(createCmd);
        String incId = incident.getId();
        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());

        // Make technician available again and manually assign
        technician.markAvailable();
        userRepository.save(technician);

        // 2. Assign incident
        AssignIncidentCommand assignCmd = new AssignIncidentCommand(ADMIN_ID, incId, TECH_ID);
        incident = incidentService.assignIncident(assignCmd);
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());

        // 3. Acknowledge incident
        incident = incidentService.acknowledgeIncident(TECH_ID, incId);
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());

        // 4. Start work
        incident = incidentService.startWork(TECH_ID, incId);
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());

        // 5. Add investigation note
        incident = incidentService.addInvestigationNote(
                new IncidentTextCommand(TECH_ID, incId, "Inspecting gateway routes"));

        // 6. Request info
        incident = incidentService.requestInfo(
                new IncidentTextCommand(TECH_ID, incId, "Are you using ethernet or wifi?"));
        assertEquals(IncidentStatus.WAITING_FOR_USER, incident.getStatus());

        // 7. Respond info
        incident = incidentService.respondToInfoRequest(
                new IncidentTextCommand(EMP_ID, incId, "Using corporate wifi"));
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());

        // 8. Resolve incident
        incident = incidentService.resolveIncident(
                new IncidentTextCommand(TECH_ID, incId, "Restarted gateway routing daemon"));
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());

        // 9. Verify resolution
        incident = incidentService.verifyResolution(EMP_ID, incId);
        assertEquals(IncidentStatus.VERIFIED, incident.getStatus());

        // 10. Submit feedback
        incident = incidentService.submitFeedback(
                new SubmitFeedbackCommand(EMP_ID, incId, 5, "Resolved very quickly!"));

        // 11. Close incident
        incident = incidentService.closeIncident(
                new CloseIncidentCommand(ADMIN_ID, incId, "Closure confirmed by IT Ops"));
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());

        // Query audit trail directly and via admin query service
        List<AuditEntry> repoEntries = auditRepository.findByIncidentId(incId);
        List<AuditEntry> queryEntries = auditQueryService.findByIncidentId(ADMIN_ID, incId);

        assertEquals(repoEntries.size(), queryEntries.size(),
                "Direct repository entries and AuditQueryService entries must match");
        assertTrue(repoEntries.size() >= 11,
                "Expected at least 11 audit records for the full lifecycle, found: " + repoEntries.size());

        // Validate that all 7 core fields are present and valid across all entries
        for (AuditEntry entry : repoEntries) {
            assertNotNull(entry.occurredAt(), "occurredAt must not be null");
            assertNotNull(entry.actorId(), "actorId must not be null");
            assertFalse(entry.actorId().isBlank(), "actorId must not be blank");
            assertEquals(incId, entry.incidentId(), "incidentId must match");
            if (!"INCIDENT_CREATED".equals(entry.action())) {
                assertNotNull(entry.previousStatus(), "previousStatus must not be null for action: " + entry.action());
            }
            assertNotNull(entry.newStatus(), "newStatus must not be null");
            assertNotNull(entry.action(), "action must not be null");
            assertFalse(entry.action().isBlank(), "action must not be blank");
            assertNotNull(entry.description(), "description must not be null");
        }

        // Verify first and last transitions
        AuditEntry first = repoEntries.get(0);
        assertEquals("INCIDENT_CREATED", first.action());
        assertEquals(EMP_ID, first.actorId());
        assertNull(first.previousStatus());
        assertEquals(IncidentStatus.NEW, first.newStatus());

        AuditEntry last = repoEntries.get(repoEntries.size() - 1);
        assertEquals("INCIDENT_CLOSED", last.action());
        assertEquals(ADMIN_ID, last.actorId());
        assertEquals(IncidentStatus.CLOSED, last.newStatus());

        // Query all audit entries as Admin
        List<AuditEntry> allEntries = auditQueryService.findAll(ADMIN_ID);
        assertTrue(allEntries.size() >= repoEntries.size());
    }
}
