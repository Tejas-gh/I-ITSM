package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;

/**
 * Command for creating or updating an incident category.
 */
public record SaveCategoryCommand(
        String actorId,
        String id,
        String name,
        Boolean active
) {
    public SaveCategoryCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("name must not be blank");
        }
    }
}
