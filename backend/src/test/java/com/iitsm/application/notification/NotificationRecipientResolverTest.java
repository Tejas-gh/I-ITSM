package com.iitsm.application.notification;

import com.iitsm.domain.event.*;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.model.*;
import com.iitsm.domain.priority.WeightedPriorityStrategy;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentIdGenerator;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationRecipientResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private InMemoryUserRepository userRepository;
    private NotificationRecipientResolver resolver;

    // Users
    private Administrator admin1;
    private Administrator admin2;
    private Technician tech1;
    private Technician tech2;
    private Employee employee1;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        resolver = new NotificationRecipientResolver(userRepository);
        admin1 = TestData.admin("admin-1");
        admin2 = TestData.admin("admin-2");
        tech1 = TestData.technician("tech-1", 5);
        tech2 = TestData.technician("tech-2", 5);
        employee1 = TestData.employee("emp-1");
    }

    @Test
    @DisplayName("INCIDENT_CREATED: admins receive, actor excluded, inactive excluded")
    void incidentCreated_adminsReceive_actorExcluded_inactiveExcluded() {
        Administrator inactiveAdmin = TestData.admin("admin-inactive");
        inactiveAdmin.deactivate();

        userRepository.save(admin1);
        userRepository.save(admin2);
        userRepository.save(inactiveAdmin);
        userRepository.save(tech1);
        userRepository.save(employee1);

        // admin-2 is the actor
        DomainEvent event = new IncidentCreated(
                NOW, "admin-2", "INC-001", null, IncidentStatus.NEW,
                "Ticket opened", "emp-1", "srv-1", "cat-1",
                null, CategorySource.MANUAL, PriorityLevel.P1, 80
        );

        List<String> recipients = resolver.resolveRecipientIds(event);

        assertTrue(recipients.contains("admin-1"), "admin-1 should be notified");
        assertFalse(recipients.contains("admin-2"), "actor admin-2 should be excluded");
        assertFalse(recipients.contains("admin-inactive"), "inactive admin should be excluded");
        assertFalse(recipients.contains("tech-1"), "tech is not an admin");
        assertFalse(recipients.contains("emp-1"), "employee is not an admin");
    }

    @Test
    @DisplayName("INCIDENT_ASSIGNED: assigned technician + admins receive, actor excluded")
    void incidentAssigned_techAndAdminsReceive_actorExcluded() {
        userRepository.save(admin1);
        userRepository.save(admin2);
        userRepository.save(tech1);
        userRepository.save(tech2);
        userRepository.save(employee1);

        DomainEvent event = new IncidentAssigned(
                NOW, "admin-1", "INC-001",
                IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED,
                "Assigned to tech-1", "tech-1", true
        );

        List<String> recipients = resolver.resolveRecipientIds(event);

        // tech-1 is the assigned technician; admin-2 should receive; admin-1 is actor
        assertTrue(recipients.contains("tech-1"), "assigned tech-1 should receive");
        assertTrue(recipients.contains("admin-2"), "admin-2 should receive");
        assertFalse(recipients.contains("admin-1"), "actor admin-1 should be excluded");
        assertFalse(recipients.contains("tech-2"), "unrelated tech-2 should not be in this case");
        assertFalse(recipients.contains("emp-1"), "employee should not receive");
    }

    @Test
    @DisplayName("SLA_WARNING: admins + all technicians receive, actor excluded")
    void slaWarning_adminsAndTechsReceive_actorExcluded() {
        userRepository.save(admin1);
        userRepository.save(admin2);
        userRepository.save(tech1);
        userRepository.save(tech2);
        userRepository.save(employee1);

        DomainEvent event = new SlaWarning(
                NOW, DomainEvent.SYSTEM_ACTOR, "INC-001",
                IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "SLA approaching", com.iitsm.domain.sla.SlaType.RESOLUTION, NOW.plusSeconds(600)
        );

        List<String> recipients = resolver.resolveRecipientIds(event);

        // SYSTEM_ACTOR won't match any user id, so all active admins + techs should be included
        assertTrue(recipients.contains("admin-1"));
        assertTrue(recipients.contains("admin-2"));
        assertTrue(recipients.contains("tech-1"));
        assertTrue(recipients.contains("tech-2"));
        assertFalse(recipients.contains("emp-1"), "employee should not receive SLA warnings");
    }

    // ---- A1: lifecycle recipients = assignee + admins + reporter ----

    private static final String INC = "INC1001";

    private Incident incidentFor(InMemoryIncidentRepository repo, Employee reporter, Technician assignee) {
        Incident i = new IncidentFactory(new InMemoryIncidentIdGenerator(), new WeightedPriorityStrategy())
                .create(TestData.draft(reporter, TestData.service("srv-1", BusinessCriticality.HIGH),
                        TestData.category("NETWORK"), Impact.HIGH, Urgency.HIGH, 1), NOW);
        i.classify();
        if (assignee != null) {
            i.assign(assignee, NOW);
        }
        repo.save(i);
        return i;
    }

    private void saveAllUsers() {
        Employee other = TestData.employee("emp-other");
        for (User u : List.of(admin1, admin2, tech1, tech2, employee1, other)) {
            userRepository.save(u);
        }
    }

    private List<String> resolve(DomainEvent event, Technician assignee) {
        InMemoryIncidentRepository repo = new InMemoryIncidentRepository();
        incidentFor(repo, employee1, assignee);
        return new NotificationRecipientResolver(userRepository, repo).resolveRecipientIds(event);
    }

    @Test
    @DisplayName("ASSIGNED: assignee + admins + reporter; unrelated users excluded")
    void assigned_assigneeAdminsReporter() {
        saveAllUsers();
        DomainEvent e = new IncidentAssigned(NOW, "admin-1", INC, IncidentStatus.CLASSIFIED,
                IncidentStatus.ASSIGNED, "Assigned", "tech-1", false);
        List<String> r = resolve(e, tech1);
        assertEquals(java.util.Set.of("tech-1", "admin-2", "emp-1"), new java.util.HashSet<>(r));
        assertEquals(r.size(), new java.util.HashSet<>(r).size(), "no duplicates");
    }

    @Test
    @DisplayName("ACKNOWLEDGED: assignee is actor (excluded); admins + reporter notified")
    void acknowledged_adminsAndReporter() {
        saveAllUsers();
        DomainEvent e = new IncidentAcknowledged(NOW, "tech-1", INC, IncidentStatus.ASSIGNED,
                IncidentStatus.ACKNOWLEDGED, "Ack", "tech-1");
        assertEquals(java.util.Set.of("admin-1", "admin-2", "emp-1"), new java.util.HashSet<>(resolve(e, tech1)));
    }

    @Test
    @DisplayName("INFO_REQUESTED: assignee (from incident) + admins + reporter")
    void infoRequested_assigneeAdminsReporter() {
        saveAllUsers();
        DomainEvent e = new IncidentStatusChanged(NOW, "tech-1", INC, IncidentStatus.IN_PROGRESS,
                IncidentStatus.WAITING_FOR_USER, "Info requested", "INFO_REQUESTED");
        assertEquals(java.util.Set.of("admin-1", "admin-2", "emp-1"), new java.util.HashSet<>(resolve(e, tech1)));
        DomainEvent byAdmin = new IncidentStatusChanged(NOW, "admin-1", INC, IncidentStatus.IN_PROGRESS,
                IncidentStatus.WAITING_FOR_USER, "Info requested", "INFO_REQUESTED");
        assertTrue(resolve(byAdmin, tech1).contains("tech-1"), "assignee resolved from incident");
    }

    @Test
    @DisplayName("RESOLVED: assignee excluded as actor; admins + reporter notified")
    void resolved_adminsAndReporter() {
        saveAllUsers();
        DomainEvent e = new IncidentResolved(NOW, "tech-1", INC, IncidentStatus.IN_PROGRESS,
                IncidentStatus.RESOLVED, "Resolved", "tech-1");
        assertEquals(java.util.Set.of("admin-1", "admin-2", "emp-1"), new java.util.HashSet<>(resolve(e, tech1)));
    }

    @Test
    @DisplayName("CLOSED: only assignee + admins + reporter — no broadcast to unrelated users")
    void closed_noBroadcast() {
        Employee inactiveReporterLike = TestData.employee("emp-inactive");
        inactiveReporterLike.deactivate();
        saveAllUsers();
        userRepository.save(inactiveReporterLike);
        DomainEvent e = new IncidentClosed(NOW, "admin-1", INC, IncidentStatus.VERIFIED,
                IncidentStatus.CLOSED, "Closed", "admin-1");
        List<String> r = resolve(e, tech1);
        assertEquals(java.util.Set.of("tech-1", "admin-2", "emp-1"), new java.util.HashSet<>(r));
        assertFalse(r.contains("tech-2"));
        assertFalse(r.contains("emp-other"));
        assertFalse(r.contains("emp-inactive"));
    }

    @Test
    @DisplayName("Inactive reporter/assignee are skipped; unassigned incident has no assignee recipient")
    void inactiveAndUnassigned() {
        saveAllUsers();
        employee1.deactivate();
        DomainEvent e = new IncidentClosed(NOW, "admin-1", INC, IncidentStatus.VERIFIED,
                IncidentStatus.CLOSED, "Closed", "admin-1");
        assertEquals(List.of("admin-2"), resolve(e, null));
    }

    @Test
    @DisplayName("User who is both reporter-side and admin-side is notified once")
    void overlappingGroups_deliveredOnce() {
        saveAllUsers();
        // admin-2 is also the technician id carried by the event -> appears in two groups
        DomainEvent e = new IncidentAssigned(NOW, "admin-1", INC, IncidentStatus.CLASSIFIED,
                IncidentStatus.ASSIGNED, "Assigned", "admin-2", false);
        List<String> r = resolve(e, null);
        assertEquals(1, r.stream().filter("admin-2"::equals).count());
    }

    @Test
    @DisplayName("Missing incident: falls back to event assignee + admins without failing")
    void missingIncident_fallback() {
        saveAllUsers();
        DomainEvent e = new IncidentClosed(NOW, "admin-1", "INC-UNKNOWN", IncidentStatus.VERIFIED,
                IncidentStatus.CLOSED, "Closed", "admin-1");
        List<String> r = new NotificationRecipientResolver(userRepository, new InMemoryIncidentRepository())
                .resolveRecipientIds(e);
        assertEquals(List.of("admin-2"), r);
    }

    @Test
    @DisplayName("SLA_VIOLATED: admins + technicians (unchanged), employees excluded")
    void slaViolated_unchanged() {
        saveAllUsers();
        DomainEvent e = new SlaViolated(NOW, DomainEvent.SYSTEM_ACTOR, INC, IncidentStatus.IN_PROGRESS,
                IncidentStatus.IN_PROGRESS, "Violated", com.iitsm.domain.sla.SlaType.RESOLUTION, NOW);
        List<String> r = resolve(e, tech1);
        assertTrue(r.containsAll(List.of("admin-1", "admin-2", "tech-1", "tech-2")));
        assertFalse(r.contains("emp-1"));
    }

    @Test
    @DisplayName("Unknown event action returns empty list")
    void unknownAction_returnsEmptyList() {
        userRepository.save(admin1);
        userRepository.save(admin2);
        userRepository.save(tech1);

        DomainEvent event = new IncidentUpdated(
                NOW, "actor-1", "INC-001",
                IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "Something unknown", "SOME_UNKNOWN_ACTION_XYZ"
        );

        List<String> recipients = resolver.resolveRecipientIds(event);

        assertTrue(recipients.isEmpty());
    }
}
