package com.iitsm.infrastructure.persistence.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySlaAlertRepositoryTest {

    private InMemorySlaAlertRepository repository;

    private static final Instant T0 = Instant.parse("2026-11-15T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-15T12:30:00Z");
    private static final Instant T2 = Instant.parse("2026-11-15T12:35:00Z");

    @BeforeEach
    void setUp() {
        repository = new InMemorySlaAlertRepository();
    }

    @Test
    @DisplayName("save and exists verify presence using natural idempotency key")
    void saveAndExists_workCorrectly() {
        assertFalse(repository.exists("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING));

        SlaAlert alert = new SlaAlert("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING, T1, T0);
        repository.save(alert);

        assertTrue(repository.exists("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING));
        assertFalse(repository.exists("INC1001", SlaType.RESPONSE, SlaAlertKind.VIOLATION));
        assertFalse(repository.exists("INC1001", SlaType.RESOLUTION, SlaAlertKind.WARNING));
        assertFalse(repository.exists("INC9999", SlaType.RESPONSE, SlaAlertKind.WARNING));
    }

    @Test
    @DisplayName("duplicate saves with same key perform idempotent upsert without creating duplicates")
    void duplicateSaves_areIdempotent() {
        SlaAlert alert1 = new SlaAlert("INC1001", SlaType.RESOLUTION, SlaAlertKind.WARNING, T1, T0);
        repository.save(alert1);

        assertEquals(1, repository.findAll().size());
        assertEquals(T0, repository.findByKey("INC1001", SlaType.RESOLUTION, SlaAlertKind.WARNING).orElseThrow().raisedAt());

        // Second tick creates updated alert for same incident, slaType, and kind
        SlaAlert alert2 = new SlaAlert("INC1001", SlaType.RESOLUTION, SlaAlertKind.WARNING, T1, T2);
        repository.save(alert2);

        // Count remains 1, updated raisedAt value
        List<SlaAlert> all = repository.findAll();
        assertEquals(1, all.size(), "Idempotent save must not create duplicate alerts");
        assertEquals(T2, all.get(0).raisedAt(), "Upsert must update existing entry");
    }

    @Test
    @DisplayName("findByKind filters alerts by WARNING and VIOLATION accurately")
    void findByKind_filtersAccurately() {
        SlaAlert w1 = new SlaAlert("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING, T1, T0);
        SlaAlert w2 = new SlaAlert("INC1002", SlaType.RESOLUTION, SlaAlertKind.WARNING, T1, T0);
        SlaAlert v1 = new SlaAlert("INC1001", SlaType.RESPONSE, SlaAlertKind.VIOLATION, T1, T2);

        repository.save(w1);
        repository.save(w2);
        repository.save(v1);

        List<SlaAlert> warnings = repository.findByKind(SlaAlertKind.WARNING);
        assertEquals(2, warnings.size());
        assertTrue(warnings.stream().allMatch(a -> a.kind() == SlaAlertKind.WARNING));

        List<SlaAlert> violations = repository.findByKind(SlaAlertKind.VIOLATION);
        assertEquals(1, violations.size());
        assertEquals("INC1001", violations.get(0).incidentId());
    }

    @Test
    @DisplayName("findByIncidentId returns all alerts for a given incident")
    void findByIncidentId_returnsMatchingAlerts() {
        repository.save(new SlaAlert("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING, T1, T0));
        repository.save(new SlaAlert("INC1001", SlaType.RESPONSE, SlaAlertKind.VIOLATION, T1, T2));
        repository.save(new SlaAlert("INC1002", SlaType.RESOLUTION, SlaAlertKind.VIOLATION, T1, T2));

        List<SlaAlert> inc1Alerts = repository.findByIncidentId("INC1001");
        assertEquals(2, inc1Alerts.size());

        List<SlaAlert> inc2Alerts = repository.findByIncidentId("INC1002");
        assertEquals(1, inc2Alerts.size());

        List<SlaAlert> empty = repository.findByIncidentId("INC9999");
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("findById and findByKey return matching optionals")
    void findByIdAndKey_workAsExpected() {
        SlaAlert alert = new SlaAlert("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING, T1, T0);
        repository.save(alert);

        Optional<SlaAlert> byKey = repository.findByKey("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING);
        assertTrue(byKey.isPresent());
        assertEquals(alert, byKey.get());

        Optional<SlaAlert> byId = repository.findById("INC1001:RESPONSE:WARNING");
        assertTrue(byId.isPresent());
        assertEquals(alert, byId.get());

        assertFalse(repository.findById("does-not-exist").isPresent());
    }

    @Test
    @DisplayName("invalid input checks throw InvalidInputException")
    void invalidInputs_throwExceptions() {
        assertThrows(InvalidInputException.class, () -> repository.save(null));
        assertThrows(InvalidInputException.class, () -> repository.findByKind(null));
        assertThrows(InvalidInputException.class, () -> repository.findByIncidentId(null));
        assertThrows(InvalidInputException.class, () -> repository.findByIncidentId("  "));

        // exists and findByKey gracefully return false / empty on null or blank
        assertFalse(repository.exists(null, SlaType.RESPONSE, SlaAlertKind.WARNING));
        assertFalse(repository.exists("INC1", null, SlaAlertKind.WARNING));
        assertFalse(repository.exists("INC1", SlaType.RESPONSE, null));

        assertTrue(repository.findByKey(null, SlaType.RESPONSE, SlaAlertKind.WARNING).isEmpty());
        assertTrue(repository.findById(null).isEmpty());
        assertTrue(repository.findById("").isEmpty());
    }
}
