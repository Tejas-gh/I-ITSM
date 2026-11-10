package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.sla.SlaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EventRecordsTest {

    private final Instant now = Instant.parse("2026-11-10T10:00:00Z");

    @Test
    @DisplayName("IncidentCreated requires valid parameters and returns action INCIDENT_CREATED")
    void incidentCreated_validParameters_constructedSuccessfully() {
        IncidentCreated event = new IncidentCreated(
                now, "EMP001", "INC1001", null, IncidentStatus.NEW,
                "Incident created", "EMP001", "SRV-01", "CAT-01",
                null, CategorySource.MANUAL, PriorityLevel.P1, 64
        );
        assertEquals("INCIDENT_CREATED", event.action());
        assertNull(event.previousStatus());
        assertEquals(IncidentStatus.NEW, event.newStatus());

        assertThrows(InvalidInputException.class, () -> new IncidentCreated(
                now, "EMP001", "INC1001", IncidentStatus.NEW, IncidentStatus.NEW,
                "desc", "EMP001", "SRV-01", "CAT-01", null, CategorySource.MANUAL, PriorityLevel.P1, 64
        ));
    }

    @Test
    @DisplayName("IncidentClassified validates status transition NEW -> CLASSIFIED")
    void incidentClassified_validParameters_constructedSuccessfully() {
        IncidentClassified event = new IncidentClassified(
                now, "EMP001", "INC1001", IncidentStatus.NEW, IncidentStatus.CLASSIFIED,
                "Classified incident", "CAT-01"
        );
        assertEquals("INCIDENT_CLASSIFIED", event.action());
        assertEquals(IncidentStatus.NEW, event.previousStatus());
        assertEquals(IncidentStatus.CLASSIFIED, event.newStatus());

        assertThrows(InvalidInputException.class, () -> new IncidentClassified(
                now, "EMP001", "INC1001", IncidentStatus.CLASSIFIED, IncidentStatus.CLASSIFIED,
                "desc", "CAT-01"
        ));
    }

    @Test
    @DisplayName("PriorityChanged validates unchanged status and scores >= 1")
    void priorityChanged_validParameters_constructedSuccessfully() {
        PriorityChanged event = new PriorityChanged(
                now, "TECH001", "INC1001", IncidentStatus.ASSIGNED, IncidentStatus.ASSIGNED,
                "Priority changed", PriorityLevel.P2, PriorityLevel.P1, 20, 40
        );
        assertEquals("PRIORITY_CHANGED", event.action());

        assertThrows(InvalidInputException.class, () -> new PriorityChanged(
                now, "TECH001", "INC1001", IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED,
                "desc", PriorityLevel.P2, PriorityLevel.P1, 20, 40
        ));
    }

    @Test
    @DisplayName("IncidentAssigned validates CLASSIFIED -> ASSIGNED")
    void incidentAssigned_validParameters_constructedSuccessfully() {
        IncidentAssigned event = new IncidentAssigned(
                now, "ADMIN01", "INC1001", IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED,
                "Assigned to technician", "TECH001", true
        );
        assertEquals("INCIDENT_ASSIGNED", event.action());
        assertTrue(event.automatic());

        assertThrows(InvalidInputException.class, () -> new IncidentAssigned(
                now, "ADMIN01", "INC1001", IncidentStatus.NEW, IncidentStatus.ASSIGNED,
                "desc", "TECH001", true
        ));
    }

    @Test
    @DisplayName("IncidentAcknowledged validates ASSIGNED -> ACKNOWLEDGED")
    void incidentAcknowledged_validParameters_constructedSuccessfully() {
        IncidentAcknowledged event = new IncidentAcknowledged(
                now, "TECH001", "INC1001", IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED,
                "Acknowledged incident", "TECH001"
        );
        assertEquals("INCIDENT_ACKNOWLEDGED", event.action());

        assertThrows(InvalidInputException.class, () -> new IncidentAcknowledged(
                now, "TECH001", "INC1001", IncidentStatus.ACKNOWLEDGED, IncidentStatus.ACKNOWLEDGED,
                "desc", "TECH001"
        ));
    }

    @Test
    @DisplayName("IncidentStatusChanged supports dynamic actions per §J")
    void incidentStatusChanged_validParameters_constructedSuccessfully() {
        IncidentStatusChanged event = new IncidentStatusChanged(
                now, "TECH001", "INC1001", IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS,
                "Started work", "WORK_STARTED"
        );
        assertEquals("WORK_STARTED", event.action());

        assertThrows(InvalidInputException.class, () -> new IncidentStatusChanged(
                now, "TECH001", "INC1001", IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS,
                "Started work", ""
        ));
    }

    @Test
    @DisplayName("IncidentResolved validates IN_PROGRESS -> RESOLVED")
    void incidentResolved_validParameters_constructedSuccessfully() {
        IncidentResolved event = new IncidentResolved(
                now, "TECH001", "INC1001", IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED,
                "Resolved", "TECH001"
        );
        assertEquals("INCIDENT_RESOLVED", event.action());

        assertThrows(InvalidInputException.class, () -> new IncidentResolved(
                now, "TECH001", "INC1001", IncidentStatus.ASSIGNED, IncidentStatus.RESOLVED,
                "Resolved", "TECH001"
        ));
    }

    @Test
    @DisplayName("IncidentClosed validates VERIFIED -> CLOSED")
    void incidentClosed_validParameters_constructedSuccessfully() {
        IncidentClosed event = new IncidentClosed(
                now, "ADMIN01", "INC1001", IncidentStatus.VERIFIED, IncidentStatus.CLOSED,
                "Closed", "ADMIN01"
        );
        assertEquals("INCIDENT_CLOSED", event.action());

        assertThrows(InvalidInputException.class, () -> new IncidentClosed(
                now, "ADMIN01", "INC1001", IncidentStatus.RESOLVED, IncidentStatus.CLOSED,
                "Closed", "ADMIN01"
        ));
    }

    @Test
    @DisplayName("IncidentUpdated validates unchanged status and non-blank action")
    void incidentUpdated_validParameters_constructedSuccessfully() {
        IncidentUpdated event = new IncidentUpdated(
                now, "TECH001", "INC1001", IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "Note added", "NOTE_ADDED"
        );
        assertEquals("NOTE_ADDED", event.action());

        assertThrows(InvalidInputException.class, () -> new IncidentUpdated(
                now, "TECH001", "INC1001", IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED,
                "Note added", "NOTE_ADDED"
        ));
    }

    @Test
    @DisplayName("AssignmentFailed validates CLASSIFIED -> CLASSIFIED")
    void assignmentFailed_validParameters_constructedSuccessfully() {
        AssignmentFailed event = new AssignmentFailed(
                now, DomainEvent.SYSTEM_ACTOR, "INC1001", IncidentStatus.CLASSIFIED, IncidentStatus.CLASSIFIED,
                "Failed to auto-assign"
        );
        assertEquals("ASSIGNMENT_FAILED", event.action());
        assertEquals("SYSTEM", event.actorId());
    }

    @Test
    @DisplayName("SlaWarning and SlaViolated validate unchanged status and non-null slaType and deadline")
    void slaEvents_validParameters_constructedSuccessfully() {
        Instant deadline = now.plusSeconds(1800);

        SlaWarning warning = new SlaWarning(
                now, DomainEvent.SYSTEM_ACTOR, "INC1001", IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "SLA approaching", SlaType.RESOLUTION, deadline
        );
        assertEquals("SLA_WARNING", warning.action());
        assertEquals(SlaType.RESOLUTION, warning.slaType());
        assertEquals(deadline, warning.deadline());

        SlaViolated violated = new SlaViolated(
                now, DomainEvent.SYSTEM_ACTOR, "INC1001", IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "SLA breached", SlaType.RESPONSE, deadline
        );
        assertEquals("SLA_VIOLATED", violated.action());
        assertEquals(SlaType.RESPONSE, violated.slaType());
        assertEquals(deadline, violated.deadline());
    }
}
