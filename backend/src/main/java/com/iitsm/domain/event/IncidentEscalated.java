package com.iitsm.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Role;

/**
 * Domain event published when an unacknowledged P1 incident is automatically escalated
 * via the Chain of Responsibility.
 */
public record IncidentEscalated(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description,
        int escalationLevel,
        Role targetRole
) implements DomainEvent {

    public static final String ACTION = "INCIDENT_ESCALATED";

    public IncidentEscalated {
        if (occurredAt == null) {
            throw new InvalidInputException("occurredAt must not be null");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (previousStatus == null) {
            throw new InvalidInputException("previousStatus must not be null");
        }
        if (newStatus == null) {
            throw new InvalidInputException("newStatus must not be null");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
        if (escalationLevel < 1 || escalationLevel > 3) {
            throw new InvalidInputException("escalationLevel must be between 1 and 3");
        }
        if (targetRole == null) {
            throw new InvalidInputException("targetRole must not be null");
        }
    }

    @Override
    public String action() {
        return ACTION;
    }
}
