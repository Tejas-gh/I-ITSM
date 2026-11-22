package com.iitsm.application.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentEscalated;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;

/** Escalation recipient scoping: only the target role holder and administrators, only active users. */
class EscalationRecipientRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:30:00Z");

    private InMemoryUserRepository users;
    private NotificationRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        resolver = new NotificationRecipientResolver(users);
        users.save(TestData.admin("ADMIN01"));
        users.save(new Administrator("MGR01", "Carol Manager", "mgr@corp.com", Role.IT_MANAGER));
        users.save(TestData.teamLead("TL01"));
        users.save(TestData.technician("TECH01", 5));
        users.save(TestData.employee("EMP01"));
    }

    private static DomainEvent escalation(int level, Role targetRole) {
        return new IncidentEscalated(NOW, DomainEvent.SYSTEM_ACTOR, "INC-001", IncidentStatus.ASSIGNED,
                IncidentStatus.ASSIGNED, "Escalated", level, targetRole);
    }

    @Test
    @DisplayName("Level 2 notifies administrators only: not the team lead, technicians or employees")
    void level2_notifiesAdministratorsOnly() {
        List<String> recipients = resolver.resolveRecipientIds(escalation(2, Role.ADMINISTRATOR));

        assertTrue(recipients.contains("ADMIN01"));
        assertTrue(recipients.contains("MGR01"));
        assertFalse(recipients.contains("TL01"));
        assertFalse(recipients.contains("TECH01"));
        assertFalse(recipients.contains("EMP01"));
    }

    @Test
    @DisplayName("An inactive team lead is not notified at Level 1, administrators still are")
    void level1_inactiveTeamLeadIsNotNotified() {
        Technician inactiveLead = TestData.teamLead("TL02");
        inactiveLead.deactivate();
        users.save(inactiveLead);

        List<String> recipients = resolver.resolveRecipientIds(escalation(1, Role.TEAM_LEAD));

        assertTrue(recipients.contains("TL01"));
        assertFalse(recipients.contains("TL02"));
        assertTrue(recipients.contains("ADMIN01"));
    }
}
