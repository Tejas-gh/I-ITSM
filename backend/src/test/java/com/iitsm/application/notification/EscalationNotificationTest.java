package com.iitsm.application.notification;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentEscalated;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;

import static org.junit.jupiter.api.Assertions.*;

class EscalationNotificationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:15:00Z");

    private InMemoryUserRepository userRepository;
    private NotificationRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        resolver = new NotificationRecipientResolver(userRepository);

        User admin = TestData.admin("ADMIN01");
        User teamLead = TestData.teamLead("TL01");
        User itManager = new Administrator("MGR01", "Carol Manager", "mgr@corp.com", Role.IT_MANAGER);
        User tech = TestData.technician("TECH01", 5);
        User emp = TestData.employee("EMP01");

        userRepository.save(admin);
        userRepository.save(teamLead);
        userRepository.save(itManager);
        userRepository.save(tech);
        userRepository.save(emp);
    }

    @Test
    @DisplayName("INCIDENT_ESCALATED Level 1: Notifies Administrators and Team Leads")
    void level1_notifiesAdminAndTeamLead() {
        DomainEvent event = new IncidentEscalated(
                NOW,
                DomainEvent.SYSTEM_ACTOR,
                "INC-001",
                IncidentStatus.ASSIGNED,
                IncidentStatus.ASSIGNED,
                "Escalated to Team Lead",
                1,
                Role.TEAM_LEAD
        );

        List<String> recipients = resolver.resolveRecipientIds(event);

        assertTrue(recipients.contains("ADMIN01"), "Admin should be notified");
        assertTrue(recipients.contains("TL01"), "Team Lead should be notified");
        assertTrue(recipients.contains("MGR01"), "IT Manager (admin role) should be notified");
        assertFalse(recipients.contains("TECH01"), "Technician not targeted");
        assertFalse(recipients.contains("EMP01"), "Employee not targeted");
    }

    @Test
    @DisplayName("INCIDENT_ESCALATED Level 3: Notifies Administrators and IT Manager")
    void level3_notifiesAdminAndManager() {
        DomainEvent event = new IncidentEscalated(
                NOW,
                DomainEvent.SYSTEM_ACTOR,
                "INC-001",
                IncidentStatus.ASSIGNED,
                IncidentStatus.ASSIGNED,
                "Escalated to IT Manager",
                3,
                Role.IT_MANAGER
        );

        List<String> recipients = resolver.resolveRecipientIds(event);

        assertTrue(recipients.contains("ADMIN01"), "Admin should be notified");
        assertTrue(recipients.contains("MGR01"), "IT Manager should be notified");
        assertFalse(recipients.contains("TL01"), "Team Lead not targeted in Level 3");
    }
}
