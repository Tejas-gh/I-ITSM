package com.iitsm.application.command;

import java.util.Map;

import com.iitsm.domain.exception.InvalidInputException;

/**
 * Administrator update of an existing user. Every field except the ids is optional: {@code null} keeps the current
 * value. The role never changes. A non-null {@code skillLevels} replaces the technician's whole skill map.
 */
public record UpdateUserCommand(
        String actorId,
        String userId,
        String name,
        String email,
        String department,
        Map<String, Integer> skillLevels,
        Integer yearsOfExperience,
        Integer maxActiveIncidents,
        Boolean active
) {
    public UpdateUserCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new InvalidInputException("userId must not be blank");
        }
    }
}
