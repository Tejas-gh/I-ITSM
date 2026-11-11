package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;

public record UpdatePriorityInputsCommand(
        String actorId,
        String incidentId,
        Impact impact,
        Urgency urgency
) {

    public UpdatePriorityInputsCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (impact == null) {
            throw new InvalidInputException("impact must not be null");
        }
        if (urgency == null) {
            throw new InvalidInputException("urgency must not be null");
        }
    }
}
