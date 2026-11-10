package com.iitsm.domain.model;

import com.iitsm.domain.exception.InvalidInputException;

public final class Administrator extends User {

    public Administrator(String id, String name, String email, Role role) {
        super(id, name, email, role);
        if (role != Role.ADMINISTRATOR && role != Role.IT_MANAGER) {
            throw new InvalidInputException("role must be ADMINISTRATOR or IT_MANAGER");
        }
    }
}
