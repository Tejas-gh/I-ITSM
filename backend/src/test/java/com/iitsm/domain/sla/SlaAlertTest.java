package com.iitsm.domain.sla;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.exception.InvalidInputException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlaAlertTest {

    private static final Instant DEADLINE = Instant.parse("2026-11-15T15:00:00Z");
    private static final Instant RAISED_AT = Instant.parse("2026-11-15T14:48:00Z");

    @Test
    @DisplayName("SlaAlert constructs successfully with valid arguments and default id")
    void validAlert_constructsSuccessfully() {
        SlaAlert alert = new SlaAlert("INC1001", SlaType.RESOLUTION, SlaAlertKind.WARNING, DEADLINE, RAISED_AT);

        assertEquals("INC1001", alert.incidentId());
        assertEquals(SlaType.RESOLUTION, alert.slaType());
        assertEquals(SlaAlertKind.WARNING, alert.kind());
        assertEquals(DEADLINE, alert.deadline());
        assertEquals(RAISED_AT, alert.raisedAt());
        assertEquals("INC1001:RESOLUTION:WARNING", alert.id());
        assertEquals("INC1001:RESOLUTION:WARNING", alert.alertKey());
    }

    @Test
    @DisplayName("SlaAlert preserves custom id if provided")
    void customId_preserved() {
        SlaAlert alert = new SlaAlert("custom-alert-99", "INC1001", SlaType.RESPONSE, SlaAlertKind.VIOLATION, DEADLINE, RAISED_AT);

        assertEquals("custom-alert-99", alert.id());
        assertEquals("INC1001:RESPONSE:VIOLATION", alert.alertKey());
    }

    @Test
    @DisplayName("SlaAlert rejects invalid arguments")
    void invalidArguments_throwException() {
        assertThrows(InvalidInputException.class, () ->
                new SlaAlert(null, SlaType.RESPONSE, SlaAlertKind.WARNING, DEADLINE, RAISED_AT));
        assertThrows(InvalidInputException.class, () ->
                new SlaAlert("  ", SlaType.RESPONSE, SlaAlertKind.WARNING, DEADLINE, RAISED_AT));
        assertThrows(InvalidInputException.class, () ->
                new SlaAlert("INC1001", null, SlaAlertKind.WARNING, DEADLINE, RAISED_AT));
        assertThrows(InvalidInputException.class, () ->
                new SlaAlert("INC1001", SlaType.RESPONSE, null, DEADLINE, RAISED_AT));
        assertThrows(InvalidInputException.class, () ->
                new SlaAlert("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING, null, RAISED_AT));
        assertThrows(InvalidInputException.class, () ->
                new SlaAlert("INC1001", SlaType.RESPONSE, SlaAlertKind.WARNING, DEADLINE, null));
    }

    @Test
    @DisplayName("key helper calculates deterministic idempotency key")
    void keyHelper_deterministic() {
        String key = SlaAlert.key("INC2002", SlaType.RESPONSE, SlaAlertKind.VIOLATION);
        assertEquals("INC2002:RESPONSE:VIOLATION", key);

        assertThrows(InvalidInputException.class, () -> SlaAlert.key("", SlaType.RESPONSE, SlaAlertKind.VIOLATION));
        assertThrows(InvalidInputException.class, () -> SlaAlert.key("INC1", null, SlaAlertKind.VIOLATION));
        assertThrows(InvalidInputException.class, () -> SlaAlert.key("INC1", SlaType.RESPONSE, null));
    }
}
