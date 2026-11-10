package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;

import java.time.Instant;

public record IncidentUpdated(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description,
        String action
) implements DomainEvent {

    public IncidentUpdated {
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
        if (previousStatus != newStatus) {
            throw new InvalidInputException("previousStatus must equal newStatus for IncidentUpdated");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
        if (action == null || action.isBlank()) {
            throw new InvalidInputException("action must not be blank");
        }
    }
}
