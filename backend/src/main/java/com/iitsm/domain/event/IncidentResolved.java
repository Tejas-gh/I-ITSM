package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;

import java.time.Instant;

public record IncidentResolved(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description,
        String resolverId
) implements DomainEvent {

    public static final String ACTION = "INCIDENT_RESOLVED";

    public IncidentResolved {
        if (occurredAt == null) {
            throw new InvalidInputException("occurredAt must not be null");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (previousStatus != IncidentStatus.IN_PROGRESS) {
            throw new InvalidInputException("previousStatus must be IN_PROGRESS for IncidentResolved");
        }
        if (newStatus != IncidentStatus.RESOLVED) {
            throw new InvalidInputException("newStatus must be RESOLVED for IncidentResolved");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
        if (resolverId == null || resolverId.isBlank()) {
            throw new InvalidInputException("resolverId must not be blank");
        }
    }

    @Override
    public String action() {
        return ACTION;
    }
}
