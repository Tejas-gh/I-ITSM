package com.iitsm.application.listener;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.event.*;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;
import com.iitsm.infrastructure.persistence.memory.InMemoryAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditEventListenerTest {

    private InMemoryAuditRepository auditRepository;
    private AuditEventListener listener;
    private InMemoryDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        auditRepository = new InMemoryAuditRepository();
        listener = new AuditEventListener(auditRepository);
        publisher = new InMemoryDomainEventPublisher();
        publisher.subscribe(listener);
    }

    @Test
    @DisplayName("critical() returns true")
    void testCriticalReturnsTrue() {
        assertTrue(listener.critical(), "AuditEventListener must be critical so audit failures abort transactions");
    }

    @Test
    @DisplayName("Maps domain events using only DomainEvent interface without modifying listener")
    void testDomainEventsMapCorrectly() {
        Instant now = Instant.now();
        String incId = "INC-2026-0001";
        String actor = "tech-1";

        DomainEvent created = new IncidentCreated(
                now,
                actor,
                incId,
                null,
                IncidentStatus.NEW,
                "Ticket opened",
                "reporter-1",
                "srv-1",
                "cat-1",
                "cat-1",
                CategorySource.KEYWORD,
                PriorityLevel.P1,
                80
        );

        DomainEvent statusChanged = new IncidentStatusChanged(
                now.plusSeconds(60),
                actor,
                incId,
                IncidentStatus.NEW,
                IncidentStatus.IN_PROGRESS,
                "Working on incident",
                "START_WORK"
        );

        publisher.publish(created);
        publisher.publish(statusChanged);

        List<AuditEntry> recorded = auditRepository.findByIncidentId(incId);
        assertEquals(2, recorded.size());

        AuditEntry entry1 = recorded.get(0);
        assertEquals(created.occurredAt(), entry1.occurredAt());
        assertEquals(created.actorId(), entry1.actorId());
        assertEquals(created.incidentId(), entry1.incidentId());
        assertEquals(created.previousStatus(), entry1.previousStatus());
        assertEquals(created.newStatus(), entry1.newStatus());
        assertEquals(created.action(), entry1.action());
        assertEquals(created.description(), entry1.description());

        AuditEntry entry2 = recorded.get(1);
        assertEquals(statusChanged.occurredAt(), entry2.occurredAt());
        assertEquals(statusChanged.actorId(), entry2.actorId());
        assertEquals(statusChanged.incidentId(), entry2.incidentId());
        assertEquals(statusChanged.previousStatus(), entry2.previousStatus());
        assertEquals(statusChanged.newStatus(), entry2.newStatus());
        assertEquals(statusChanged.action(), entry2.action());
        assertEquals(statusChanged.description(), entry2.description());
    }

    @Test
    @DisplayName("Repository failure propagates immediately when publisher notifies critical listener")
    void testRepositoryFailurePropagates() {
        AuditRepository failingRepo = new AuditRepository() {
            @Override
            public void save(AuditEntry entry) {
                throw new IllegalStateException("Database write failed");
            }

            @Override
            public List<AuditEntry> findByIncidentId(String incidentId) {
                return List.of();
            }

            @Override
            public List<AuditEntry> findAll() {
                return List.of();
            }
        };

        AuditEventListener failingListener = new AuditEventListener(failingRepo);
        InMemoryDomainEventPublisher pub = new InMemoryDomainEventPublisher();
        pub.subscribe(failingListener);

        DomainEvent event = new IncidentStatusChanged(
                Instant.now(),
                "u1",
                "INC-1",
                IncidentStatus.NEW,
                IncidentStatus.IN_PROGRESS,
                "State change",
                "START"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pub.publish(event));
        assertEquals("Database write failed", ex.getMessage());
    }

    @Test
    @DisplayName("Null parameters reject with InvalidInputException")
    void testNullValidation() {
        assertThrows(InvalidInputException.class, () -> new AuditEventListener(null));
        assertThrows(InvalidInputException.class, () -> listener.onEvent(null));
    }
}
