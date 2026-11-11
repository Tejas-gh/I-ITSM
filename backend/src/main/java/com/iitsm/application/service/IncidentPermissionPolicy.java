package com.iitsm.application.service;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.User;

/**
 * Enforces the role-based permission matrix for incident operations.
 */
public final class IncidentPermissionPolicy {

    public IncidentPermissionPolicy() {
    }

    public void check(User actor, IncidentOperation operation, Incident incident) {
        if (actor == null) {
            throw new InvalidInputException("actor must not be null");
        }
        if (operation == null) {
            throw new InvalidInputException("operation must not be null");
        }

        if (!actor.isActive()) {
            throw new UnauthorizedOperationException("Actor " + actor.getId() + " is inactive");
        }

        if (operation != IncidentOperation.CREATE && operation != IncidentOperation.SUGGEST_CATEGORY && incident == null) {
            throw new InvalidInputException("incident must not be null for operation " + operation);
        }

        Role role = actor.getRole();
        boolean isEmployee = role == Role.EMPLOYEE;
        boolean isTechnician = role == Role.TECHNICIAN || role == Role.TEAM_LEAD;
        boolean isAdmin = role == Role.ADMINISTRATOR || role == Role.IT_MANAGER;

        boolean allowed = switch (operation) {
            case CREATE -> isEmployee;

            case VIEW -> {
                if (isAdmin) {
                    yield true;
                }
                if (isEmployee) {
                    yield isReporter(actor, incident);
                }
                if (isTechnician) {
                    yield isAssignee(actor, incident);
                }
                yield false;
            }

            case ASSIGN -> isAdmin;

            case ACKNOWLEDGE, START_WORK, ADD_INVESTIGATION_NOTE, REQUEST_INFO, RESOLVE ->
                    isTechnician && isAssignee(actor, incident);

            case RESPOND_INFO, ADD_ADDITIONAL_INFO, VERIFY, REJECT_RESOLUTION, SUBMIT_FEEDBACK ->
                    isEmployee && isReporter(actor, incident);

            case CLOSE -> isAdmin;

            case UPDATE_PRIORITY -> isAdmin || (isTechnician && isAssignee(actor, incident));

            case SUGGEST_CATEGORY -> isEmployee || isTechnician || isAdmin;
        };

        if (!allowed) {
            throw new UnauthorizedOperationException(
                    "User " + actor.getId() + " with role " + role + " is not authorized for operation " + operation
            );
        }
    }

    private boolean isReporter(User actor, Incident incident) {
        return incident.getReporter() != null && actor.getId().equals(incident.getReporter().getId());
    }

    private boolean isAssignee(User actor, Incident incident) {
        return incident.getAssignedTechnician().isPresent()
                && actor.getId().equals(incident.getAssignedTechnician().get().getId());
    }
}
