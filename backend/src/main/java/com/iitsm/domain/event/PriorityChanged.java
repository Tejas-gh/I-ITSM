package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;

import java.time.Instant;

public record PriorityChanged(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description,
        PriorityLevel previousPriority,
        PriorityLevel newPriority,
        int previousScore,
        int newScore
) implements DomainEvent {

    public static final String ACTION = "PRIORITY_CHANGED";

    public PriorityChanged {
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
            throw new InvalidInputException("previousStatus must equal newStatus for PriorityChanged");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
        if (previousPriority == null) {
            throw new InvalidInputException("previousPriority must not be null");
        }
        if (newPriority == null) {
            throw new InvalidInputException("newPriority must not be null");
        }
        if (previousScore < 1) {
            throw new InvalidInputException("previousScore must be at least 1");
        }
        if (newScore < 1) {
            throw new InvalidInputException("newScore must be at least 1");
        }
    }

    @Override
    public String action() {
        return ACTION;
    }
}
