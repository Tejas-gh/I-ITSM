package com.iitsm.application.command;

import java.util.Map;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;

/**
 * Administrator-created user. {@code department} applies to EMPLOYEE; {@code skillLevels}, {@code yearsOfExperience}
 * and {@code maxActiveIncidents} apply to TECHNICIAN and TEAM_LEAD. The initial password is hashed and never stored
 * or returned as plain text.
 */
public record CreateUserCommand(
        String actorId,
        String id,
        String name,
        String email,
        Role role,
        String department,
        Map<String, Integer> skillLevels,
        int yearsOfExperience,
        int maxActiveIncidents,
        String password
) {
    public CreateUserCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("id must not be blank");
        }
        if (role == null) {
            throw new InvalidInputException("role must not be null");
        }
    }

    @Override
    public String toString() {
        return "CreateUserCommand[actorId=" + actorId + ", id=" + id + ", name=" + name + ", email=" + email
                + ", role=" + role + "]";
    }
}
