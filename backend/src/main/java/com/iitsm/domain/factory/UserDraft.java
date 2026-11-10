package com.iitsm.domain.factory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;

import java.util.Map;

public record UserDraft(String id, String name, String email, Role role, String department,
                        Map<String, Integer> skillLevels, int yearsOfExperience, int maxActiveIncidents) {

    public UserDraft {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        if (name == null) {
            throw new InvalidInputException("name must not be null");
        }
        if (email == null) {
            throw new InvalidInputException("email must not be null");
        }
        if (role == null) {
            throw new InvalidInputException("role must not be null");
        }
    }
}
