package com.iitsm.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventListener;
import com.iitsm.domain.event.IncidentEscalated;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;
import com.iitsm.infrastructure.persistence.memory.InMemoryEscalationRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.support.TestData;

import static org.junit.jupiter.api.Assertions.*;

class EscalationServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");
    private static final Duration TARGET = Duration.ofMinutes(15);
    private static final Instant T1 = T0.plus(TARGET); // 10:15
    private static final Instant T2 = T1.plus(TARGET); // 10:30
    private static final Instant T3 = T2.plus(TARGET); // 10:45

    private InMemoryIncidentRepository incidentRepository;
    private InMemoryEscalationRepository escalationRepository;
    private InMemoryDomainEventPublisher eventPublisher;
    private List<DomainEvent> publishedEvents;
    private EscalationService service;

    @BeforeEach
    void setUp() {
        incidentRepository = new InMemoryIncidentRepository();
        escalationRepository = new InMemoryEscalationRepository();
        eventPublisher = new InMemoryDomainEventPublisher();
        publishedEvents = new ArrayList<>();
        eventPublisher.subscribe(new DomainEventListener() {
            @Override
            public void onEvent(DomainEvent event) {
                publishedEvents.add(event);
            }
            @Override
            public boolean critical() {
                return false;
            }
        });

        Clock clock = Clock.fixed(T1, ZoneOffset.UTC);
        service = new EscalationService(incidentRepository, escalationRepository, eventPublisher, clock);
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
        PriorityResult priority = new PriorityResult(95, PriorityLevel.P1);
        Incident incident = new Incident(id, draft, priority, T0);
        incident.applySlaDeadlines(T1, T0.plus(Duration.ofHours(2)));
        incidentRepository.save(incident);
        return incident;
    }

    @Test
    @DisplayName("P1 unacknowledged incident escalates to Level 1 at response deadline")
    void p1Unacknowledged_escalatesToLevel1() {
        Incident inc = createP1Incident("INC-101");

        Optional<EscalationRecord> record = service.evaluateIncident(inc, T1);

        assertTrue(record.isPresent());
        assertEquals(1, record.get().level());
        assertEquals(Role.TEAM_LEAD, record.get().targetRole());
        assertEquals("INC-101", record.get().incidentId());

        // Verify repository
        assertTrue(escalationRepository.existsByIncidentIdAndLevel("INC-101", 1));

        // Verify domain event
        assertEquals(1, publishedEvents.size());
        DomainEvent ev = publishedEvents.get(0);
        assertInstanceOf(IncidentEscalated.class, ev);
        IncidentEscalated escalated = (IncidentEscalated) ev;
        assertEquals(1, escalated.escalationLevel());
        assertEquals(Role.TEAM_LEAD, escalated.targetRole());
    }

    @Test
    @DisplayName("Subsequent evaluation at T2 escalates to Level 2 (Administrator)")
    void subsequentEvaluation_escalatesToLevel2() {
        Incident inc = createP1Incident("INC-101");

        // First pass at T1
        service.evaluateIncident(inc, T1);
        assertEquals(1, escalationRepository.findByIncidentId("INC-101").size());

        // Second pass at T2 (+1 target duration)
        Optional<EscalationRecord> record = service.evaluateIncident(inc, T2);

        assertTrue(record.isPresent());
        assertEquals(2, record.get().level());
        assertEquals(Role.ADMINISTRATOR, record.get().targetRole());
        assertEquals(2, escalationRepository.findByIncidentId("INC-101").size());
        assertEquals(2, publishedEvents.size());
    }

    @Test
    @DisplayName("Subsequent evaluation at T3 escalates to Level 3 (IT Manager)")
    void subsequentEvaluation_escalatesToLevel3() {
        Incident inc = createP1Incident("INC-101");

        // Pass 1 & 2
        service.evaluateIncident(inc, T1);
        service.evaluateIncident(inc, T2);

        // Third pass at T3 (+2 target duration)
        Optional<EscalationRecord> record = service.evaluateIncident(inc, T3);

        assertTrue(record.isPresent());
        assertEquals(3, record.get().level());
        assertEquals(Role.IT_MANAGER, record.get().targetRole());
        assertEquals(3, escalationRepository.findByIncidentId("INC-101").size());

        // Fourth pass after T3: no further escalation
        Optional<EscalationRecord> pass4 = service.evaluateIncident(inc, T3.plus(Duration.ofHours(1)));
        assertTrue(pass4.isEmpty());
    }

    @Test
    @DisplayName("Duplicate tick does not create duplicate escalation")
    void duplicateTick_idempotent() {
        Incident inc = createP1Incident("INC-101");

        Optional<EscalationRecord> r1 = service.evaluateIncident(inc, T1);
        Optional<EscalationRecord> r2 = service.evaluateIncident(inc, T1);

        assertTrue(r1.isPresent());
        assertTrue(r2.isEmpty());
        assertEquals(1, escalationRepository.findByIncidentId("INC-101").size());
    }

    @Test
    @DisplayName("Non-P1 incidents are ignored")
    void nonP1_ignored() {
        IncidentDraft draft = TestData.draft(
                TestData.employee("EMP01"),
                TestData.service("SVC01", BusinessCriticality.LOW),
                TestData.category("CAT01"),
                Impact.LOW,
                Urgency.LOW,
                1
        );
        PriorityResult priority = new PriorityResult(30, PriorityLevel.P3);
        Incident incident = new Incident("INC-P3", draft, priority, T0);
        incident.applySlaDeadlines(T1, T0.plus(Duration.ofHours(4)));
        incidentRepository.save(incident);

        Optional<EscalationRecord> record = service.evaluateIncident(incident, T1);
        assertTrue(record.isEmpty());
        assertEquals(0, escalationRepository.findAll().size());
    }

    @Test
    @DisplayName("Acknowledged incident is ignored")
    void acknowledged_ignored() {
        Incident inc = createP1Incident("INC-ACK");
        inc.classify();
        Technician tech = TestData.technician("TECH01", 5);
        inc.assign(tech, T0.plus(Duration.ofMinutes(2)));
        inc.acknowledge(T0.plus(Duration.ofMinutes(5)));

        Optional<EscalationRecord> record = service.evaluateIncident(inc, T1);
        assertTrue(record.isEmpty());
        assertEquals(0, escalationRepository.findAll().size());
    }

    @Test
    @DisplayName("Closed incident is ignored")
    void closed_ignored() {
        Incident inc = createP1Incident("INC-CLOSED");
        inc.classify();
        Technician tech = TestData.technician("TECH01", 5);
        inc.assign(tech, T0.plus(Duration.ofMinutes(2)));
        inc.acknowledge(T0.plus(Duration.ofMinutes(5)));
        inc.startWork();
        inc.resolve("TECH01", "Fixed", T0.plus(Duration.ofMinutes(10)));
        inc.verify(T0.plus(Duration.ofMinutes(12)));
        inc.close("ADMIN01", "Closed", T0.plus(Duration.ofMinutes(14)));

        Optional<EscalationRecord> record = service.evaluateIncident(inc, T1.plus(Duration.ofHours(5)));
        assertTrue(record.isEmpty());
        assertEquals(0, escalationRepository.findAll().size());
    }

    @Test
    @DisplayName("runEscalations evaluates all open incidents")
    void runEscalations_allIncidents() {
        createP1Incident("INC-1");
        createP1Incident("INC-2");

        List<EscalationRecord> results = service.runEscalations(T1);

        assertEquals(2, results.size());
        assertEquals(2, escalationRepository.findAll().size());
    }
}
