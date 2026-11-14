package com.iitsm.api.dto;

import java.util.Map;

/** Body of {@code PUT /api/admin/users/{id}}. Omitted (null) fields keep their current value; the role cannot change. */
public record UpdateUserRequest(String name, String email, String department, Map<String, Integer> skillLevels,
                                Integer yearsOfExperience, Integer maxActiveIncidents, Boolean active) {
}
