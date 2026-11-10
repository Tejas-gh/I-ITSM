package com.iitsm.domain.factory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;

public final class UserFactory {

    public User create(UserDraft draft) {
        if (draft == null) {
            throw new InvalidInputException("draft must not be null");
        }
        Role role = draft.role();
        return switch (role) {
            case EMPLOYEE -> {
                if (draft.department() == null) {
                    throw new InvalidInputException("department is required for an EMPLOYEE");
                }
                yield new Employee(draft.id(), draft.name(), draft.email(), draft.department());
            }
            case TECHNICIAN, TEAM_LEAD -> {
                if (draft.skillLevels() == null) {
                    throw new InvalidInputException("skillLevels is required for a " + role);
                }
                yield new Technician(draft.id(), draft.name(), draft.email(), role,
                        draft.skillLevels(), draft.yearsOfExperience(), draft.maxActiveIncidents());
            }
            case ADMINISTRATOR, IT_MANAGER ->
                    new Administrator(draft.id(), draft.name(), draft.email(), role);
        };
    }
}
