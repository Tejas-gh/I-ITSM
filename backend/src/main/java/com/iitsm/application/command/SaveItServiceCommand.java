package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.BusinessCriticality;

/**
 * Command for creating or updating an IT service.
 */
public record SaveItServiceCommand(
        String actorId,
        String id,
        String name,
        BusinessCriticality businessCriticality
) {
    public SaveItServiceCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("name must not be blank");
        }
        if (businessCriticality == null) {
            throw new InvalidInputException("businessCriticality must not be null");
        }
    }
}
