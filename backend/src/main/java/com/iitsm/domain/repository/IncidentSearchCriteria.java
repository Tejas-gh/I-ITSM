package com.iitsm.domain.repository;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;

import java.time.Instant;

public record IncidentSearchCriteria(String incidentId, Instant createdFrom, Instant createdTo,
                                     IncidentStatus status, PriorityLevel priority,
                                     String categoryId, String serviceId, String technicianId) {

    public IncidentSearchCriteria {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidInputException("createdFrom must not be after createdTo");
        }
    }

    public static IncidentSearchCriteria none() {
        return new IncidentSearchCriteria(null, null, null, null, null, null, null, null);
    }
}
