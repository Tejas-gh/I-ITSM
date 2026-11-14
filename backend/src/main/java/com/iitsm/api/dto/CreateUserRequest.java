package com.iitsm.api.dto;

import java.util.Map;

import com.iitsm.domain.model.Role;

/**
 * Body of {@code POST /api/admin/users}. {@code department} is for EMPLOYEE; {@code skillLevels},
 * {@code yearsOfExperience} and {@code maxActiveIncidents} are for TECHNICIAN and TEAM_LEAD.
 */
public record CreateUserRequest(String id, String name, String email, Role role, String department,
                                Map<String, Integer> skillLevels, Integer yearsOfExperience,
                                Integer maxActiveIncidents, String password) {

    @Override
    public String toString() {
        return "CreateUserRequest[id=" + id + ", name=" + name + ", email=" + email + ", role=" + role + "]";
    }
}
