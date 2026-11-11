package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;

public record AssignIncidentCommand(
        String actorId,
        String incidentId,
        String technicianId
) {

    public AssignIncidentCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (technicianId == null || technicianId.isBlank()) {
            throw new InvalidInputException("technicianId must not be blank");
        }
    }
}
