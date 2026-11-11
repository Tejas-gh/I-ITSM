package com.iitsm.application.service;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.sla.SlaStatus;

import java.time.Instant;

/**
 * Immutable search query for searching visible incidents with multi-attribute criteria and SLA status.
 */
public record IncidentSearchQuery(
        String incidentId,
        Instant createdFrom,
        Instant createdTo,
        IncidentStatus status,
        PriorityLevel priority,
        String categoryId,
        String serviceId,
        String technicianId,
        SlaStatus slaStatus
) {
    public IncidentSearchQuery {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidInputException("createdFrom must not be after createdTo");
        }
    }

    public static IncidentSearchQuery empty() {
        return new IncidentSearchQuery(null, null, null, null, null, null, null, null, null);
    }
}
