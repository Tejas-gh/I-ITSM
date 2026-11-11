package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;

public record SubmitFeedbackCommand(
        String actorId,
        String incidentId,
        int rating,
        String comment
) {

    public SubmitFeedbackCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
    }
}
