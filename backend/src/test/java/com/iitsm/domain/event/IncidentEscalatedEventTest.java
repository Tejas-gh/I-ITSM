package com.iitsm.domain.event;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Role;

import static org.junit.jupiter.api.Assertions.*;

class IncidentEscalatedEventTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:15:00Z");

    @Test
    @DisplayName("Valid IncidentEscalated event creation")
    void validCreation() {
        IncidentEscalated event = new IncidentEscalated(
                NOW,
                DomainEvent.SYSTEM_ACTOR,
                "INC-001",
                IncidentStatus.ASSIGNED,
                IncidentStatus.ASSIGNED,
                "Escalated to Team Lead",
                1,
                Role.TEAM_LEAD
        );

        assertEquals(NOW, event.occurredAt());
        assertEquals("SYSTEM", event.actorId());
        assertEquals("INC-001", event.incidentId());
        assertEquals("INCIDENT_ESCALATED", event.action());
        assertEquals(1, event.escalationLevel());
        assertEquals(Role.TEAM_LEAD, event.targetRole());
    }

    @Test
    @DisplayName("Validation guards reject null or invalid inputs")
    void validationGuards() {
        assertThrows(InvalidInputException.class, () -> new IncidentEscalated(
                null, "SYSTEM", "INC-001", IncidentStatus.ASSIGNED, IncidentStatus.ASSIGNED, "desc", 1, Role.TEAM_LEAD
        ));
        assertThrows(InvalidInputException.class, () -> new IncidentEscalated(
                NOW, " ", "INC-001", IncidentStatus.ASSIGNED, IncidentStatus.ASSIGNED, "desc", 1, Role.TEAM_LEAD
        ));
        assertThrows(InvalidInputException.class, () -> new IncidentEscalated(
                NOW, "SYSTEM", null, IncidentStatus.ASSIGNED, IncidentStatus.ASSIGNED, "desc", 1, Role.TEAM_LEAD
        ));
        assertThrows(InvalidInputException.class, () -> new IncidentEscalated(
                NOW, "SYSTEM", "INC-001", IncidentStatus.ASSIGNED, IncidentStatus.ASSIGNED, "desc", 4, Role.TEAM_LEAD
        ));
        assertThrows(InvalidInputException.class, () -> new IncidentEscalated(
                NOW, "SYSTEM", "INC-001", IncidentStatus.ASSIGNED, IncidentStatus.ASSIGNED, "desc", 1, null
        ));
    }
}
