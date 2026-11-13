package com.iitsm.infrastructure.persistence.jpa;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaAuditRepositoryTest {

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save and findByIncidentId round-trips correctly with ordering preserved")
    void save_thenFindByIncidentId_roundTrips() {
        Instant now = Instant.parse("2026-11-13T10:00:00Z");

        AuditEntry entry1 = new AuditEntry(
                now, "user-1", "INC-1001",
                null, IncidentStatus.NEW, "CREATE", "Created ticket"
        );
        AuditEntry entry2 = new AuditEntry(
                now.plusSeconds(30), "tech-1", "INC-1001",
                IncidentStatus.NEW, IncidentStatus.ASSIGNED, "ASSIGN", "Assigned to technician"
        );

        auditRepository.save(entry1);
        auditRepository.save(entry2);

        entityManager.flush();
        entityManager.clear();

        List<AuditEntry> results = auditRepository.findByIncidentId("INC-1001");
        assertEquals(2, results.size());

        AuditEntry r1 = results.get(0);
        assertEquals("user-1", r1.actorId());
        assertEquals("INC-1001", r1.incidentId());
        assertNull(r1.previousStatus());
        assertEquals(IncidentStatus.NEW, r1.newStatus());
        assertEquals("CREATE", r1.action());
        assertEquals("Created ticket", r1.description());

        AuditEntry r2 = results.get(1);
        assertEquals("tech-1", r2.actorId());
        assertEquals(IncidentStatus.NEW, r2.previousStatus());
        assertEquals(IncidentStatus.ASSIGNED, r2.newStatus());
        assertEquals("ASSIGN", r2.action());
    }

    @Test
    @DisplayName("findAll returns all audit entries across incidents")
    void findAll_returnsAllEntries() {
        Instant now = Instant.parse("2026-11-13T10:00:00Z");

        auditRepository.save(new AuditEntry(
                now, "user-1", "INC-2001", null, IncidentStatus.NEW, "CREATE", "Ticket 1"
        ));
        auditRepository.save(new AuditEntry(
                now, "user-2", "INC-2002", null, IncidentStatus.NEW, "CREATE", "Ticket 2"
        ));

        entityManager.flush();
        entityManager.clear();

        List<AuditEntry> all = auditRepository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    @DisplayName("Invalid arguments throw InvalidInputException")
    void invalidArguments_throwException() {
        assertThrows(InvalidInputException.class, () -> auditRepository.save(null));
        assertThrows(InvalidInputException.class, () -> auditRepository.findByIncidentId(""));
        assertThrows(InvalidInputException.class, () -> auditRepository.findByIncidentId(null));
    }
}
