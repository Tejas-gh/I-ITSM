package com.iitsm.infrastructure.persistence.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.model.Role;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryEscalationRepositoryTest {

    private InMemoryEscalationRepository repository;
    private static final Instant NOW = Instant.parse("2026-11-10T10:15:00Z");

    @BeforeEach
    void setUp() {
        repository = new InMemoryEscalationRepository();
    }

    @Test
    @DisplayName("save and findByIncidentId returns records in order")
    void saveAndFindByIncidentId() {
        EscalationRecord r1 = EscalationRecord.create("INC-01", 1, Role.TEAM_LEAD, NOW, "Level 1");
        EscalationRecord r2 = EscalationRecord.create("INC-01", 2, Role.ADMINISTRATOR, NOW.plusSeconds(900), "Level 2");

        EscalationRecord saved1 = repository.save(r1);
        EscalationRecord saved2 = repository.save(r2);

        assertNotNull(saved1.id());
        assertNotNull(saved2.id());

        List<EscalationRecord> records = repository.findByIncidentId("INC-01");
        assertEquals(2, records.size());
        assertEquals(1, records.get(0).level());
        assertEquals(2, records.get(1).level());
    }

    @Test
    @DisplayName("findLatestByIncidentId returns newest escalation")
    void findLatestByIncidentId() {
        assertTrue(repository.findLatestByIncidentId("INC-01").isEmpty());

        repository.save(EscalationRecord.create("INC-01", 1, Role.TEAM_LEAD, NOW, "Level 1"));
        repository.save(EscalationRecord.create("INC-01", 2, Role.ADMINISTRATOR, NOW.plusSeconds(900), "Level 2"));

        Optional<EscalationRecord> latest = repository.findLatestByIncidentId("INC-01");
        assertTrue(latest.isPresent());
        assertEquals(2, latest.get().level());
    }

    @Test
    @DisplayName("existsByIncidentIdAndLevel checks specific level presence")
    void existsByIncidentIdAndLevel() {
        assertFalse(repository.existsByIncidentIdAndLevel("INC-01", 1));

        repository.save(EscalationRecord.create("INC-01", 1, Role.TEAM_LEAD, NOW, "Level 1"));

        assertTrue(repository.existsByIncidentIdAndLevel("INC-01", 1));
        assertFalse(repository.existsByIncidentIdAndLevel("INC-01", 2));
    }
}
