package com.iitsm.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.iitsm.api.dto.EscalationResponse;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.EscalationService;
import com.iitsm.application.service.ServiceFixture;
import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.infrastructure.persistence.memory.InMemoryEscalationRepository;
import com.iitsm.support.TestData;

import static org.junit.jupiter.api.Assertions.*;

class EscalationControllerTest {

    private ServiceFixture fixture;
    private InMemoryEscalationRepository escalationRepository;
    private EscalationService escalationService;
    private EscalationController controller;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        baseTime = Instant.parse("2026-11-15T12:00:00Z");
        fixture.clock.set(baseTime);

        escalationRepository = new InMemoryEscalationRepository();
        escalationService = new EscalationService(
                fixture.incidents,
                escalationRepository,
                fixture.events,
                fixture.clock
        );

        AdminPermissionPolicy permissionPolicy = new AdminPermissionPolicy();
        controller = new EscalationController(escalationService, fixture.users, permissionPolicy);
    }

    private Incident createP1Incident(String id) {
        IncidentDraft draft = TestData.draft(
                TestData.employee("EMP01"),
                TestData.service("SVC01", BusinessCriticality.CRITICAL),
                TestData.category("CAT01"),
                Impact.CRITICAL,
                Urgency.HIGH,
                1
        );
        PriorityResult priority = new PriorityResult(90, PriorityLevel.P1);
        Incident inc = new Incident(id, draft, priority, baseTime);
        inc.applySlaDeadlines(baseTime.plus(Duration.ofMinutes(15)), baseTime.plus(Duration.ofHours(2)));
        fixture.incidents.save(inc);
        return inc;
    }

    @Test
    @DisplayName("GET /api/incidents/{id}/escalations returns escalation history")
    void getIncidentEscalations() {
        escalationRepository.save(EscalationRecord.create("INC-101", 1, Role.TEAM_LEAD, baseTime, "Level 1"));

        List<EscalationResponse> res = controller.getIncidentEscalations("INC-101");
        assertEquals(1, res.size());
        assertEquals(1, res.get(0).level());
        assertEquals(Role.TEAM_LEAD, res.get(0).targetRole());
    }

    @Test
    @DisplayName("GET /api/escalations returns all recorded escalations")
    void getAllEscalations() {
        escalationRepository.save(EscalationRecord.create("INC-101", 1, Role.TEAM_LEAD, baseTime, "Level 1"));
        escalationRepository.save(EscalationRecord.create("INC-102", 2, Role.ADMINISTRATOR, baseTime, "Level 2"));

        List<EscalationResponse> res = controller.getAllEscalations();
        assertEquals(2, res.size());
    }

    @Test
    @DisplayName("POST /api/escalations/run by ADMIN executes evaluation")
    void runEscalations_asAdmin_succeeds() {
        createP1Incident("INC-101");
        // Advance clock to response deadline
        fixture.clock.set(baseTime.plus(Duration.ofMinutes(15)));

        Authentication adminAuth = new UsernamePasswordAuthenticationToken("ADMIN01", null, List.of());
        List<EscalationResponse> res = controller.runEscalations(adminAuth);

        assertEquals(1, res.size());
        assertEquals(1, res.get(0).level());
    }

    @Test
    @DisplayName("POST /api/escalations/run by non-admin is rejected with UnauthorizedOperationException")
    void runEscalations_asNonAdmin_throwsUnauthorized() {
        Authentication empAuth = new UsernamePasswordAuthenticationToken("EMP01", null, List.of());
        assertThrows(UnauthorizedOperationException.class, () -> controller.runEscalations(empAuth));
    }
}
