package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;

import java.time.Instant;

public record IncidentClosed(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description,
        String closedById
) implements DomainEvent {

    public static final String ACTION = "INCIDENT_CLOSED";

    public IncidentClosed {
        if (occurredAt == null) {
            throw new InvalidInputException("occurredAt must not be null");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (previousStatus != IncidentStatus.VERIFIED) {
            throw new InvalidInputException("previousStatus must be VERIFIED for IncidentClosed");
        }
        if (newStatus != IncidentStatus.CLOSED) {
            throw new InvalidInputException("newStatus must be CLOSED for IncidentClosed");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
        if (closedById == null || closedById.isBlank()) {
            throw new InvalidInputException("closedById must not be blank");
        }
    }

    @Override
    public String action() {
        return ACTION;
    }
}
