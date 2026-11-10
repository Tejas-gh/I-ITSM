package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;

import java.time.Instant;

public record IncidentAcknowledged(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description,
        String technicianId
) implements DomainEvent {

    public static final String ACTION = "INCIDENT_ACKNOWLEDGED";

    public IncidentAcknowledged {
        if (occurredAt == null) {
            throw new InvalidInputException("occurredAt must not be null");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (previousStatus != IncidentStatus.ASSIGNED) {
            throw new InvalidInputException("previousStatus must be ASSIGNED for IncidentAcknowledged");
        }
        if (newStatus != IncidentStatus.ACKNOWLEDGED) {
            throw new InvalidInputException("newStatus must be ACKNOWLEDGED for IncidentAcknowledged");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
        if (technicianId == null || technicianId.isBlank()) {
            throw new InvalidInputException("technicianId must not be blank");
        }
    }

    @Override
    public String action() {
        return ACTION;
    }
}
