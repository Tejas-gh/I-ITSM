package com.iitsm.domain.audit;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable audit record capturing the 7 core ITSM audit fields.
 */
public record AuditEntry(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String action,
        String description
) {
    public AuditEntry {
        if (occurredAt == null) {
            throw new InvalidInputException("occurredAt must not be null");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be null or blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be null or blank");
        }
        if (action == null || action.isBlank()) {
            throw new InvalidInputException("action must not be null or blank");
        }
        if (description == null) {
            description = "";
        }
    }

    /**
     * Creates an AuditEntry mapped generically from any DomainEvent.
     */
    public static AuditEntry from(DomainEvent event) {
        if (event == null) {
            throw new InvalidInputException("event must not be null");
        }
        return new AuditEntry(
                event.occurredAt(),
                event.actorId(),
                event.incidentId(),
                event.previousStatus(),
                event.newStatus(),
                event.action(),
                event.description()
        );
    }
}
