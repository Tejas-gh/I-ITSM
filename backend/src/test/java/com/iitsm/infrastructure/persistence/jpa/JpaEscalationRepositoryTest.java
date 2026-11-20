package com.iitsm.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.escalation.EscalationRepository;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;

import jakarta.persistence.EntityManager;

/** A7 integration tests: escalation persistence on H2 (PostgreSQL mode) with the V7 Flyway migration. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaEscalationRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-11-16T09:05:00Z");

    @Autowired
    private EscalationRepository escalations;
    @Autowired
    private EscalationEntityRepository entityRepository;
    @Autowired
    private EntityManager em;

    private static EscalationRecord record(String incidentId, int level, Role role, Instant at) {
        return EscalationRecord.create(incidentId, level, role, at, "Level " + level + " reason");
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void wiring_escalationRepositoryBean_isTheJpaImplementation() {
        assertInstanceOf(JpaEscalationRepository.class, escalations);
    }

    @Test
    void save_assignsIdAndRoundTripsAllFieldsFromTheDatabase() {
        EscalationRecord saved = escalations.save(record("ESC9001", 1, Role.TEAM_LEAD, T0));
        flushAndClear();

        assertNotNull(saved.id());
        EscalationRecord loaded = escalations.findByIncidentId("ESC9001").get(0);
        assertEquals(saved, loaded);
        assertEquals("ESC9001", loaded.incidentId());
        assertEquals(1, loaded.level());
        assertEquals(Role.TEAM_LEAD, loaded.targetRole());
        assertEquals(T0, loaded.escalatedAt());
        assertEquals("Level 1 reason", loaded.reason());
    }

    @Test
    void save_sameIncidentAndLevelTwice_createsNoDuplicateAndKeepsTheOriginal() {
        EscalationRecord first = escalations.save(record("ESC9002", 1, Role.TEAM_LEAD, T0));
        flushAndClear();
        EscalationRecord second = escalations.save(
                EscalationRecord.create("ESC9002", 1, Role.TEAM_LEAD, T0.plusSeconds(600), "later retry"));
        flushAndClear();

        assertEquals(first.id(), second.id());
        assertEquals(T0, second.escalatedAt(), "original timestamp must not be rewritten");
        assertEquals("Level 1 reason", second.reason());
        assertEquals(1, escalations.findByIncidentId("ESC9002").size());
    }

    @Test
    void save_sameLevelOnDifferentIncidents_andDifferentLevelsOnOneIncident_areAllowed() {
        escalations.save(record("ESC9003", 1, Role.TEAM_LEAD, T0));
        escalations.save(record("ESC9004", 1, Role.TEAM_LEAD, T0));
        escalations.save(record("ESC9003", 2, Role.ADMINISTRATOR, T0.plusSeconds(300)));
        flushAndClear();

        assertEquals(2, escalations.findByIncidentId("ESC9003").size());
        assertEquals(1, escalations.findByIncidentId("ESC9004").size());
    }

    @Test
    void findByIncidentId_returnsRecordsChronologically_evenWhenSavedOutOfOrder() {
        escalations.save(record("ESC9005", 2, Role.ADMINISTRATOR, T0.plusSeconds(300)));
        escalations.save(record("ESC9005", 1, Role.TEAM_LEAD, T0));
        escalations.save(record("ESC9005", 3, Role.IT_MANAGER, T0.plusSeconds(600)));
        flushAndClear();

        List<EscalationRecord> list = escalations.findByIncidentId("ESC9005");
        assertEquals(List.of(1, 2, 3), list.stream().map(EscalationRecord::level).toList());
    }

    @Test
    void findByIncidentId_unknownNullOrBlank_returnsEmpty() {
        assertTrue(escalations.findByIncidentId("NOPE").isEmpty());
        assertTrue(escalations.findByIncidentId(null).isEmpty());
        assertTrue(escalations.findByIncidentId("  ").isEmpty());
    }

    @Test
    void findLatestByIncidentId_returnsTheMostRecentLevel() {
        escalations.save(record("ESC9006", 1, Role.TEAM_LEAD, T0));
        escalations.save(record("ESC9006", 2, Role.ADMINISTRATOR, T0.plusSeconds(300)));
        flushAndClear();

        EscalationRecord latest = escalations.findLatestByIncidentId("ESC9006").orElseThrow();
        assertEquals(2, latest.level());
        assertEquals(Role.ADMINISTRATOR, latest.targetRole());
    }

    @Test
    void findLatestByIncidentId_unknownNullOrBlank_returnsEmpty() {
        assertTrue(escalations.findLatestByIncidentId("NOPE").isEmpty());
        assertTrue(escalations.findLatestByIncidentId(null).isEmpty());
        assertTrue(escalations.findLatestByIncidentId(" ").isEmpty());
    }

    @Test
    void existsByIncidentIdAndLevel_isTheIdempotencyGuard_andSurvivesAClearedSession() {
        escalations.save(record("ESC9007", 1, Role.TEAM_LEAD, T0));
        flushAndClear(); // nothing cached any more: the answer must come from the database

        assertTrue(escalations.existsByIncidentIdAndLevel("ESC9007", 1));
        assertFalse(escalations.existsByIncidentIdAndLevel("ESC9007", 2));
        assertFalse(escalations.existsByIncidentIdAndLevel("ESC9999", 1));
        assertFalse(escalations.existsByIncidentIdAndLevel(null, 1));
        assertFalse(escalations.existsByIncidentIdAndLevel(" ", 1));
    }

    @Test
    void findAll_includesSavedRecordsInInsertionOrder() {
        escalations.save(record("ESC9008", 1, Role.TEAM_LEAD, T0));
        escalations.save(record("ESC9009", 1, Role.TEAM_LEAD, T0));
        flushAndClear();

        List<EscalationRecord> all = escalations.findAll();
        assertEquals("ESC9008", all.get(all.size() - 2).incidentId());
        assertEquals("ESC9009", all.get(all.size() - 1).incidentId());
    }

    @Test
    void save_null_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> escalations.save(null));
    }

    @Test
    void database_rejectsDuplicateIncidentLevel_evenBypassingTheAdapter() {
        entityRepository.saveAndFlush(EscalationEntity.from(record("ESC9010", 1, Role.TEAM_LEAD, T0)));
        EscalationEntity duplicate = EscalationEntity.from(record("ESC9010", 1, Role.TEAM_LEAD, T0));

        assertThrows(DataIntegrityViolationException.class, () -> entityRepository.saveAndFlush(duplicate));
    }

    @Test
    void database_rejectsAnOutOfRangeLevel() {
        EscalationEntity bad = EscalationEntity.from(record("ESC9011", 1, Role.TEAM_LEAD, T0));
        bad.escalationLevel = 4;

        assertThrows(DataIntegrityViolationException.class, () -> entityRepository.saveAndFlush(bad));
    }
}
