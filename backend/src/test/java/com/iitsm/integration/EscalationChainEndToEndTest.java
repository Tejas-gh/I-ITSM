package com.iitsm.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.application.listener.AuditEventListener;
import com.iitsm.application.listener.NotificationEventListener;
import com.iitsm.application.notification.InAppNotificationChannel;
import com.iitsm.application.notification.NotificationRecipientResolver;
import com.iitsm.application.service.EscalationService;
import com.iitsm.application.service.ServiceFixture;
import com.iitsm.application.service.SlaIncidentProcessor;
import com.iitsm.application.service.SlaMonitorService;
import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationFactory;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.infrastructure.persistence.memory.InMemoryAuditRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryEscalationRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryNotificationRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaAlertRepository;
import com.iitsm.support.TestData;

/**
 * Automatic P1 escalation through the whole chain, driven only by SLA monitor ticks: monitor -> escalation service ->
 * escalation repository -> IncidentEscalated event -> audit listener + in-app notification listener (real recipient
 * resolver). Complements SlaMonitorEscalationIntegrationTest (Level 1 and 2 records only).
 */
class EscalationChainEndToEndTest {

    private static final String ESCALATION_TITLE = "P1 Incident Escalated";
    private static final String ESCALATION_ACTION = "INCIDENT_ESCALATED";
    private static final Duration TARGET = Duration.ofMinutes(15);

    private ServiceFixture fixture;
    private InMemoryEscalationRepository escalations;
    private InMemoryNotificationRepository notifications;
    private InMemoryAuditRepository audit;
    private SlaMonitorService monitor;
    private Instant base;
    private Instant responseDeadline;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        base = Instant.parse("2026-11-15T10:00:00Z");
        fixture.clock.set(base);
        responseDeadline = base.plus(TARGET);

        fixture.users.save(TestData.teamLead("TL01"));
        fixture.users.save(new Administrator("MGR01", "Carol Manager", "mgr@corp.com", Role.IT_MANAGER));

        escalations = new InMemoryEscalationRepository();
        notifications = new InMemoryNotificationRepository();
        audit = new InMemoryAuditRepository();
        SlaAlertRepository slaAlerts = new InMemorySlaAlertRepository();

        fixture.events.subscribe(new AuditEventListener(audit));
        fixture.events.subscribe(new NotificationEventListener(new NotificationFactory(),
                new NotificationRecipientResolver(fixture.users),
                List.of(new InAppNotificationChannel(notifications))));

        EscalationService escalationService =
                new EscalationService(fixture.incidents, escalations, fixture.events, fixture.clock);
        monitor = new SlaMonitorService(fixture.incidents, slaAlerts,
                new SlaIncidentProcessor(slaAlerts, new SlaEvaluator(), fixture.events),
                escalationService, fixture.clock);
    }

    private Incident unacknowledgedP1(String id) {
        IncidentDraft draft = TestData.draft(TestData.employee("EMP01"),
                TestData.service("SVC01", BusinessCriticality.CRITICAL), TestData.category("CAT01"),
                Impact.CRITICAL, Urgency.HIGH, 1);
        Incident incident = new Incident(id, draft, new PriorityResult(95, PriorityLevel.P1), base);
        incident.applySlaDeadlines(responseDeadline, base.plus(Duration.ofHours(2)));
        fixture.incidents.save(incident);
        return incident;
    }

    private void tickAt(Instant time) {
        fixture.clock.set(time);
        monitor.runMonitor();
    }

    /** Recipients of the escalation notifications of the incident, in creation order. */
    private List<String> escalationRecipients(String incidentId) {
        return notifications.findAll().stream()
                .filter(n -> ESCALATION_TITLE.equals(n.title()) && incidentId.equals(n.incidentId()))
                .map(Notification::recipientId)
                .toList();
    }

    private static List<String> added(List<String> before, List<String> after) {
        return after.subList(before.size(), after.size()).stream().sorted().toList();
    }

    private long escalationAuditEntries(String incidentId) {
        return audit.findByIncidentId(incidentId).stream()
                .filter(e -> ESCALATION_ACTION.equals(e.action()))
                .count();
    }

    @Test
    @DisplayName("Monitor ticks escalate an unacknowledged P1 through L1, L2 and L3 with the right records, audit entries and recipients")
    void monitorTicks_escalateP1ThroughAllLevels_withAuditAndRecipients() {
        unacknowledgedP1("INC-E2E-1");

        tickAt(responseDeadline.minus(Duration.ofMinutes(1)));
        assertTrue(escalations.findByIncidentId("INC-E2E-1").isEmpty());
        assertTrue(escalationRecipients("INC-E2E-1").isEmpty());

        // Level 1 at the response deadline -> TEAM_LEAD (+ administrators)
        List<String> none = escalationRecipients("INC-E2E-1");
        tickAt(responseDeadline);
        List<String> afterL1 = escalationRecipients("INC-E2E-1");
        assertEquals(List.of("ADMIN01", "MGR01", "TL01"), added(none, afterL1));

        // Level 2 after +1 response target -> ADMINISTRATOR: administrators only, the team lead is not notified again
        tickAt(responseDeadline.plus(TARGET));
        List<String> afterL2 = escalationRecipients("INC-E2E-1");
        assertEquals(List.of("ADMIN01", "MGR01"), added(afterL1, afterL2));

        // Level 3 after +2 response targets -> IT_MANAGER: administrators (incl. IT manager) only
        tickAt(responseDeadline.plus(TARGET.multipliedBy(2)));
        List<String> afterL3 = escalationRecipients("INC-E2E-1");
        assertEquals(List.of("ADMIN01", "MGR01"), added(afterL2, afterL3));

        List<EscalationRecord> records = escalations.findByIncidentId("INC-E2E-1");
        assertEquals(List.of(1, 2, 3), records.stream().map(EscalationRecord::level).toList());
        assertEquals(List.of(Role.TEAM_LEAD, Role.ADMINISTRATOR, Role.IT_MANAGER),
                records.stream().map(EscalationRecord::targetRole).toList());

        // audit trail: one SYSTEM-authored entry per level, nobody unrelated was notified
        List<AuditEntry> entries = audit.findByIncidentId("INC-E2E-1").stream()
                .filter(e -> ESCALATION_ACTION.equals(e.action())).toList();
        assertEquals(3, entries.size());
        assertTrue(entries.stream().allMatch(e -> "SYSTEM".equals(e.actorId())));
        assertEquals(1, afterL3.stream().filter("TL01"::equals).count(), "team lead is notified at Level 1 only");
        assertTrue(afterL3.stream().noneMatch(id -> id.equals("EMP01") || id.startsWith("TECH")),
                "reporter and technicians are not escalation recipients");
    }

    @Test
    @DisplayName("Repeated monitor ticks at the same instant create no duplicate escalation record, audit entry or notification")
    void repeatedTicks_doNotDuplicateEscalationOutputs() {
        unacknowledgedP1("INC-E2E-2");

        tickAt(responseDeadline);
        tickAt(responseDeadline);
        tickAt(responseDeadline);

        assertEquals(1, escalations.findByIncidentId("INC-E2E-2").size());
        assertEquals(1, escalationAuditEntries("INC-E2E-2"));
        assertEquals(3, escalationRecipients("INC-E2E-2").size(), "admin, IT manager and team lead notified once");
    }

    @Test
    @DisplayName("Acknowledging after Level 1 stops any further escalation even when later thresholds pass")
    void acknowledgedAfterLevel1_stopsFurtherEscalation() {
        Incident incident = unacknowledgedP1("INC-E2E-3");
        tickAt(responseDeadline);
        assertEquals(1, escalations.findByIncidentId("INC-E2E-3").size());

        fixture.clock.set(responseDeadline.plus(Duration.ofMinutes(1)));
        incident.classify();
        incident.assign(TestData.technician("TECH01", 5), fixture.clock.instant());
        incident.acknowledge(fixture.clock.instant());
        fixture.incidents.save(incident);

        tickAt(responseDeadline.plus(TARGET));
        tickAt(responseDeadline.plus(TARGET.multipliedBy(2)));

        assertEquals(1, escalations.findByIncidentId("INC-E2E-3").size(), "no Level 2 or 3 after acknowledgement");
        assertEquals(1, escalationAuditEntries("INC-E2E-3"));
    }

    @Test
    @DisplayName("An incident acknowledged before the response deadline is never escalated, audited or notified by the monitor")
    void acknowledgedBeforeDeadline_isNeverEscalatedByMonitor() {
        Incident incident = unacknowledgedP1("INC-E2E-4");
        fixture.clock.set(base.plus(Duration.ofMinutes(5)));
        incident.classify();
        incident.assign(TestData.technician("TECH01", 5), fixture.clock.instant());
        incident.acknowledge(fixture.clock.instant());
        fixture.incidents.save(incident);

        tickAt(responseDeadline);
        tickAt(responseDeadline.plus(TARGET));
        tickAt(responseDeadline.plus(TARGET.multipliedBy(2)));

        assertTrue(escalations.findByIncidentId("INC-E2E-4").isEmpty());
        assertEquals(0, escalationAuditEntries("INC-E2E-4"));
        assertTrue(escalationRecipients("INC-E2E-4").isEmpty());
    }
}
