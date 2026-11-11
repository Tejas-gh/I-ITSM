package com.iitsm.application.service;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.UserRepository;

/**
 * Enforces administrator permission rules for administrative catalog and audit operations.
 */
public final class AdminPermissionPolicy {

    public AdminPermissionPolicy() {
    }

    /**
     * Checks whether the actor has administrative permissions.
     *
     * @param actor the loaded user to check
     * @throws UnauthorizedOperationException if the user is inactive or not an administrator
     */
    public void check(User actor) {
        if (actor == null) {
            throw new InvalidInputException("actor must not be null");
        }
        if (!actor.isActive()) {
            throw new UnauthorizedOperationException("Actor " + actor.getId() + " is inactive");
        }
        Role role = actor.getRole();
        if (role != Role.ADMINISTRATOR && role != Role.IT_MANAGER) {
            throw new UnauthorizedOperationException(
                    "Actor " + actor.getId() + " does not have administrator privileges (role: " + role + ")"
            );
        }
    }

    /**
     * Resolves the actor by id and checks administrative permissions.
     *
     * @param actorId        the id of the actor
     * @param userRepository the repository to resolve the actor from
     * @throws UserNotFoundException          if the actor cannot be found
     * @throws UnauthorizedOperationException if the actor is inactive or not an administrator
     */
    public void check(String actorId, UserRepository userRepository) {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + actorId));
        check(actor);
    }
}
