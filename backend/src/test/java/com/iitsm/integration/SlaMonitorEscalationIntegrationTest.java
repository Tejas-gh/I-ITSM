package com.iitsm.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.application.service.EscalationService;
import com.iitsm.application.service.ServiceFixture;
import com.iitsm.application.service.SlaMonitorResult;
import com.iitsm.application.service.SlaMonitorService;
import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.infrastructure.persistence.memory.InMemoryEscalationRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaAlertRepository;
import com.iitsm.support.TestData;

import static org.junit.jupiter.api.Assertions.*;

class SlaMonitorEscalationIntegrationTest {

    private ServiceFixture fixture;
    private InMemoryEscalationRepository escalationRepository;
    private SlaAlertRepository slaAlerts;
    private EscalationService escalationService;
    private SlaMonitorService monitorService;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        baseTime = Instant.parse("2026-11-15T10:00:00Z");
        fixture.clock.set(baseTime);

        escalationRepository = new InMemoryEscalationRepository();
        slaAlerts = new InMemorySlaAlertRepository();

        escalationService = new EscalationService(
                fixture.incidents,
                escalationRepository,
                fixture.events,
                fixture.clock
        );

        monitorService = new SlaMonitorService(
                fixture.incidents,
                slaAlerts,
                new com.iitsm.application.service.SlaIncidentProcessor(slaAlerts, new SlaEvaluator(), fixture.events),
                escalationService,
                fixture.clock
        );
    }

    @Test
    @DisplayName("SLA Monitor tick automatically escalates unacknowledged P1 incident when response deadline is reached")
    void monitorTick_automaticallyEscalatesP1() {
        // Create unacknowledged P1 incident with 15 min response target
        IncidentDraft draft = TestData.draft(
                TestData.employee("EMP01"),
                TestData.service("SVC01", BusinessCriticality.CRITICAL),
                TestData.category("CAT01"),
                Impact.CRITICAL,
                Urgency.HIGH,
                1
        );
        PriorityResult priority = new PriorityResult(95, PriorityLevel.P1);
        Incident inc = new Incident("INC-PAYMENT", draft, priority, baseTime);
        Instant responseDeadline = baseTime.plus(Duration.ofMinutes(15));
        inc.applySlaDeadlines(responseDeadline, baseTime.plus(Duration.ofHours(2)));
        fixture.incidents.save(inc);

        // Before threshold: monitor runs, no escalation
        fixture.clock.set(baseTime.plus(Duration.ofMinutes(10)));
        SlaMonitorResult res1 = monitorService.runMonitor();
        assertEquals(1, res1.scannedCount());
        assertTrue(escalationRepository.findAll().isEmpty());

        // At response deadline threshold: monitor runs -> triggers Level 1 escalation!
        fixture.clock.set(responseDeadline);
        SlaMonitorResult res2 = monitorService.runMonitor();
        assertEquals(1, res2.scannedCount());

        List<EscalationRecord> escalations = escalationRepository.findByIncidentId("INC-PAYMENT");
        assertEquals(1, escalations.size());
        assertEquals(1, escalations.get(0).level());
        assertEquals(Role.TEAM_LEAD, escalations.get(0).targetRole());

        // At response deadline + 1 target (10:30): monitor runs -> triggers Level 2 escalation!
        fixture.clock.set(responseDeadline.plus(Duration.ofMinutes(15)));
        monitorService.runMonitor();

        List<EscalationRecord> escalationsL2 = escalationRepository.findByIncidentId("INC-PAYMENT");
        assertEquals(2, escalationsL2.size());
        assertEquals(2, escalationsL2.get(1).level());
        assertEquals(Role.ADMINISTRATOR, escalationsL2.get(1).targetRole());
    }
}
