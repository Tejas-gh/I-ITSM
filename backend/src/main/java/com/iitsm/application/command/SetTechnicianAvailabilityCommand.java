package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;

/** Marks a technician available or unavailable for assignment. */
public record SetTechnicianAvailabilityCommand(String actorId, String technicianId, boolean available) {

    public SetTechnicianAvailabilityCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (technicianId == null || technicianId.isBlank()) {
            throw new InvalidInputException("technicianId must not be blank");
        }
    }
}
