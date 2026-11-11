package com.iitsm.domain.audit;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentCreated;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AuditEntryTest {

    @Test
    @DisplayName("Successfully creates AuditEntry with all 7 fields")
    void testCreateAuditEntry() {
        Instant now = Instant.now();
        AuditEntry entry = new AuditEntry(
                now,
                "user-1",
                "INC-2026-0001",
                IncidentStatus.NEW,
                IncidentStatus.ASSIGNED,
                "ASSIGN",
                "Assigned to technician"
        );

        assertEquals(now, entry.occurredAt());
        assertEquals("user-1", entry.actorId());
        assertEquals("INC-2026-0001", entry.incidentId());
        assertEquals(IncidentStatus.NEW, entry.previousStatus());
        assertEquals(IncidentStatus.ASSIGNED, entry.newStatus());
        assertEquals("ASSIGN", entry.action());
        assertEquals("Assigned to technician", entry.description());
    }

    @Test
    @DisplayName("AuditEntry.from converts DomainEvent into AuditEntry")
    void testFromDomainEvent() {
        Instant now = Instant.now();
        DomainEvent event = new IncidentCreated(
                now,
                "user-1",
                "INC-2026-0001",
                null,
                IncidentStatus.NEW,
                "Incident created",
                "reporter-1",
                "srv-1",
                "cat-1",
                "cat-1",
                CategorySource.KEYWORD,
                PriorityLevel.P1,
                80
        );

        AuditEntry entry = AuditEntry.from(event);
        assertNotNull(entry);
        assertEquals(now, entry.occurredAt());
        assertEquals("user-1", entry.actorId());
        assertEquals("INC-2026-0001", entry.incidentId());
        assertNull(entry.previousStatus());
        assertEquals(IncidentStatus.NEW, entry.newStatus());
        assertEquals(IncidentCreated.ACTION, entry.action());
        assertEquals("Incident created", entry.description());
    }

    @Test
    @DisplayName("Validation fails when required fields are missing")
    void testValidation() {
        Instant now = Instant.now();
        assertThrows(InvalidInputException.class, () ->
                new AuditEntry(null, "u1", "INC-1", null, IncidentStatus.NEW, "CREATE", "desc"));
        assertThrows(InvalidInputException.class, () ->
                new AuditEntry(now, null, "INC-1", null, IncidentStatus.NEW, "CREATE", "desc"));
        assertThrows(InvalidInputException.class, () ->
                new AuditEntry(now, "   ", "INC-1", null, IncidentStatus.NEW, "CREATE", "desc"));
        assertThrows(InvalidInputException.class, () ->
                new AuditEntry(now, "u1", null, null, IncidentStatus.NEW, "CREATE", "desc"));
        assertThrows(InvalidInputException.class, () ->
                new AuditEntry(now, "u1", "INC-1", null, IncidentStatus.NEW, null, "desc"));
        assertThrows(InvalidInputException.class, () ->
                AuditEntry.from(null));
    }
}
