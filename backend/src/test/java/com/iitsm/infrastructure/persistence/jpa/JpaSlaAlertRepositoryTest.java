package com.iitsm.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaType;

import jakarta.persistence.EntityManager;

/** A3 integration tests: SLA alert persistence on H2 (PostgreSQL mode) with the V6 Flyway migration. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaSlaAlertRepositoryTest {

    private static final Instant DEADLINE = Instant.parse("2026-11-16T09:05:00Z");
    private static final Instant RAISED = Instant.parse("2026-11-16T09:04:00Z");

    @Autowired
    private SlaAlertRepository alerts;
    @Autowired
    private SlaAlertEntityRepository entityRepository;
    @Autowired
    private EntityManager em;

    private static SlaAlert alert(String incidentId, SlaType type, SlaAlertKind kind) {
        return new SlaAlert(incidentId, type, kind, DEADLINE, RAISED);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void wiring_slaAlertRepositoryBean_isTheJpaImplementation() {
        assertInstanceOf(JpaSlaAlertRepository.class, alerts);
    }

    @Test
    void save_thenFindByKey_roundTripsAllFieldsFromTheDatabase() {
        SlaAlert saved = alert("INC9001", SlaType.RESPONSE, SlaAlertKind.WARNING);
        alerts.save(saved);
        flushAndClear();

        SlaAlert loaded = alerts.findByKey("INC9001", SlaType.RESPONSE, SlaAlertKind.WARNING).orElseThrow();
        assertEquals(saved, loaded);
        assertEquals("INC9001:RESPONSE:WARNING", loaded.id());
        assertEquals(DEADLINE, loaded.deadline());
        assertEquals(RAISED, loaded.raisedAt());
    }

    @Test
    void save_sameKeyTwice_isIdempotentAndCreatesNoDuplicateRow() {
        alerts.save(alert("INC9002", SlaType.RESOLUTION, SlaAlertKind.VIOLATION));
        alerts.save(alert("INC9002", SlaType.RESOLUTION, SlaAlertKind.VIOLATION));
        flushAndClear();

        assertEquals(1, alerts.findByIncidentId("INC9002").size());
    }

    @Test
    void save_sameKeyWithNewTimes_updatesInPlace() {
        alerts.save(alert("INC9003", SlaType.RESPONSE, SlaAlertKind.VIOLATION));
        flushAndClear();
        Instant newDeadline = DEADLINE.plusSeconds(600);
        Instant newRaised = RAISED.plusSeconds(600);
        alerts.save(new SlaAlert("INC9003", SlaType.RESPONSE, SlaAlertKind.VIOLATION, newDeadline, newRaised));
        flushAndClear();

        List<SlaAlert> all = alerts.findByIncidentId("INC9003");
        assertEquals(1, all.size());
        assertEquals(newDeadline, all.get(0).deadline());
        assertEquals(newRaised, all.get(0).raisedAt());
    }

    @Test
    void exists_distinguishesIncidentTypeAndKind() {
        alerts.save(alert("INC9004", SlaType.RESPONSE, SlaAlertKind.WARNING));
        flushAndClear();

        assertTrue(alerts.exists("INC9004", SlaType.RESPONSE, SlaAlertKind.WARNING));
        assertFalse(alerts.exists("INC9004", SlaType.RESPONSE, SlaAlertKind.VIOLATION));
        assertFalse(alerts.exists("INC9004", SlaType.RESOLUTION, SlaAlertKind.WARNING));
        assertFalse(alerts.exists("INC9999", SlaType.RESPONSE, SlaAlertKind.WARNING));
    }

    @Test
    void exists_andFindByKey_withNullOrBlankArguments_areSafeNoOps() {
        assertFalse(alerts.exists(null, SlaType.RESPONSE, SlaAlertKind.WARNING));
        assertFalse(alerts.exists(" ", SlaType.RESPONSE, SlaAlertKind.WARNING));
        assertFalse(alerts.exists("INC1", null, SlaAlertKind.WARNING));
        assertFalse(alerts.exists("INC1", SlaType.RESPONSE, null));
        assertTrue(alerts.findByKey(null, SlaType.RESPONSE, SlaAlertKind.WARNING).isEmpty());
        assertTrue(alerts.findById(" ").isEmpty());
    }

    @Test
    void findByKind_returnsOnlyThatKind_inInsertionOrder() {
        int warningsBefore = alerts.findByKind(SlaAlertKind.WARNING).size();
        int violationsBefore = alerts.findByKind(SlaAlertKind.VIOLATION).size();
        alerts.save(alert("INC9010", SlaType.RESPONSE, SlaAlertKind.WARNING));
        alerts.save(alert("INC9011", SlaType.RESOLUTION, SlaAlertKind.VIOLATION));
        alerts.save(alert("INC9012", SlaType.RESOLUTION, SlaAlertKind.WARNING));
        flushAndClear();

        List<SlaAlert> warnings = alerts.findByKind(SlaAlertKind.WARNING);
        List<SlaAlert> violations = alerts.findByKind(SlaAlertKind.VIOLATION);
        assertEquals(warningsBefore + 2, warnings.size());
        assertEquals(violationsBefore + 1, violations.size());
        assertTrue(warnings.stream().allMatch(a -> a.kind() == SlaAlertKind.WARNING));
        assertEquals("INC9010", warnings.get(warnings.size() - 2).incidentId());
        assertEquals("INC9012", warnings.get(warnings.size() - 1).incidentId());
        assertEquals("INC9011", violations.get(violations.size() - 1).incidentId());
    }

    @Test
    void findAll_includesSavedAlertsInInsertionOrder() {
        alerts.save(alert("INC9020", SlaType.RESPONSE, SlaAlertKind.WARNING));
        alerts.save(alert("INC9021", SlaType.RESPONSE, SlaAlertKind.WARNING));
        flushAndClear();

        List<SlaAlert> all = alerts.findAll();
        assertEquals("INC9020", all.get(all.size() - 2).incidentId());
        assertEquals("INC9021", all.get(all.size() - 1).incidentId());
    }

    @Test
    void findById_returnsAlertByItsId() {
        alerts.save(alert("INC9030", SlaType.RESOLUTION, SlaAlertKind.WARNING));
        flushAndClear();

        assertEquals("INC9030", alerts.findById("INC9030:RESOLUTION:WARNING").orElseThrow().incidentId());
        assertTrue(alerts.findById("nope").isEmpty());
    }

    @Test
    void findByIncidentId_returnsAllAlertsOfThatIncidentOnly() {
        alerts.save(alert("INC9040", SlaType.RESPONSE, SlaAlertKind.WARNING));
        alerts.save(alert("INC9040", SlaType.RESPONSE, SlaAlertKind.VIOLATION));
        alerts.save(alert("INC9041", SlaType.RESPONSE, SlaAlertKind.WARNING));
        flushAndClear();

        assertEquals(2, alerts.findByIncidentId("INC9040").size());
        assertEquals(1, alerts.findByIncidentId("INC9041").size());
        assertTrue(alerts.findByIncidentId("INC9042").isEmpty());
    }

    @Test
    void invalidArguments_throwInvalidInputException_likeTheInMemoryRepository() {
        assertThrows(InvalidInputException.class, () -> alerts.save(null));
        assertThrows(InvalidInputException.class, () -> alerts.findByKind(null));
        assertThrows(InvalidInputException.class, () -> alerts.findByIncidentId(null));
        assertThrows(InvalidInputException.class, () -> alerts.findByIncidentId("  "));
    }

    @Test
    void database_rejectsDuplicateIdempotencyKey_evenBypassingTheAdapter() {
        entityRepository.saveAndFlush(SlaAlertEntity.from(alert("INC9050", SlaType.RESPONSE, SlaAlertKind.WARNING)));
        SlaAlertEntity duplicate = SlaAlertEntity.from(alert("INC9050", SlaType.RESPONSE, SlaAlertKind.WARNING));
        duplicate.alertId = "other-id";

        assertThrows(DataIntegrityViolationException.class, () -> entityRepository.saveAndFlush(duplicate));
    }
}
