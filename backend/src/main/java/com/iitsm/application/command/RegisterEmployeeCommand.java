package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;

/** Self-registration data. There is deliberately no role field: registration only creates an EMPLOYEE. */
public record RegisterEmployeeCommand(String id, String name, String email, String department, String password) {

    public RegisterEmployeeCommand {
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("id must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RegisterEmployeeCommand[id=" + id + ", name=" + name + ", email=" + email
                + ", department=" + department + "]";
    }
}
