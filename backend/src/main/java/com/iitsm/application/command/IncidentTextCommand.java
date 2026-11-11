package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;

public record IncidentTextCommand(
        String actorId,
        String incidentId,
        String text
) {

    public IncidentTextCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
    }
}
