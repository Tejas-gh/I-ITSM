package com.iitsm.domain.event;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;

import java.time.Instant;

public record IncidentCreated(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String description,
        String reporterId,
        String serviceId,
        String categoryId,
        String suggestedCategoryId,
        CategorySource categorySource,
        PriorityLevel priority,
        int priorityScore
) implements DomainEvent {

    public static final String ACTION = "INCIDENT_CREATED";

    public IncidentCreated {
        if (occurredAt == null) {
            throw new InvalidInputException("occurredAt must not be null");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (previousStatus != null) {
            throw new InvalidInputException("previousStatus must be null for IncidentCreated");
        }
        if (newStatus != IncidentStatus.NEW) {
            throw new InvalidInputException("newStatus must be NEW for IncidentCreated");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("description must not be blank");
        }
        if (reporterId == null || reporterId.isBlank()) {
            throw new InvalidInputException("reporterId must not be blank");
        }
        if (serviceId == null || serviceId.isBlank()) {
            throw new InvalidInputException("serviceId must not be blank");
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new InvalidInputException("categoryId must not be blank");
        }
        if (categorySource == null) {
            throw new InvalidInputException("categorySource must not be null");
        }
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        if (priorityScore < 1) {
            throw new InvalidInputException("priorityScore must be at least 1");
        }
    }

    @Override
    public String action() {
        return ACTION;
    }
}
