package com.iitsm.domain.notification;

import com.iitsm.domain.event.*;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.sla.SlaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NotificationFactoryTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final String INCIDENT_ID = "INC-2026-0001";
    private static final String ACTOR = "admin-1";

    private NotificationFactory factory;

    @BeforeEach
    void setUp() {
        factory = new NotificationFactory();
    }

    @Test
    @DisplayName("INCIDENT_CREATED produces non-blank title and body")
    void incidentCreated_producesTemplate() {
        DomainEvent event = new IncidentCreated(
                NOW, ACTOR, INCIDENT_ID, null, IncidentStatus.NEW,
                "Ticket opened", "reporter-1", "srv-1", "cat-1",
                null, CategorySource.MANUAL, PriorityLevel.P1, 80
        );

        Optional<NotificationTemplate> result = factory.templateFor(event);

        assertTrue(result.isPresent());
        assertFalse(result.get().title().isBlank());
        assertFalse(result.get().body().isBlank());
        assertTrue(result.get().body().contains(INCIDENT_ID));
    }

    @Test
    @DisplayName("SLA_WARNING produces a template")
    void slaWarning_producesTemplate() {
        DomainEvent event = new SlaWarning(
                NOW, DomainEvent.SYSTEM_ACTOR, INCIDENT_ID,
                IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "SLA approaching", SlaType.RESOLUTION, NOW.plusSeconds(600)
        );

        Optional<NotificationTemplate> result = factory.templateFor(event);

        assertTrue(result.isPresent());
        assertFalse(result.get().title().isBlank());
        assertTrue(result.get().body().contains(INCIDENT_ID));
    }

    @Test
    @DisplayName("SLA_VIOLATED produces a template")
    void slaViolated_producesTemplate() {
        DomainEvent event = new SlaViolated(
                NOW, DomainEvent.SYSTEM_ACTOR, INCIDENT_ID,
                IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "SLA breached", SlaType.RESPONSE, NOW.plusSeconds(0)
        );

        Optional<NotificationTemplate> result = factory.templateFor(event);

        assertTrue(result.isPresent());
        assertFalse(result.get().title().isBlank());
        assertTrue(result.get().body().contains(INCIDENT_ID));
    }

    @Test
    @DisplayName("Unknown action string returns empty Optional")
    void unknownAction_returnsEmpty() {
        // Use IncidentUpdated which supports dynamic action field
        DomainEvent event = new IncidentUpdated(
                NOW, ACTOR, INCIDENT_ID,
                IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "Something unknown", "SOME_UNKNOWN_ACTION_XYZ"
        );

        Optional<NotificationTemplate> result = factory.templateFor(event);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Null event throws InvalidInputException")
    void nullEvent_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> factory.templateFor(null));
    }
}
