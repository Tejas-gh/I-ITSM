package com.iitsm.application.notification;

import com.iitsm.domain.event.*;
import com.iitsm.domain.model.*;
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

    @Test
    @DisplayName("INCIDENT_CLOSED: all active users receive, actor excluded")
    void incidentClosed_allActiveUsersReceive_actorExcluded() {
        Employee inactiveEmp = TestData.employee("emp-inactive");
        inactiveEmp.deactivate();

        userRepository.save(admin1);
        userRepository.save(admin2);
        userRepository.save(tech1);
        userRepository.save(employee1);
        userRepository.save(inactiveEmp);

        DomainEvent event = new IncidentClosed(
                NOW, "admin-1", "INC-001",
                IncidentStatus.VERIFIED, IncidentStatus.CLOSED,
                "Incident closed", "admin-1"
        );

        List<String> recipients = resolver.resolveRecipientIds(event);

        assertFalse(recipients.contains("admin-1"), "actor should be excluded");
        assertTrue(recipients.contains("admin-2"));
        assertTrue(recipients.contains("tech-1"));
        assertTrue(recipients.contains("emp-1"));
        assertFalse(recipients.contains("emp-inactive"), "inactive user should be excluded");
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
