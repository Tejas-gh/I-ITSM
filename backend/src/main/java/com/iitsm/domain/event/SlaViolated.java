package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.sla.SlaType;

import java.time.Instant;

public record SlaViolated(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description,
        SlaType slaType,
        Instant deadline
) implements DomainEvent {

    public static final String ACTION = "SLA_VIOLATED";

    public SlaViolated {
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
            throw new InvalidInputException("previousStatus must equal newStatus for SlaViolated");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
        if (slaType == null) {
            throw new InvalidInputException("slaType must not be null");
        }
        if (deadline == null) {
            throw new InvalidInputException("deadline must not be null");
        }
    }

    @Override
    public String action() {
        return ACTION;
    }
}
