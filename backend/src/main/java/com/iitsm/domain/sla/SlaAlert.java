package com.iitsm.domain.sla;

import java.time.Instant;

import com.iitsm.domain.exception.InvalidInputException;

/**
 * Immutable domain model representing an SLA alert (warning or breach violation).
 *
 * <p>Idempotency rule: An SLA alert is uniquely identified by the tuple
 * {@code (incidentId, slaType, kind)}. Subsequent evaluations for the same incident, SLA dimension,
 * and alert kind are idempotent and must not duplicate alerts.</p>
 */
public record SlaAlert(
        String id,
        String incidentId,
        SlaType slaType,
        SlaAlertKind kind,
        Instant deadline,
        Instant raisedAt
) {

    public SlaAlert {
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (slaType == null) {
            throw new InvalidInputException("slaType must not be null");
        }
        if (kind == null) {
            throw new InvalidInputException("kind must not be null");
        }
        if (deadline == null) {
            throw new InvalidInputException("deadline must not be null");
        }
        if (raisedAt == null) {
            throw new InvalidInputException("raisedAt must not be null");
        }
        if (id == null || id.isBlank()) {
            id = key(incidentId, slaType, kind);
        }
    }

    /**
     * Convenience constructor generating the natural idempotency key as the id.
     */
    public SlaAlert(String incidentId, SlaType slaType, SlaAlertKind kind, Instant deadline, Instant raisedAt) {
        this(key(incidentId, slaType, kind), incidentId, slaType, kind, deadline, raisedAt);
    }

    /**
     * Computes the natural idempotency key for an SLA alert: {@code incidentId:slaType:kind}.
     */
    public static String key(String incidentId, SlaType slaType, SlaAlertKind kind) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (slaType == null) {
            throw new InvalidInputException("slaType must not be null");
        }
        if (kind == null) {
            throw new InvalidInputException("kind must not be null");
        }
        return incidentId + ":" + slaType.name() + ":" + kind.name();
    }

    /**
     * Returns the natural idempotency key of this alert.
     */
    public String alertKey() {
        return key(incidentId, slaType, kind);
    }
}
