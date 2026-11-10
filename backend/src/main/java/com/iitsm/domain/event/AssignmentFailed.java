package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;

import java.time.Instant;

public record AssignmentFailed(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description
) implements DomainEvent {

    public static final String ACTION = "ASSIGNMENT_FAILED";

    public AssignmentFailed {
        if (occurredAt == null) {
            throw new InvalidInputException("occurredAt must not be null");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (previousStatus != IncidentStatus.CLASSIFIED) {
            throw new InvalidInputException("previousStatus must be CLASSIFIED for AssignmentFailed");
        }
        if (newStatus != IncidentStatus.CLASSIFIED) {
            throw new InvalidInputException("newStatus must be CLASSIFIED for AssignmentFailed");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
    }

    @Override
    public String action() {
        return ACTION;
    }
}
